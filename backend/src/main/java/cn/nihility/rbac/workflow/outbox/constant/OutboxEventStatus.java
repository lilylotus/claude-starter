package cn.nihility.rbac.workflow.outbox.constant;

/**
 * Outbox 事件状态，对应 {@code tab_wf_outbox_event.status}（真实 DDL 见
 * {@code V11__add_production_approval_lifecycle_tables.sql}，只有这四个取值，没有
 * design.md 表格文字提到但实际未建的独立 {@code revision} 列）。
 */
public final class OutboxEventStatus {

    /** 待领取：新写入尚未被任何 worker 抢占，或失败退避后重新回到可领取状态。 */
    public static final String PENDING = "PENDING";

    /** 已被某个 worker 领取，持有未过期的租约（{@code lease_token}/{@code lease_until}）。 */
    public static final String LEASED = "LEASED";

    /** 已成功处理，终态，不再参与到期扫描。 */
    public static final String SUCCEEDED = "SUCCEEDED";

    /** 达到最大尝试次数或最大存活时长后转入的人工处理终态，不再参与到期扫描。 */
    public static final String FAILED = "FAILED";

    /**
     * 工具类不允许实例化。
     */
    private OutboxEventStatus() {
    }
}
