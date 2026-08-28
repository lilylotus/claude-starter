package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.appaccess.policy.constant.PolicyAttrOperator;
import cn.nihility.rbac.appaccess.policy.constant.PolicyStatus;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyGrantEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyOrgScopeEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyTargetAppEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyUserAttrEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyGrantMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyOrgScopeMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyTargetAppMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyUserAttrMapper;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link DomainChangeEventProcessor} 组织/用户/任职变更后策略自动重新执行分支的集成测试
 * （close-sso-log-and-policy-gaps change tasks.md 2.4），起真实 MySQL 连接，直接调用
 * {@code process}（同步调用，不经过 Disruptor 异步转发，行为等价，避免测试等待异步结果
 * 引入的不确定性）：新增用户命中组织范围策略自动产生授权记录、编辑用户属性命中属性条件
 * 策略自动产生授权记录、停用用户自动从"仅请求控制"策略的全量启用用户命中集合中移除、
 * 单条策略执行失败不影响其余策略继续重新执行。
 */
@SpringBootTest
class DomainChangeEventProcessorPolicyReExecutionTest {

    /** 被测组件。 */
    @Autowired
    private DomainChangeEventProcessor processor;

    /** 组织数据访问接口。 */
    @Autowired
    private OrgMapper orgMapper;

    /** 用户数据访问接口。 */
    @Autowired
    private UserMapper userMapper;

    /** 用户任职记录数据访问接口。 */
    @Autowired
    private UserPositionMapper userPositionMapper;

    /** 策略规则数据访问接口。 */
    @Autowired
    private PolicyMapper policyMapper;

    /** 策略组织范围条件数据访问接口。 */
    @Autowired
    private PolicyOrgScopeMapper policyOrgScopeMapper;

    /** 策略用户属性条件数据访问接口。 */
    @Autowired
    private PolicyUserAttrMapper policyUserAttrMapper;

    /** 策略目标应用数据访问接口。 */
    @Autowired
    private PolicyTargetAppMapper policyTargetAppMapper;

    /** 策略计算结果数据访问接口。 */
    @Autowired
    private PolicyGrantMapper policyGrantMapper;

    /** 元数据字段数据访问接口，查询 {@code gender} 字段 id 构造属性条件测试数据。 */
    @Autowired
    private MetadataFieldMapper metadataFieldMapper;

    /** 本用例插入的组织 id 列表，供 {@link #tearDown()} 清理。 */
    private final List<Long> orgIds = new ArrayList<>();

    /** 本用例插入的用户 id 列表，供 {@link #tearDown()} 清理。 */
    private final List<Long> userIds = new ArrayList<>();

    /** 本用例插入的策略 id 列表，供 {@link #tearDown()} 清理。 */
    private final List<Long> policyIds = new ArrayList<>();

    /**
     * 每个用例执行前设置一个已登录操作人上下文：{@code PolicyExecutionServiceImpl#execute}
     * 内部通过 {@code CurrentOperatorService} 解析当前操作人 id，脱离已登录上下文调用会
     * 抛出 {@link IllegalStateException}，测试环境没有真实 HTTP 请求，需要手动设置。
     */
    @BeforeEach
    void setUp() {
        CurrentUserContext.setUserId(1L);
    }

