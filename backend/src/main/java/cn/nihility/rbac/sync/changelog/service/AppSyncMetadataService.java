package cn.nihility.rbac.sync.changelog.service;

/** 应用同步全局元数据业务逻辑接口。 */
public interface AppSyncMetadataService {

    /** {@code tab_app_sync_metadata} 里保留窗口下界游标的元数据键。 */
    String CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY = "CHANGE_LOG_RETENTION_FLOOR_SEQ";

    /**
     * 查询当前变更流水保留窗口下界游标：早于该值的 {@code changeSeq} 对应记录已被清理任务
     * 删除，增量拉取接口据此判断调用方传入的 {@code sinceSeq} 是否已过期。
     *
     * @return 保留窗口下界游标，元数据行不存在时防御性返回 0（视同"尚未清理过，不存在过期
     *         边界"）
     */
    long getRetentionFloorSeq();

    /**
     * 原子推进保留窗口下界游标：仅当 {@code newFloorSeq} 大于当前值时才更新（数据库侧
     * {@code GREATEST}，不先读后写），floor 只能单调递增，不会因为某一批意外算出较小值而
     * 倒退（app-sync-changelog-pull change design.md Decision 8）。
     *
     * @param newFloorSeq 本批清理任务实际删除的最大 {@code change_seq}
     */
    void advanceRetentionFloorSeq(long newFloorSeq);
}
