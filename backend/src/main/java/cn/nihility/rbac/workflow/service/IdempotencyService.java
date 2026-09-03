package cn.nihility.rbac.workflow.service;

import java.util.function.Supplier;

/**
 * 操作幂等服务：基于 {@code tab_wf_operation_request} 对审批类写操作提供幂等保护
 * （workflow-approval-engine change design.md Decision 6）。
 */
public interface IdempotencyService {

    /**
     * 以给定幂等键执行一次操作；{@code requestKey} 为空时不做任何保护，直接执行。
     * 调用方需保证本方法运行在与 {@code action} 内部数据库写操作相同的事务中——幂等标记行的
     * 插入与实际业务写操作要么一起提交，要么一起回滚，失败重试时不会被误判为"已处理"。
     *
     * @param requestKey 幂等键，取自 {@code X-Request-Id} 请求头，可为空
     * @param operation  操作类型，{@link cn.nihility.rbac.workflow.constant.ApprovalAction} 字面量
     * @param operatorId 操作人用户 id
     * @param taskId     关联的审批任务 id，可为空（如撤回操作不针对具体任务）
     * @param action     实际要执行的业务逻辑
     * @param <T>        业务逻辑返回值类型
     * @return {@code action} 的执行结果；命中重复请求时返回 {@code null}（当前全部写操作均为
     *         {@code void} 语义，调用方应将其视为"已按首次结果处理完成"，不再重复处理）
     */
    <T> T executeOnce(String requestKey, String operation, Long operatorId, Long taskId, Supplier<T> action);
}
