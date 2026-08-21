package cn.nihility.rbac.logcleanup.support;

import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import cn.nihility.rbac.loginlog.mapper.LoginLogMapper;
import cn.nihility.rbac.logcleanup.config.LogCleanupProperties;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 登录日志/操作日志定期清理定时任务（close-sso-log-and-policy-gaps change design.md
 * Decision 4）：按 {@link LogCleanupProperties} 配置的 cron 表达式周期执行，删除
 * {@code tab_login_log}/{@code tab_operation_log} 中创建时间早于"当前时间 - 保留天数"
 * 的记录。两张表各自独立 try/catch，一张表删除失败不影响另一张表，失败仅记录应用日志，
 * 不重新抛出（定时任务框架对未捕获异常的处理不应该被依赖）。单表删除用 MyBatis-Plus
 * {@code LambdaQueryWrapper} 即可，不涉及多表 JOIN，不需要 XML。不引入分布式锁：清理是
 * 幂等操作，多实例部署下重复执行只是多做几次没有行可删的 DELETE，没有副作用
 * （design.md Decision 5）。{@code RbacApplication} 已 {@code @EnableScheduling}，
 * 不需要再加。三张表共用同一份 {@link LogCleanupProperties} 配置，不单独开关
 * （add-sso-protocol-access-log change design.md Decision 5，新增 SSO 协议调用记录表
 * {@code tab_sso_protocol_log} 的清理）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanupScheduler {

    /** 登录日志/操作日志清理相关配置。 */
    private final LogCleanupProperties logCleanupProperties;

    /** 登录日志数据访问接口。 */
    private final LoginLogMapper loginLogMapper;

    /** 操作日志数据访问接口。 */
    private final OperationLogMapper operationLogMapper;

    /** SSO 协议调用记录数据访问接口。 */
    private final SsoProtocolLogMapper ssoProtocolLogMapper;

    /**
     * 定时清理入口，cron 表达式取自 {@link LogCleanupProperties#getCron()}。
     */
    @Scheduled(cron = "#{logCleanupProperties.cron}")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(logCleanupProperties.getRetentionDays());
        cleanupLoginLog(cutoff);
        cleanupOperationLog(cutoff);
        cleanupSsoProtocolLog(cutoff);
    }

    /**
     * 清理 {@code tab_login_log} 中创建时间早于截止时间的记录，失败仅记录日志。
     *
     * @param cutoff 截止时间，早于该时间的记录会被删除
     */
    private void cleanupLoginLog(LocalDateTime cutoff) {
        try {
            int deleted = loginLogMapper.delete(new LambdaQueryWrapper<LoginLogEntity>()
                    .lt(LoginLogEntity::getCreateTime, cutoff));
            log.info("登录日志清理完成，删除 {} 条创建时间早于 {} 的记录", deleted, cutoff);
        } catch (Exception e) {
            log.error("登录日志清理失败", e);
        }
    }

    /**
     * 清理 {@code tab_operation_log} 中创建时间早于截止时间的记录，失败仅记录日志。
     *
     * @param cutoff 截止时间，早于该时间的记录会被删除
     */
    private void cleanupOperationLog(LocalDateTime cutoff) {
        try {
            int deleted = operationLogMapper.delete(new LambdaQueryWrapper<OperationLogEntity>()
                    .lt(OperationLogEntity::getCreateTime, cutoff));
            log.info("操作日志清理完成，删除 {} 条创建时间早于 {} 的记录", deleted, cutoff);
        } catch (Exception e) {
            log.error("操作日志清理失败", e);
        }
    }

    /**
     * 清理 {@code tab_sso_protocol_log} 中创建时间早于截止时间的记录，失败仅记录日志
     * （add-sso-protocol-access-log change design.md Decision 5）。
     *
     * @param cutoff 截止时间，早于该时间的记录会被删除
     */
    private void cleanupSsoProtocolLog(LocalDateTime cutoff) {
        try {
            int deleted = ssoProtocolLogMapper.delete(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                    .lt(SsoProtocolLogEntity::getCreateTime, cutoff));
            log.info("SSO 协议调用记录清理完成，删除 {} 条创建时间早于 {} 的记录", deleted, cutoff);
        } catch (Exception e) {
            log.error("SSO 协议调用记录清理失败", e);
        }
    }
}
