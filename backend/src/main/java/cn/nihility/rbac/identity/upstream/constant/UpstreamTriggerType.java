package cn.nihility.rbac.identity.upstream.constant;

/**
 * 上游数据同步的触发方式常量：定时轮询触发或管理员手动触发（design.md Decision 2/3）。
 * 取值直接对应 {@code tab_upstream_sync_record.trigger_type} 列的存储值。
 */
public final class UpstreamTriggerType {

    /** 定时轮询触发。 */
    public static final String SCHEDULE = "SCHEDULE";

    /** 管理员手动触发（"立即同步一次"）。 */
    public static final String MANUAL = "MANUAL";

    /**
     * 工具类不允许实例化。
     */
    private UpstreamTriggerType() {
    }
}
