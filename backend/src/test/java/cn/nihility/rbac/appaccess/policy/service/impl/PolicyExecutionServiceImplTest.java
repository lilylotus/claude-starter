package cn.nihility.rbac.appaccess.policy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.appaccess.policy.constant.PolicyAttrOperator;
import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;
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
import cn.nihility.rbac.appaccess.policy.service.PolicyService;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PolicyExecutionServiceImpl} 的单元测试（tasks.md 8.2），覆盖仅组织范围、仅属性
 * 条件、组织范围+属性条件取交集三种组合、组织范围展开含子组织/不含子组织、
 * {@code EQ}/{@code NE}/{@code IN} 三种运算符匹配、命中用户去重、笛卡尔积生成、重新执行
 * 整体替换、执行不影响人工例外。
 */
@ExtendWith(MockitoExtension.class)
class PolicyExecutionServiceImplTest {

    /** 被测服务的策略规则数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyMapper policyMapper;

    /** 被测服务的策略组织范围条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyOrgScopeMapper policyOrgScopeMapper;

    /** 被测服务的策略用户属性条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyUserAttrMapper policyUserAttrMapper;

    /** 被测服务的策略目标应用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyTargetAppMapper policyTargetAppMapper;

    /** 被测服务的策略计算结果数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyGrantMapper policyGrantMapper;

    /** 被测服务的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测服务的用户任职记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测服务的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测服务的组织子孙展开工具依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgDescendantExpander orgDescendantExpander;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务的策略规则业务逻辑依赖（复用其详情组装逻辑），使用 Mockito 打桩。 */
    @Mock
    private PolicyService policyService;

    /** 被测服务实例。 */
    private PolicyExecutionServiceImpl executionService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        executionService = new PolicyExecutionServiceImpl(policyMapper, policyOrgScopeMapper, policyUserAttrMapper,
                policyTargetAppMapper, policyGrantMapper, metadataFieldMapper, userPositionMapper, userMapper,
                orgDescendantExpander, currentOperatorService, policyService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().when(policyMapper.selectById(100L)).thenReturn(PolicyEntity.builder().id(100L).build());
        lenient().when(policyTargetAppMapper.selectList(any())).thenReturn(List.of(
                PolicyTargetAppEntity.builder().policyId(100L).appId(500L).build()));
        lenient().when(policyService.detail(100L)).thenReturn(PolicyVO.builder().id(100L).build());
    }

