package cn.nihility.rbac.logcleanup.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录日志/操作日志定期清理相关配置，绑定前缀 {@code rbac.log-cleanup}：清理任务的执行
 * 时间点（cron 表达式）与保留天数，两张表共用同一份配置，不支持按表单独配置（YAGNI，
 * close-sso-log-and-policy-gaps change design.md Decision 4）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.log-cleanup")
public class LogCleanupProperties {

    /** 清理任务执行时间点的 cron 表达式，默认每天凌晨 1 点执行一次。 */
    private String cron = "0 0 1 * * ?";

    /** 日志保留天数，默认 180 天（半年），创建时间早于"当前时间 - 保留天数"的记录会被清理。 */
    private int retentionDays = 180;
}
