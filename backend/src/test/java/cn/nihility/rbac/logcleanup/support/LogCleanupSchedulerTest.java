package cn.nihility.rbac.logcleanup.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.logcleanup.config.LogCleanupProperties;
import cn.nihility.rbac.loginlog.constant.LoginResult;
import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import cn.nihility.rbac.loginlog.mapper.LoginLogMapper;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogEventType;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link LogCleanupScheduler} 的测试（close-sso-log-and-policy-gaps change tasks.md 3.5；
 * add-sso-protocol-access-log change tasks.md 6.4 扩展第三张表），起真实 MySQL 连接，直接
 * 调用 {@code cleanup} 方法而不依赖真实 cron 触发：验证保留期外（创建时间早于截止时间）的
 * 登录日志/操作日志/SSO 协议调用记录同批次被删除，保留期内的记录不受影响。
 */
@SpringBootTest
class LogCleanupSchedulerTest {

    /** 被测组件。 */
    @Autowired
    private LogCleanupScheduler scheduler;

    /** 登录日志/操作日志清理相关配置，测试内临时调整保留天数以制造可控的截止时间。 */
    @Autowired
    private LogCleanupProperties logCleanupProperties;

    /** 登录日志数据访问接口。 */
    @Autowired
    private LoginLogMapper loginLogMapper;

    /** 操作日志数据访问接口。 */
    @Autowired
    private OperationLogMapper operationLogMapper;

    /** SSO 协议调用记录数据访问接口。 */
    @Autowired
    private SsoProtocolLogMapper ssoProtocolLogMapper;

    /** 本用例插入的登录日志账号，供 {@link #tearDown()} 清理与断言定位。 */
    private String loginAccount;

    /** 本用例插入的操作日志被操作对象名称，供 {@link #tearDown()} 清理与断言定位。 */
    private String targetName;

    /** 本用例插入的 SSO 协议调用记录原始 appId，供 {@link #tearDown()} 清理与断言定位。 */
    private String ssoProtocolLogAppId;

    /** 本用例修改前的保留天数配置，供 {@link #tearDown()} 还原。 */
    private int originalRetentionDays;

    /**
     * 每个用例结束后清理本用例插入的测试数据，并还原保留天数配置。
     */
    @AfterEach
    void tearDown() {
        if (loginAccount != null) {
            loginLogMapper.delete(new LambdaQueryWrapper<LoginLogEntity>()
                    .eq(LoginLogEntity::getLoginAccount, loginAccount));
        }
        if (targetName != null) {
            operationLogMapper.delete(new LambdaQueryWrapper<OperationLogEntity>()
                    .eq(OperationLogEntity::getTargetName, targetName));
        }
        if (ssoProtocolLogAppId != null) {
            ssoProtocolLogMapper.delete(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                    .eq(SsoProtocolLogEntity::getAppId, ssoProtocolLogAppId));
        }
        logCleanupProperties.setRetentionDays(originalRetentionDays);
    }

    /**
     * 保留期外的登录日志/操作日志记录应被删除，保留期内的记录应保留（同一次清理内两张表
     * 各自独立生效）。
     */
    @Test
    void cleanup_shouldDeleteRecordsOlderThanRetention_andKeepNewerOnes() {
        originalRetentionDays = logCleanupProperties.getRetentionDays();
        logCleanupProperties.setRetentionDays(30);

        loginAccount = "log-cleanup-test-" + UUID.randomUUID();
        targetName = "log-cleanup-test-" + UUID.randomUUID();
        ssoProtocolLogAppId = "log-cleanup-test-" + UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oldTime = now.minusDays(31);
        LocalDateTime newTime = now.minusDays(1);

        LoginLogEntity oldLoginLog = insertLoginLog(oldTime);
        LoginLogEntity newLoginLog = insertLoginLog(newTime);
        OperationLogEntity oldOperationLog = insertOperationLog(oldTime);
        OperationLogEntity newOperationLog = insertOperationLog(newTime);
        SsoProtocolLogEntity oldSsoProtocolLog = insertSsoProtocolLog(oldTime);
        SsoProtocolLogEntity newSsoProtocolLog = insertSsoProtocolLog(newTime);

        scheduler.cleanup();

        assertThat(loginLogMapper.selectById(oldLoginLog.getId())).isNull();
        assertThat(loginLogMapper.selectById(newLoginLog.getId())).isNotNull();
        assertThat(operationLogMapper.selectById(oldOperationLog.getId())).isNull();
        assertThat(operationLogMapper.selectById(newOperationLog.getId())).isNotNull();
        assertThat(ssoProtocolLogMapper.selectById(oldSsoProtocolLog.getId())).isNull();
        assertThat(ssoProtocolLogMapper.selectById(newSsoProtocolLog.getId())).isNotNull();
    }

    /**
     * 插入一条指定创建时间的登录日志记录。
     *
     * @param createTime 创建时间
     * @return 插入后的登录日志实体（含自增主键）
     */
    private LoginLogEntity insertLoginLog(LocalDateTime createTime) {
        LoginLogEntity entity = LoginLogEntity.builder()
                .loginAccount(loginAccount)
                .loginResult(LoginResult.SUCCESS)
                .createBy(loginAccount)
                .createTime(createTime)
                .updateBy(loginAccount)
                .updateTime(createTime)
                .build();
        loginLogMapper.insert(entity);
        return entity;
    }

    /**
     * 插入一条指定创建时间的操作日志记录。
     *
     * @param createTime 创建时间
     * @return 插入后的操作日志实体（含自增主键）
     */
    private OperationLogEntity insertOperationLog(LocalDateTime createTime) {
        OperationLogEntity entity = OperationLogEntity.builder()
                .module("日志清理测试")
                .resourceType("TEST")
                .resourceName("测试资源")
                .operationType(OperationType.CREATE)
                .operateSource(0)
                .targetId(1L)
                .targetName(targetName)
                .changeDetail("[]")
                .createBy("test")
                .createTime(createTime)
                .updateBy("test")
                .updateTime(createTime)
                .build();
        operationLogMapper.insert(entity);
        return entity;
    }

    /**
     * 插入一条指定创建时间的 SSO 协议调用记录（add-sso-protocol-access-log change tasks.md
     * 6.4）。
     *
     * @param createTime 创建时间
     * @return 插入后的 SSO 协议调用记录实体（含自增主键）
     */
    private SsoProtocolLogEntity insertSsoProtocolLog(LocalDateTime createTime) {
        SsoProtocolLogEntity entity = SsoProtocolLogEntity.builder()
                .appId(ssoProtocolLogAppId)
                .protocol("CAS")
                .eventType(SsoProtocolLogEventType.LOGIN)
                .result(SsoProtocolLogResult.SUCCESS)
                .createBy(ssoProtocolLogAppId)
                .createTime(createTime)
                .updateBy(ssoProtocolLogAppId)
                .updateTime(createTime)
                .build();
        ssoProtocolLogMapper.insert(entity);
        return entity;
    }
}