    /**
     * 仅配置组织范围（不含子组织）时，命中用户按 tab_user_position 直接匹配，与目标应用做
     * 笛卡尔积。
     */
    @Test
    void execute_shouldMatchByOrgScopeOnly_withoutIncludeChildren() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(false).build()));
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of());
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(2L).orgId(10L).status(PositionStatus.ENABLED).build()));

        executionService.execute(100L);

        verify(policyGrantMapper).delete(any());
        ArgumentCaptor<PolicyGrantEntity> captor = ArgumentCaptor.forClass(PolicyGrantEntity.class);
        verify(policyGrantMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(PolicyGrantEntity::getUserId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getAllValues()).allSatisfy(grant -> {
            assertThat(grant.getPolicyId()).isEqualTo(100L);
            assertThat(grant.getAppId()).isEqualTo(500L);
        });
    }

    /**
     * 组织范围配置了含子组织的行时，应调用 {@link OrgDescendantExpander} 展开子孙组织后再
     * 查询任职记录。
     */
    @Test
    void execute_shouldExpandDescendants_whenIncludeChildren() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(true).build()));
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of());
        when(orgDescendantExpander.expandWithDescendants(Set.of(10L))).thenReturn(Set.of(10L, 11L, 12L));
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(3L).orgId(11L).status(PositionStatus.ENABLED).build()));

        executionService.execute(100L);

        verify(orgDescendantExpander).expandWithDescendants(Set.of(10L));
        ArgumentCaptor<PolicyGrantEntity> captor = ArgumentCaptor.forClass(PolicyGrantEntity.class);
        verify(policyGrantMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(3L);
    }

    /**
     * 仅配置用户属性条件（{@code EQ}）时，按解析出的物理列名匹配用户，不做组织范围限定。
     */
    @Test
    void execute_shouldMatchByUserAttrOnly_withEqOperator() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of());
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of(
                PolicyUserAttrEntity.builder().policyId(100L).metadataFieldId(1L)
                        .operator(PolicyAttrOperator.EQ).attrValue("male").build()));
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField());
        when(userMapper.selectIdsByAttrCondition(eq("gender"), eq(PolicyAttrOperator.EQ), eq("male"), any()))
                .thenReturn(List.of(1L, 2L));

        executionService.execute(100L);

        verify(userPositionMapper, never()).selectList(any());
        ArgumentCaptor<PolicyGrantEntity> captor = ArgumentCaptor.forClass(PolicyGrantEntity.class);
        verify(policyGrantMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(PolicyGrantEntity::getUserId).containsExactlyInAnyOrder(1L, 2L);
    }

    /**
     * {@code IN} 运算符按逗号拆分多值匹配。
     */
    @Test
    void execute_shouldMatchByUserAttr_withInOperator() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of());
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of(
                PolicyUserAttrEntity.builder().policyId(100L).metadataFieldId(1L)
                        .operator(PolicyAttrOperator.IN).attrValue("male,female").build()));
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField());
        when(userMapper.selectIdsByAttrCondition(eq("gender"), eq(PolicyAttrOperator.IN), eq(null),
                eq(List.of("male", "female")))).thenReturn(List.of(1L, 2L, 3L));

        executionService.execute(100L);

        ArgumentCaptor<PolicyGrantEntity> captor = ArgumentCaptor.forClass(PolicyGrantEntity.class);
        verify(policyGrantMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(PolicyGrantEntity::getUserId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    /**
     * {@code NE} 运算符匹配不等于给定值的用户。
     */
    @Test
    void execute_shouldMatchByUserAttr_withNeOperator() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of());
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of(
                PolicyUserAttrEntity.builder().policyId(100L).metadataFieldId(1L)
                        .operator(PolicyAttrOperator.NE).attrValue("male").build()));
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField());
        when(userMapper.selectIdsByAttrCondition(eq("gender"), eq(PolicyAttrOperator.NE), eq("male"), any()))
                .thenReturn(List.of(4L));

        executionService.execute(100L);

        ArgumentCaptor<PolicyGrantEntity> captor = ArgumentCaptor.forClass(PolicyGrantEntity.class);
        verify(policyGrantMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(4L);
    }

    /**
     * 组织范围与用户属性条件都配置时，命中用户取两者交集。
     */
    @Test
    void execute_shouldIntersect_whenBothOrgScopeAndUserAttrConfigured() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(false).build()));
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(2L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(3L).orgId(10L).status(PositionStatus.ENABLED).build()));

        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of(
                PolicyUserAttrEntity.builder().policyId(100L).metadataFieldId(1L)
                        .operator(PolicyAttrOperator.EQ).attrValue("male").build()));
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField());
        when(userMapper.selectIdsByAttrCondition(eq("gender"), eq(PolicyAttrOperator.EQ), eq("male"), any()))
                .thenReturn(List.of(2L, 3L, 4L));

        executionService.execute(100L);

        ArgumentCaptor<PolicyGrantEntity> captor = ArgumentCaptor.forClass(PolicyGrantEntity.class);
        verify(policyGrantMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(PolicyGrantEntity::getUserId).containsExactlyInAnyOrder(2L, 3L);
    }

    /**
     * 命中用户在任职记录中存在多条记录时应去重，与目标应用做笛卡尔积。
     */
    @Test
    void execute_shouldDeduplicateHitUsers() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(false).build(),
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(20L).includeChildren(false).build()));
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of());
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(1L).orgId(20L).status(PositionStatus.ENABLED).build()));

        executionService.execute(100L);

        verify(policyGrantMapper, times(1)).insert(any(PolicyGrantEntity.class));
    }

    /**
     * 命中用户与多个目标应用做笛卡尔积，产生的授权记录数应等于用户数乘以应用数。
     */
    @Test
    void execute_shouldGenerateCartesianProduct_withMultipleTargetApps() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(false).build()));
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of());
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(2L).orgId(10L).status(PositionStatus.ENABLED).build()));
        when(policyTargetAppMapper.selectList(any())).thenReturn(List.of(
                PolicyTargetAppEntity.builder().policyId(100L).appId(500L).build(),
                PolicyTargetAppEntity.builder().policyId(100L).appId(501L).build()));

        executionService.execute(100L);

        verify(policyGrantMapper, times(4)).insert(any(PolicyGrantEntity.class));
    }

    /**
     * 重新执行是整体重建：先删除该策略此前产生的全部授权记录，再插入本次计算得到的新集合，
     * 并更新执行时间与执行人。
     */
    @Test
    void execute_shouldReplaceExistingGrants_onReExecution() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(false).build()));
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of());
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(9L).orgId(10L).status(PositionStatus.ENABLED).build()));

        executionService.execute(100L);

        verify(policyGrantMapper).delete(any());
        verify(policyGrantMapper).insert(any(PolicyGrantEntity.class));
        ArgumentCaptor<PolicyEntity> policyCaptor = ArgumentCaptor.forClass(PolicyEntity.class);
        verify(policyMapper).updateById(policyCaptor.capture());
        assertThat(policyCaptor.getValue().getLastExecTime()).isNotNull();
        assertThat(policyCaptor.getValue().getLastExecBy()).isEqualTo("1");
    }

    /**
     * 策略执行不应触碰人工例外表：{@code PolicyExecutionServiceImpl} 不依赖也不调用任何
     * 人工例外相关的数据访问接口。
     */
    @Test
    void execute_shouldNotAffectManualOverrides() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(false).build()));
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of());
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build()));

        executionService.execute(100L);

        // PolicyExecutionServiceImpl 的构造参数中不包含任何 tab_app_access_manual_override
        // 数据访问依赖，物理上不可能触碰人工例外记录（design.md Decision 2）。
        verify(policyGrantMapper).delete(any());
    }

    /**
     * 策略执行结果不受请求控制条件（浏览器白名单/IP 白名单）影响：
     * {@code PolicyExecutionServiceImpl} 的构造参数中不包含任何
     * {@code tab_app_access_policy_browser_rule}/{@code tab_app_access_policy_ip_rule}
     * 数据访问依赖，物理上不可能读取或校验请求控制条件；组织范围/用户属性条件完全相同的
     * 两条策略，无论是否配置了请求控制，"执行"产生的命中结果必然一致
     * （app-access-request-control change spec.md"执行时不校验请求控制条件" Scenario）。
     */
    @Test
    void execute_shouldProduceSameGrants_regardlessOfRequestControlConfiguration() {
        when(policyOrgScopeMapper.selectList(any())).thenReturn(List.of(
                PolicyOrgScopeEntity.builder().policyId(100L).orgId(10L).includeChildren(false).build()));
        when(policyUserAttrMapper.selectList(any())).thenReturn(List.of());
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(2L).orgId(10L).status(PositionStatus.ENABLED).build()));

        executionService.execute(100L);

        ArgumentCaptor<PolicyGrantEntity> captor = ArgumentCaptor.forClass(PolicyGrantEntity.class);
        verify(policyGrantMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(PolicyGrantEntity::getUserId).containsExactlyInAnyOrder(1L, 2L);
    }

    /**
     * 构造一个测试用的、状态启用且业务域为 USER 的元数据字段实体，列名为 {@code gender}。
     *
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildUserField() {
        return MetadataFieldEntity.builder()
                .id(1L)
                .bizType(FormFieldBizType.USER)
                .tableName("tab_user")
                .columnName("gender")
                .columnType("VARCHAR(64)")
                .fieldCode("gender")
                .fieldName("性别")
                .status(MetadataFieldStatus.ENABLED)
                .build();
    }
}
