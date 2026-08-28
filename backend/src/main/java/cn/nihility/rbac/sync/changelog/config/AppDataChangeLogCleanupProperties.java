package cn.nihility.rbac.sync.changelog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全局变更流水表定期清理相关配置，绑定前缀 {@code rbac.sync.change-log-cleanup}
 * （app-sync-changelog-pull change design.md Decision 8），风格对齐
 * {@code cn.nihility.rbac.logcleanup.config.LogCleanupProperties}。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.sync.change-log-cleanup")
public class AppDataChangeLogCleanupProperties {

    /** 清理任务执行时间点的 cron 表达式，默认每天凌晨 1 点 30 分执行一次。 */
    private String cron = "0 30 1 * * ?";

    /** 变更流水保留天数，默认 90 天，变更发生时间早于"当前时间 - 保留天数"的记录会被清理。 */
    private int retentionDays = 90;

    /** 每批删除的记录数上限，默认 1000。 */
    private int batchSize = 1000;
}
