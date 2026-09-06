package cn.nihility.rbac.workflow.outbox.service;

import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import java.util.List;

/**
 * Outbox 可靠事件生产/领取服务接口（production-approval-lifecycle change design.md 第10节，
 * tasks.md 7.1）。本轮只交付"写入 + 领取 + 退避重试 + 死信"这一层通用基础设施本身，具体消费
 * 唯一键、fencing 细节延后到 7.2，各业务事件类型的生产调用点延后到 7.2-7.6，不在本接口范围。
 */
public interface OutboxEventService {

    /**
     * 同事务写入一条 Outbox 事件：内部只是一次普通 {@code insert}，不声明
     * {@code REQUIRES_NEW} 等会切换物理事务/连接的传播方式，必须与调用方处于同一个物理事务——
     * 调用方业务写失败回滚时，这条事件行必须一起消失，不能残留一条指向已回滚业务的"幽灵事件"。
     * <p>
     * {@code eventId} 是幂等键，由调用方显式指定并保证同一业务动作重复调用时传入相同值
     * （例如"流程实例 id + 事件类型"拼接的确定性字符串）：命中 {@code event_id} 唯一键冲突时
     * 视为重复发布，直接返回已存在的行，不重复插入、不抛异常——这与调用方业务写操作本身的幂等
     * 保护（如 {@code IdempotencyService}）配合，同一次业务重试不会产生两条事件。
     *
     * @param eventId     业务幂等事件 id，调用方指定，不能为空
     * @param aggregateId 聚合根标识，通常为流程实例 id 文本，不能为空
     * @param eventSeq    同一聚合根内事件序号，供顺序消费参考
     * @param eventType   事件类型字面量（如 {@code TASK_CREATED}/{@code PROCESS_APPROVED}）
     * @param payload     事件负载，落库前序列化为 JSON 快照
     * @return 新写入或已存在（幂等复用）的事件行
     * @throws IllegalArgumentException {@code eventId}/{@code aggregateId}/{@code eventType}
     *         为空
     */
    OutboxEventEntity publish(String eventId, String aggregateId, long eventSeq, String eventType, Object payload);

    /**
     * 领取一批到期事件：按 {@code status='PENDING' AND next_retry_time<=now} 或
     * {@code status='LEASED' AND lease_until<now}（租约已过期视为可重新领取）读取到期候选后，
     * 对每个候选逐条发起条件 {@code UPDATE} 抢占租约（MySQL 5.7 兼容写法，不依赖
     * {@code SELECT ... FOR UPDATE SKIP LOCKED}），检查受影响行数；没抢到的候选跳过、继续
     * 处理下一个，不抛异常中断整批。返回的实体已经是抢占成功后的最新状态（{@code status}=
     * {@code LEASED}、携带新生成的 {@code leaseToken}/{@code leaseUntil}），调用方后续调用
     * {@link #markSucceeded}/{@link #markFailed} 时需要传入这份实体（或至少其 id 与
     * leaseToken）以完成基于租约 token 的 CAS。
     *
     * @param batchSize 单轮最多尝试领取的候选数
     * @return 本轮实际抢占成功的事件列表，可能少于 {@code batchSize}（部分候选被其他 worker
     *         抢先领取）
     */
    List<OutboxEventEntity> claimDueEvents(int batchSize);

    /**
     * 把一条已持有租约的事件标记为处理成功（终态）：仅当当前 {@code status} 仍为
     * {@code LEASED} 且 {@code lease_token} 与调用方持有的一致时才生效（CAS），租约已被其他
     * worker 重新抢占（token 不一致）时更新影响 0 行，安全忽略、不会覆盖新 worker 的处理进度。
     *
     * @param claimedEvent 此前通过 {@link #claimDueEvents} 领取到的事件实体（需携带正确的
     *                     {@code id}/{@code leaseToken}）
     * @return 是否真正完成状态流转；{@code false} 表示租约已不再由调用方持有（fencing 生效）
     */
    boolean markSucceeded(OutboxEventEntity claimedEvent);

    /**
     * 记录一条已持有租约的事件本次处理失败，并按退避策略决定转回 {@code PENDING}（写入递增后
     * 的 {@code attemptCount} 与计算出的 {@code nextRetryTime}）还是转 {@code FAILED}（达到最大
     * 尝试次数或事件存活时长超过最大存活时长，进入人工处理队列，不再参与到期扫描）。同样仅当
     * 当前 {@code status} 仍为 {@code LEASED} 且 {@code lease_token} 与调用方持有的一致时才
     * 生效（CAS），防止旧 worker 的迟到失败上报覆盖新 worker 已经在处理的记录状态。
     *
     * @param claimedEvent 此前通过 {@link #claimDueEvents} 领取到的事件实体（需携带正确的
     *                     {@code id}/{@code leaseToken}/{@code attemptCount}/
     *                     {@code createTime}）
     * @return 是否真正完成状态流转；{@code false} 表示租约已不再由调用方持有（fencing 生效）
     */
    boolean markFailed(OutboxEventEntity claimedEvent);
}
