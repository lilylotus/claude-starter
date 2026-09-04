package cn.nihility.rbac.workflow.service;

import java.util.function.Supplier;

/**
 * 操作幂等服务：基于 {@code tab_wf_operation_request} 对审批类写操作提供幂等保护
 * （workflow-approval-engine change design.md Decision 6；同 key 同 payload 返回原结果、
 * 同 key 不同 payload 拒绝并报 {@code IDEMPOTENCY_CONFLICT} 属于
 * production-approval-lifecycle change design.md 第8节，tasks.md 6.2 补齐）。
 */
public interface IdempotencyService {

    /**
     * 以给定幂等键执行一次操作；{@code requestKey} 为空时不做任何保护，直接执行。
     * 调用方需保证本方法运行在与 {@code action} 内部数据库写操作相同的事务中——幂等标记行的
     * 插入与实际业务写操作要么一起提交，要么一起回滚，失败重试时不会被误判为"已处理"。
     * <p>
     * 幂等判断不只按 {@code (operatorId, requestKey)}："存在即跳过"会把"复用同一
     * {@code X-Request-Id} 却提交了不同内容"误判为无害重试；本方法对 {@code payload} 做
     * 规范化 JSON 摘要后与首次落库的摘要比对：一致则视为真实重试，直接返回首次执行结果，
     * 不再重复执行 {@code action}；不一致则抛出携带
     * {@link cn.nihility.rbac.workflow.constant.WorkflowErrorCode#IDEMPOTENCY_CONFLICT}
     * 错误码的 {@link cn.nihility.rbac.common.exception.BusinessException}，不静默按旧结果
     * 处理，也不执行本次这份不一致的新内容。
     *
     * @param requestKey 幂等键，取自 {@code X-Request-Id} 请求头，可为空
     * @param operation  操作类型，{@link cn.nihility.rbac.workflow.constant.ApprovalAction} 字面量
     * @param operatorId 操作人用户 id
     * @param taskId     关联的审批任务 id，可为空（如撤回操作不针对具体任务）
     * @param payload    本次请求的规范化输入内容（如调用方的命令对象），用于计算 payload 摘要；
     *                   可为空（等价于固定摘要，仅当同一 requestKey 每次都不携带 payload 时才会
     *                   命中"同 payload"）
     * @param action     实际要执行的业务逻辑
     * @param <T>        业务逻辑返回值类型
     * @return {@code action} 的执行结果；命中真实重复请求时返回首次执行结果反序列化后的值
     *         （当前全部写操作均为 {@code void} 语义，调用方应将其视为"已按首次结果处理完成"，
     *         不再重复处理）
     * @throws cn.nihility.rbac.common.exception.BusinessException 同一幂等键提交了不同的
     *         {@code payload} 内容（{@code IDEMPOTENCY_CONFLICT}）
     */
    <T> T executeOnce(
            String requestKey, String operation, Long operatorId, Long taskId, Object payload, Supplier<T> action);
}
