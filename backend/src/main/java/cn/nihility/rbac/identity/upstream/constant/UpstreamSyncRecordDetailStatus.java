package cn.nihility.rbac.identity.upstream.constant;

/**
 * 上游数据同步执行记录明细的行处理状态常量（upstream-sync-record-improvements change
 * design.md Decision 3）。独立于记录级别的 {@link UpstreamSyncStatus}——行明细只有
 * 成功/失败两种取值，不存在记录级别的 {@code PARTIAL}。取值直接对应
 * {@code tab_upstream_sync_record_detail.status} 列的存储值。
 */
public final class UpstreamSyncRecordDetailStatus {

    /** 该行处理成功。 */
    public static final String SUCCESS = "SUCCESS";

    /** 该行处理失败。 */
    public static final String FAILED = "FAILED";

    /**
     * 工具类不允许实例化。
     */
    private UpstreamSyncRecordDetailStatus() {
    }
}
