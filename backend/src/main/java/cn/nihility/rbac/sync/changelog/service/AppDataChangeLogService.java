package cn.nihility.rbac.sync.changelog.service;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import java.time.LocalDateTime;

/** 全局应用数据变更流水服务。 */
public interface AppDataChangeLogService {

    /** 持久化事件并返回包含数据库自增游标的流水。 */
    AppDataChangeLogEntity append(DomainChangeEvent event);

    /**
     * 清理一批变更发生时间早于 {@code cutoff} 的过期流水，并在同一事务内把保留窗口下界
     * 游标推进为本批实际删除的最大 {@code change_seq}；删除或推进游标任一步失败均整体回滚
     * （app-sync-changelog-pull change design.md Decision 8）。
     *
     * @param cutoff    变更发生时间上限，早于该时间的记录视为过期
     * @param batchSize 单批最多删除的记录数
     * @return 本批实际删除的记录数，{@code 0} 表示当前已无过期记录（调用方据此判断是否
     *         继续下一批）
     */
    int cleanupExpiredBatch(LocalDateTime cutoff, int batchSize);
}
