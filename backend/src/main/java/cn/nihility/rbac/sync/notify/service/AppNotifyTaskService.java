package cn.nihility.rbac.sync.notify.service;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知任务落库与状态机流转业务逻辑接口（app-sync-changelog-pull change design.md
 * Decision 6）：只负责单条 {@code tab_app_notify_record} 任务记录的创建、原子抢占、状态
 * 流转，不关心候选应用如何解析（{@code NotifyCandidateResolver} 的职责）、不发起真正的 HTTP
 * 请求（{@code AppNotifyService#sendOnce} 的职责），职责边界清晰划分，避免单个类膨胀。
 */
public interface AppNotifyTaskService {

    /**
     * 为一个已判定命中的候选应用创建（或复用已存在的）{@code PENDING} 通知任务：使用
     * {@code (appRefId, eventId)} 唯一键保证同一事件同一应用只有一行，重试复用同一行、
     * 不新插入。调用方需自行保证处于合适的事务边界内（本方法不声明独立事务，便于被
     * 更大范围的"流水 + 全部通知任务同事务落库"事务复用）。
     *
     * @param event     领域变更事件
     * @param changeLog 本次事件对应、已写入的全局变更流水（提供 {@code changeSeq}）
     * @param appRefId  候选应用 id（{@code tab_app.id}）
     * @return 创建或复用到的任务记录；目标应用的对外接口配置在候选解析之后、落库之前恰好被
     *         删除这种极端竞态下返回 {@code null}，调用方需要过滤
     */
    AppNotifyRecordEntity enqueueTask(DomainChangeEvent event, AppDataChangeLogEntity changeLog, Long appRefId);

    /**
     * 扫描当前到期需要处理的任务：{@code PENDING}/{@code RETRY} 状态且到期（
     * {@code next_retry_time} 为空或已过期），或 {@code PROCESSING} 状态且租约已超时，
     * 按主键升序取前 {@code batchSize} 条（design.md Decision 6 调度器职责）。返回的实体
     * 携带调用时刻的完整字段快照，{@code requestBody}/{@code notifyUrl} 等"请求体快照"类
     * 字段自创建后不会再变化，可直接用于后续发送，无需在抢占成功后重新查询一次。
     *
     * @param now       扫描基准时刻
     * @param batchSize 单轮最多返回的记录数
     * @return 到期任务列表，按 id 升序排列
     */
    List<AppNotifyRecordEntity> scanDueTasks(LocalDateTime now, int batchSize);

    /**
     * 原子抢占一条任务：仅当当前 {@code task_status} 仍等于 {@code expectedStatus}（对
     * {@code PROCESSING} 额外要求 {@code lease_until < now}，即租约已超时）时，才把状态改为
     * {@code PROCESSING} 并写入新的 {@code lease_until}；单条 {@code UPDATE ... WHERE}
     * 语句一次性完成条件检查与状态变更，依赖数据库行锁保证并发场景下同一条任务只有一个
     * 调用方能抢占成功，不需要额外的分布式锁。
     *
     * @param id             任务主键 id
     * @param expectedStatus 期望的当前状态（{@code PENDING}/{@code RETRY}/{@code PROCESSING}）
     * @param now            当前时刻，仅在 {@code expectedStatus} 为 {@code PROCESSING} 时
     *                       用于比较租约是否已超时
     * @param leaseUntil     抢占成功后写入的新租约截止时间
     * @return 是否抢占成功
     */
    boolean claim(Long id, String expectedStatus, LocalDateTime now, LocalDateTime leaseUntil);

    /**
     * 把一条任务标记为发送成功（终态）：清空 {@code next_retry_time}/{@code lease_until}，
     * 回填历史展示字段 {@code notify_status=SUCCESS}/{@code http_status}。
     *
     * @param id         任务主键 id
     * @param httpStatus 外部接口返回的 2xx 状态码
     */
    void markSuccess(Long id, Integer httpStatus);

    /**
     * 记录一次发送尝试失败，并按退避策略决定转 {@code RETRY}（写入递增后的
     * {@code retry_count} 与计算出的 {@code next_retry_time}）还是转 {@code DEAD}（达到最大
     * 尝试次数，或本次失败属于不可重试类型）。
     *
     * @param id                    任务主键 id
     * @param retryCountBeforeAttempt 本次尝试之前已失败的次数
     * @param retryableByType       本次失败是否属于可重试的失败类型（网络异常/408/429/5xx）
     * @param httpStatus            外部接口返回的状态码，网络异常等未收到响应时为 {@code null}
     * @param errorMsg              失败原因摘要
     */
    void recordAttemptFailure(Long id, int retryCountBeforeAttempt, boolean retryableByType, Integer httpStatus,
            String errorMsg);

    /**
     * 按主键查询任务当前完整记录。
     *
     * @param id 任务主键 id
     * @return 任务记录，不存在时返回 {@code null}
     */
    AppNotifyRecordEntity getById(Long id);

    /**
     * 管理端手动重推：原子清理租约/下次重试时间/已失败次数并把 {@code DEAD} 状态重置为
     * {@code PENDING}，仅当当前状态确实是 {@code DEAD} 时才生效（tasks.md 6.3）。重置后由
     * 调用方（{@code AppNotifyRecordServiceImpl}）负责触发一次即时发送优化，本方法本身不
     * 发起发送。
     *
     * @param id 任务主键 id
     * @return 是否重置成功；记录不存在或当前不是 {@code DEAD} 状态时返回 {@code false}
     */
    boolean resetDeadToPending(Long id);
}
