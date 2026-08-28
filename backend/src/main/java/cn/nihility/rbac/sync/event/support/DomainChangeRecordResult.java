package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import java.util.List;

/**
 * {@link DomainChangeRecorder#record} 的返回结果：一条已写入的全局变更流水，及其在同一事务
 * 内为全部候选应用创建（或复用）的 {@code PENDING} 通知任务列表（app-sync-changelog-pull
 * change design.md Decision 6）。
 *
 * @param changeLog 已写入的全局变更流水
 * @param tasks     候选应用通知任务列表，候选应用为空或目标应用配置查不到时可能为空列表
 */
public record DomainChangeRecordResult(AppDataChangeLogEntity changeLog, List<AppNotifyRecordEntity> tasks) {
}