    /**
     * 每个用例结束后清理本用例插入的测试数据，并清空线程级当前登录用户标记。
     */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
        for (Long policyId : policyIds) {
            policyOrgScopeMapper.delete(new LambdaQueryWrapper<PolicyOrgScopeEntity>()
                    .eq(PolicyOrgScopeEntity::getPolicyId, policyId));
            policyUserAttrMapper.delete(new LambdaQueryWrapper<PolicyUserAttrEntity>()
                    .eq(PolicyUserAttrEntity::getPolicyId, policyId));
            policyTargetAppMapper.delete(new LambdaQueryWrapper<PolicyTargetAppEntity>()
                    .eq(PolicyTargetAppEntity::getPolicyId, policyId));
            policyGrantMapper.delete(new LambdaQueryWrapper<PolicyGrantEntity>()
                    .eq(PolicyGrantEntity::getPolicyId, policyId));
            policyMapper.deleteById(policyId);
        }
        for (Long userId : userIds) {
            userPositionMapper.delete(new LambdaQueryWrapper<UserPositionEntity>()
                    .eq(UserPositionEntity::getUserId, userId));
            userMapper.deleteById(userId);
        }
        for (Long orgId : orgIds) {
            orgMapper.deleteById(orgId);
        }
    }

    /**
     * 新增用户并挂到某组织范围策略已配置的组织下后，触发一次 {@code USER}/{@code CREATE}
     * 事件，应自动产生该策略针对该用户的授权记录，无需管理员手动点"执行"。
     */
    @Test
    void process_shouldAutoGrant_whenNewUserMatchesOrgScopePolicy() {
        Long orgId = insertOrg();
        Long policyId = insertPolicy();
        insertOrgScope(policyId, orgId);
        insertTargetApp(policyId, 9001L);

        Long userId = insertUser(UserStatus.ENABLED, "male");
        insertPosition(userId, orgId);

        processor.process(userEvent(userId, OperationType.CREATE));

        List<PolicyGrantEntity> grants = policyGrantMapper.selectList(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, policyId).eq(PolicyGrantEntity::getUserId, userId));
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).getAppId()).isEqualTo(9001L);
    }

    /**
     * 编辑用户属性（{@code gender}）使其满足某属性条件策略后，触发一次 {@code USER}/
     * {@code UPDATE} 事件，应自动产生该策略针对该用户的授权记录。
     */
    @Test
    void process_shouldAutoGrant_whenUserAttrEditMatchesAttrConditionPolicy() {
        Long policyId = insertPolicy();
        insertUserAttr(policyId, "male");
        insertTargetApp(policyId, 9002L);

        Long userId = insertUser(UserStatus.ENABLED, "female");
        processor.process(userEvent(userId, OperationType.CREATE));
        List<PolicyGrantEntity> beforeEdit = policyGrantMapper.selectList(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, policyId).eq(PolicyGrantEntity::getUserId, userId));
        assertThat(beforeEdit).isEmpty();

        userMapper.update(null, updateGender(userId, "male"));
        processor.process(userEvent(userId, OperationType.UPDATE));

        List<PolicyGrantEntity> afterEdit = policyGrantMapper.selectList(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, policyId).eq(PolicyGrantEntity::getUserId, userId));
        assertThat(afterEdit).hasSize(1);
    }

    /**
     * 组织范围与用户属性条件均未配置（仅依赖请求控制收窄）的策略，命中集合为全部启用状态
     * 用户；停用用户后触发一次 {@code USER}/{@code DISABLE} 事件重新执行，应自动把该用户
     * 从授权记录中移除（close-sso-log-and-policy-gaps change design.md Decision 3）。
     */
    @Test
    void process_shouldAutoRemoveGrant_whenUserDisabled() {
        Long policyId = insertPolicy();
        insertTargetApp(policyId, 9003L);
        Long userId = insertUser(UserStatus.ENABLED, "male");

        processor.process(userEvent(userId, OperationType.CREATE));
        List<PolicyGrantEntity> beforeDisable = policyGrantMapper.selectList(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, policyId).eq(PolicyGrantEntity::getUserId, userId));
        assertThat(beforeDisable).hasSize(1);

        userMapper.update(null, updateStatus(userId, UserStatus.DISABLED));
        processor.process(userEvent(userId, OperationType.DISABLE));

        List<PolicyGrantEntity> afterDisable = policyGrantMapper.selectList(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, policyId).eq(PolicyGrantEntity::getUserId, userId));
        assertThat(afterDisable).isEmpty();
    }

    /**
     * 一条策略执行失败（关联的元数据字段不存在）不应影响其余启用策略继续重新执行成功
     * （close-sso-log-and-policy-gaps change design.md Decision 2）。
     */
    @Test
    void process_shouldContinueOtherPolicies_whenOnePolicyExecutionFails() {
        Long failingPolicyId = insertPolicy();
        insertUserAttr(failingPolicyId, "male");
        // 强行改成一个不存在的 metadataFieldId，令该策略执行时抛出异常。
        policyUserAttrMapper.update(null, new LambdaUpdateWrapper<PolicyUserAttrEntity>()
                .eq(PolicyUserAttrEntity::getPolicyId, failingPolicyId)
                .set(PolicyUserAttrEntity::getMetadataFieldId, 999999999L));
        insertTargetApp(failingPolicyId, 9004L);

        Long orgId = insertOrg();
        Long healthyPolicyId = insertPolicy();
        insertOrgScope(healthyPolicyId, orgId);
        insertTargetApp(healthyPolicyId, 9005L);

        Long userId = insertUser(UserStatus.ENABLED, "male");
        insertPosition(userId, orgId);

        processor.process(userEvent(userId, OperationType.CREATE));

        List<PolicyGrantEntity> healthyGrants = policyGrantMapper.selectList(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, healthyPolicyId).eq(PolicyGrantEntity::getUserId, userId));
        assertThat(healthyGrants).hasSize(1);
    }

    /**
     * 非组织/用户/任职数据域的事件不应触发策略重新执行（此处仅验证调用不抛异常，具体
     * 数据域过滤逻辑已由该分支的前置判断保证）。
     */
    @Test
    void process_shouldNotThrow_whenDataTypeNotEligible() {
        DomainChangeEvent event = DomainChangeEvent.builder()
                .dataType(SyncDomain.APP)
                .bizId(1L)
                .operationType(OperationType.CREATE)
                .operator("1")
                .eventId(System.nanoTime())
                .entityVersion(1L)
                .occurredAt(LocalDateTime.now())
                .build();

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
    }

    /**
     * 插入一个测试用组织。
     *
     * @return 组织 id
     */
    private Long insertOrg() {
        LocalDateTime now = LocalDateTime.now();
        OrgEntity org = OrgEntity.builder()
                .name("策略自动重算测试组织")
                .code("policy-re-exec-org-" + UUID.randomUUID())
                .parentId(0L)
                .status(2000)
                .showOrder(0)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        orgMapper.insert(org);
        orgIds.add(org.getId());
        return org.getId();
    }

    /**
     * 插入一个启用状态的测试用户。
     *
     * @param status 用户状态
     * @param gender 性别字典编码
     * @return 用户 id
     */
    private Long insertUser(int status, String gender) {
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .name("策略自动重算测试用户")
                .code("policy-re-exec-user-" + UUID.randomUUID())
                .gender(gender)
                .showOrder(0)
                .status(status)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userMapper.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    /**
     * 为指定用户插入一条挂在指定组织下的启用状态任职记录。
     *
     * @param userId 用户 id
     * @param orgId  组织 id
     */
    private void insertPosition(Long userId, Long orgId) {
        LocalDateTime now = LocalDateTime.now();
        userPositionMapper.insert(UserPositionEntity.builder()
                .userId(userId)
                .orgId(orgId)
                .positionType("primary")
                .status(PositionStatus.ENABLED)
                .showOrder(0)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 插入一条启用状态的策略主表记录。
     *
     * @return 策略 id
     */
    private Long insertPolicy() {
        LocalDateTime now = LocalDateTime.now();
        PolicyEntity policy = PolicyEntity.builder()
                .name("策略自动重算测试策略-" + UUID.randomUUID())
                .status(PolicyStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        policyMapper.insert(policy);
        policyIds.add(policy.getId());
        return policy.getId();
    }

    /**
     * 为指定策略插入一条组织范围条件（不含子组织）。
     *
     * @param policyId 策略 id
     * @param orgId    组织 id
     */
    private void insertOrgScope(Long policyId, Long orgId) {
        LocalDateTime now = LocalDateTime.now();
        policyOrgScopeMapper.insert(PolicyOrgScopeEntity.builder()
                .policyId(policyId)
                .orgId(orgId)
                .includeChildren(false)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 为指定策略插入一条 {@code gender=genderValue} 的用户属性条件。
     *
     * @param policyId    策略 id
     * @param genderValue 比较值
     */
    private void insertUserAttr(Long policyId, String genderValue) {
        MetadataFieldEntity genderField = metadataFieldMapper.selectOne(new LambdaQueryWrapper<MetadataFieldEntity>()
                .eq(MetadataFieldEntity::getBizType, FormFieldBizType.USER)
                .eq(MetadataFieldEntity::getColumnName, "gender"));
        LocalDateTime now = LocalDateTime.now();
        policyUserAttrMapper.insert(PolicyUserAttrEntity.builder()
                .policyId(policyId)
                .metadataFieldId(genderField.getId())
                .operator(PolicyAttrOperator.EQ)
                .attrValue(genderValue)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 为指定策略插入一条目标应用记录。
     *
     * @param policyId 策略 id
     * @param appId    目标应用 id（测试内自定义，不要求真实存在，表结构无外键约束）
     */
    private void insertTargetApp(Long policyId, Long appId) {
        LocalDateTime now = LocalDateTime.now();
        policyTargetAppMapper.insert(PolicyTargetAppEntity.builder()
                .policyId(policyId)
                .appId(appId)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 构造一条 {@code USER} 数据域的领域变更事件。{@code eventId}/{@code entityVersion}
     * 显式填充固定测试值：{@code process} 现在会先把事件写入全局变更流水表
     * （{@code tab_app_data_change_log.event_id}/{@code entity_version} 均为 {@code NOT NULL}
     * 列，app-sync-changelog-pull change design.md Decision 6），不填充会导致流水写入失败、
     * 被 {@code process} 内部 catch 吞掉后跳过通知分支，虽不影响本类关注的策略重新执行分支
     * （两者各自独立 try/catch），但会在测试输出中产生无意义的异常日志噪音。
     *
     * @param userId        用户 id
     * @param operationType 操作类型
     * @return 领域变更事件
     */
    private DomainChangeEvent userEvent(Long userId, int operationType) {
        return DomainChangeEvent.builder()
                .dataType(SyncDomain.USER)
                .bizId(userId)
                .operationType(operationType)
                .operator("test")
                .eventId(System.nanoTime())
                .entityVersion(1L)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    /**
     * 构造一个只更新 {@code gender} 字段的更新条件包装器。
     *
     * @param userId 用户 id
     * @param gender 新的性别字典编码
     * @return 更新条件包装器
     */
    private LambdaUpdateWrapper<UserEntity> updateGender(Long userId, String gender) {
        return new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, userId).set(UserEntity::getGender, gender);
    }

    /**
     * 构造一个只更新 {@code status} 字段的更新条件包装器。
     *
     * @param userId 用户 id
     * @param status 新的用户状态
     * @return 更新条件包装器
     */
    private LambdaUpdateWrapper<UserEntity> updateStatus(Long userId, int status) {
        return new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, userId).set(UserEntity::getStatus, status);
    }
}
