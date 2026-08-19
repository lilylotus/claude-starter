package cn.nihility.rbac.sync.pull.record.constant;

/**
 * 拉取方式常量，对应 {@code tab_app_pull_record.pull_mode} 列（add-app-sync-notify-pull-logs
 * change design.md Decision 2）。
 */
public final class PullMode {

    /** 按数据类型 + id 拉取，对应 {@code SyncPullService#pullByBizIds}。 */
    public static final String BY_ID = "BY_ID";

    /** 按序列号游标批量拉取，对应 {@code SyncPullService#pullBySequence}。 */
    public static final String BY_SEQUENCE = "BY_SEQUENCE";

    /**
     * 工具类不允许实例化。
     */
    private PullMode() {
    }
}
