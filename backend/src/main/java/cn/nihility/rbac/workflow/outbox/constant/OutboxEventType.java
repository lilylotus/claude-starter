package cn.nihility.rbac.workflow.outbox.constant;

/**
 * Outbox 事件类型（production-approval-lifecycle change design.md 第267行，对应
 * {@code tab_wf_outbox_event.event_type} 列注释枚举的取值集合，tasks.md 7.3 首次落地为
 * 常量类，此前仅以字面量出现在注释/字符串中）。
 */
public final class OutboxEventType {

    /** 审批任务创建。 */
    public static final String TASK_CREATED = "TASK_CREATED";

    /** 审批任务分配/认领。 */
    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";

    /** 审批任务取消（如 MI 提前结束、退回、终止）。 */
    public static final String TASK_CANCELLED = "TASK_CANCELLED";

    /** 流程终审通过：审批已通过，需要执行业务变更（tasks.md 7.3 选定该事件类型驱动业务执行
     *  适配器，理由见 {@code cn.nihility.rbac.approval.execution.consumer
     *  .MasterDataBusinessExecutionConsumer} 类注释）。 */
    public static final String PROCESS_APPROVED = "PROCESS_APPROVED";

    /** 流程终审拒绝。 */
    public static final String PROCESS_REJECTED = "PROCESS_REJECTED";

    /** 业务执行成功，供通知等下游消费者感知。 */
    public static final String BUSINESS_SUCCEEDED = "BUSINESS_SUCCEEDED";

    /** 业务执行失败，供通知等下游消费者感知。 */
    public static final String BUSINESS_FAILED = "BUSINESS_FAILED";

    /** 抄送创建。 */
    public static final String CC_CREATED = "CC_CREATED";

    /** 工具类不允许实例化。 */
    private OutboxEventType() {
    }
}
