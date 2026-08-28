package cn.nihility.rbac.sync.changelog.support;

import cn.nihility.rbac.sync.changelog.config.AppDataChangeLogCleanupProperties;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 全局变更流水表定期清理定时任务（app-sync-changelog-pull change design.md Decision 8）：
 * 按 {@link AppDataChangeLogCleanupProperties} 配置的 cron 表达式周期执行，循环调用
 * {@link AppDataChangeLogService#cleanupExpiredBatch} 小批量删除
 * {@code tab_app_data_change_log} 中变更发生时间早于"当前时间 - 保留天数"的记录，直到某一批
 * 实际删除数少于 {@code batchSize}（说明已追上，不再有更多过期记录）为止；每一批的删除与
 * 保留窗口下界游标推进在同一个事务内完成（见 {@code AppDataChangeLogServiceImpl}），单批
 * 执行异常不重试、直接结束本轮，等待下一次调度周期继续（幂等操作，不会因为提前结束而遗留
 * 不一致状态）。{@code RbacApplication} 已 {@code @EnableScheduling}，不需要再加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppDataChangeLogCleanupScheduler {

    /** 全局变更流水清理相关配置。 */
    private final AppDataChangeLogCleanupProperties appDataChangeLogCleanupProperties;

    /** 全局应用数据变更流水业务逻辑接口。 */
    private final AppDataChangeLogService appDataChangeLogService;

    /**
     * 定时清理入口，cron 表达式取自 {@link AppDataChangeLogCleanupProperties#getCron()}。
     */
    @Scheduled(cron = "#{appDataChangeLogCleanupProperties.cron}")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(appDataChangeLogCleanupProperties.getRetentionDays());
        int batchSize = appDataChangeLogCleanupProperties.getBatchSize();
        long totalDeleted = 0L;
        while (true) {
            int deleted;
            try {
                deleted = appDataChangeLogService.cleanupExpiredBatch(cutoff, batchSize);
            } catch (Exception e) {
                log.error("全局变更流水清理任务执行失败，本轮提前结束：cutoff={}, 本轮已删除 {} 条", cutoff, totalDeleted, e);
                return;
            }
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        log.info("全局变更流水清理任务完成，共删除 {} 条变更发生时间早于 {} 的记录", totalDeleted, cutoff);
    }
}
