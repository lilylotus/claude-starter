package cn.nihility.rbac.approval.execution.dto;

/**
 * {@code PROCESS_APPROVED} 类型 Outbox 事件的负载结构（production-approval-lifecycle change
 * design.md 第267行"事件 payload 需要携带执行适配器完成写操作所需的全部信息"，tasks.md 7.3）。
 * 只携带指向 {@code tab_approval_request} 行的引用，而不是把 {@code bizType}/
 * {@code operationType}/{@code targetId}/{@code requestPayload} 原样复制进事件负载——这些信息
 * 该行本身已经完整持久化，复制一份快照反而引入"事件负载与申请行不一致"的风险（如申请行后续
 * 被其它路径修改），消费者按 {@code requestId} 反查该行即可拿到全部所需信息，不需要反查大量
 * 额外上下文。
 *
 * @param requestId 关联的 {@code tab_approval_request.id}
 */
public record BusinessExecutionTriggerPayload(Long requestId) {
}
