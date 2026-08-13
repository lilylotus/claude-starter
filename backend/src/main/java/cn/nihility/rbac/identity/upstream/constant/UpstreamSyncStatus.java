package cn.nihility.rbac.identity.upstream.constant;

/**
 * 上游数据同步执行记录的状态常量（design.md Decision 4）。取值直接对应
 * {@code tab_upstream_sync_record.status} 列的存储值。
 */
public final class UpstreamSyncStatus {

    /** 全部成功。 */
    public static final String SUCCESS = "SUCCESS";

    /** 部分失败。 */
    public static final String PARTIAL = "PARTIAL";

    /** 全部失败，或取数阶段发生异常。 */
    public static final String FAILED = "FAILED";

    /**
     * 工具类不允许实例化。
     */
    private UpstreamSyncStatus() {
    }
}
