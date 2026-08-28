package cn.nihility.rbac.sync.cursor.service;

/** 应用同步服务端投递水位业务逻辑接口。 */
public interface AppSyncCursorService {

    /**
     * 尽力推进 {@code (appRefId, entityType)} 的投递水位到 {@code max(现值, nextSeq)}：写入
     * 失败只记 WARN 日志，不向调用方抛出异常，不影响 {@code /changes} 接口本次的响应结果
     * （app-sync-changelog-pull change design.md Decision 9，风格对齐
     * {@code AppPullRecordService}"日志写入失败不影响主流程"的既有约定）。
     *
     * @param appRefId   应用 id
     * @param entityType 同步实体类型
     * @param nextSeq    本次响应的 {@code nextSeq}
     */
    void advance(Long appRefId, String entityType, long nextSeq);
}
