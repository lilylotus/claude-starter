package cn.nihility.rbac.userrole.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.userrole.entity.UserRoleRuleEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleGrantEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleOrgScopeEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleUserAttrEntity;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleGrantMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleOrgScopeMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleUserAttrMapper;
import cn.nihility.rbac.userrole.support.UserMatchConditionResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserRoleRuleExecutionServiceImpl} 的单元测试，覆盖新增命中用户产生 grant、不再
 * 命中的用户 grant 被删除、用户仍被其他规则命中时不误触发管理员联动停用、规则删除级联收回
 * （{@code revokeAll}）、{@code operator} 显式传参不依赖 {@code CurrentOperatorService}
 * （本测试全程不设置任何登录上下文，且被测类构造参数中本就不包含该依赖，物理上不可能调用它）
 * （add-user-role-batch-assignment change tasks.md 3.5）。
 */
@ExtendWith(MockitoExtension.class)
class UserRoleRuleExecutionServiceImplTest {

    /** 被测服务的用户角色规则数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserRoleRuleMapper userRoleRuleMapper;

    /** 被测服务的用户角色规则组织范围条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserRoleRuleOrgScopeMapper userRoleRuleOrgScopeMapper;

    /** 被测服务的用户角色规则用户属性条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserRoleRuleUserAttrMapper userRoleRuleUserAttrMapper;

    /** 被测服务的用户角色规则计算结果数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserRoleRuleGrantMapper userRoleRuleGrantMapper;

    /** 被测服务的条件匹配组件依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMatchConditionResolver conditionResolver;

    /** 被测服务的管理员数据访问依赖（跨模块注入），使用 Mockito 打桩。 */
    @Mock
    private AdminMapper adminMapper;

    /** 被测服务实例。 */
    private UserRoleRuleExecutionServiceImpl executionService;

    /**
     * 每个用例执行前重新构造被测服务，并对无关分支做宽松默认打桩。
     */
    @BeforeEach
    void setUp() {
        executionService = new UserRoleRuleExecutionServiceImpl(userRoleRuleMapper, userRoleRuleOrgScopeMapper,
                userRoleRuleUserAttrMapper, userRoleRuleGrantMapper, conditionResolver, adminMapper);
        lenient().when(userRoleRuleOrgScopeMapper.selectList(any())).thenReturn(List.of());
        lenient().when(userRoleRuleUserAttrMapper.selectList(any())).thenReturn(List.of());
        lenient().when(adminMapper.selectList(any())).thenReturn(List.of());
    }

    /**
     * 新增命中的用户应批量插入规则计算结果，并更新规则的 {@code lastExecTime}/
     * {@code lastExecBy}。
     */
    @Test
    void execute_shouldInsertGrants_forNewlyMatchedUsers() {
        UserRoleRuleEntity rule = UserRoleRuleEntity.builder().id(1L).roleId(100L).build();
        when(userRoleRuleMapper.selectById(1L)).thenReturn(rule);
        when(conditionResolver.resolve(List.of(), List.of())).thenReturn(Set.of(1L, 2L));
        when(userRoleRuleGrantMapper.selectList(any())).thenReturn(List.of());

        executionService.execute(1L, "op-1");

        ArgumentCaptor<List<UserRoleRuleGrantEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleRuleGrantMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).extracting(UserRoleRuleGrantEntity::getUserId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getValue()).allSatisfy(grant -> {
            assertThat(grant.getRuleId()).isEqualTo(1L);
            assertThat(grant.getRoleId()).isEqualTo(100L);
            assertThat(grant.getCreateBy()).isEqualTo("op-1");
        });
        verify(userRoleRuleGrantMapper, never()).delete(any());

        ArgumentCaptor<UserRoleRuleEntity> ruleCaptor = ArgumentCaptor.forClass(UserRoleRuleEntity.class);
        verify(userRoleRuleMapper).updateById(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getLastExecBy()).isEqualTo("op-1");
        assertThat(ruleCaptor.getValue().getLastExecTime()).isNotNull();
    }

    /**
     * 不再命中的用户，其对应的规则计算结果应被删除；若该用户该角色已无其他规则命中，且
     * 其存在一条 {@code autoCreatedRoleId} 匹配的未删除管理员记录，应联动将其状态置为停用。
     */
    @Test
    void execute_shouldRemoveGrant_andRevokeAutoCreatedAdmin_whenNoLongerGrantedByAnyRule() {
        UserRoleRuleEntity rule = UserRoleRuleEntity.builder().id(1L).roleId(100L).build();
        when(userRoleRuleMapper.selectById(1L)).thenReturn(rule);
        when(conditionResolver.resolve(List.of(), List.of())).thenReturn(Set.of());
        when(userRoleRuleGrantMapper.selectList(any())).thenReturn(List.of(
                UserRoleRuleGrantEntity.builder().ruleId(1L).userId(5L).roleId(100L).build()));
        when(userRoleRuleGrantMapper.selectCount(any())).thenReturn(0L);
        AdminEntity admin = AdminEntity.builder().id(50L).userId(5L).autoCreatedRoleId(100L)
                .status(AdminStatus.ENABLED).build();
        when(adminMapper.selectList(any())).thenReturn(List.of(admin));

        executionService.execute(1L, "op-2");

        verify(userRoleRuleGrantMapper).delete(any());
        verify(userRoleRuleGrantMapper, never()).insertBatch(any());

        ArgumentCaptor<AdminEntity> adminCaptor = ArgumentCaptor.forClass(AdminEntity.class);
        verify(adminMapper).updateById(adminCaptor.capture());
        assertThat(adminCaptor.getValue().getStatus()).isEqualTo(AdminStatus.DISABLED);
        assertThat(adminCaptor.getValue().getUpdateBy()).isEqualTo("op-2");
        assertThat(adminCaptor.getValue().getAutoCreatedRoleId()).isEqualTo(100L);
    }

    /**
     * 用户仍被其他规则命中该角色时（本规则的收回只是众多规则之一），不应触发管理员联动
     * 停用检查——即使该用户存在一条看起来匹配的自动创建管理员记录，也不应该被查询/停用。
     */
    @Test
    void execute_shouldNotRevokeAdmin_whenUserStillGrantedByOtherRule() {
        UserRoleRuleEntity rule = UserRoleRuleEntity.builder().id(1L).roleId(100L).build();
        when(userRoleRuleMapper.selectById(1L)).thenReturn(rule);
        when(conditionResolver.resolve(List.of(), List.of())).thenReturn(Set.of());
        when(userRoleRuleGrantMapper.selectList(any())).thenReturn(List.of(
                UserRoleRuleGrantEntity.builder().ruleId(1L).userId(5L).roleId(100L).build()));
        // 该用户该角色仍有其他规则（如 ruleId=2）命中，剩余计数 > 0。
        when(userRoleRuleGrantMapper.selectCount(any())).thenReturn(1L);

        executionService.execute(1L, "op-3");

        verify(userRoleRuleGrantMapper).delete(any());
        verify(adminMapper, never()).selectList(any());
        verify(adminMapper, never()).updateById(any(AdminEntity.class));
    }

    /**
     * 目标规则不存在时应抛出业务异常，且不产生任何写操作。
     */
    @Test
    void execute_shouldThrowBusinessException_whenRuleNotFound() {
        when(userRoleRuleMapper.selectById(99L)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executionService.execute(99L, "op"))
                .isInstanceOf(BusinessException.class);
        verify(userRoleRuleGrantMapper, never()).insertBatch(any());
        verify(userRoleRuleGrantMapper, never()).delete(any());
    }

    /**
     * {@code revokeAll} 应把该规则名下全部既有计算结果当作待收回处理，触发对应的联动停用
     * 检查，供规则删除前的级联收回使用（design.md Decision 3a）。
     */
    @Test
    void revokeAll_shouldRemoveAllGrants_andTriggerCascadeCheckForEachUser() {
        UserRoleRuleEntity rule = UserRoleRuleEntity.builder().id(1L).roleId(100L).build();
        when(userRoleRuleMapper.selectById(1L)).thenReturn(rule);
        when(userRoleRuleGrantMapper.selectList(any())).thenReturn(List.of(
                UserRoleRuleGrantEntity.builder().ruleId(1L).userId(7L).roleId(100L).build(),
                UserRoleRuleGrantEntity.builder().ruleId(1L).userId(8L).roleId(100L).build()));
        when(userRoleRuleGrantMapper.selectCount(any())).thenReturn(0L);

        executionService.revokeAll(1L, "op-4");

        verify(userRoleRuleGrantMapper, times(1)).delete(any());
        verify(userRoleRuleGrantMapper, times(2)).selectCount(any());
        verify(adminMapper, times(2)).selectList(any());
        // revokeAll 不更新规则本身的 lastExecTime/lastExecBy（规则即将被物理删除）。
        verify(userRoleRuleMapper, never()).updateById(any(UserRoleRuleEntity.class));
    }

    /**
     * {@code revokeAll} 面对不存在的规则应静默跳过，不产生任何写操作。
     */
    @Test
    void revokeAll_shouldDoNothing_whenRuleNotFound() {
        when(userRoleRuleMapper.selectById(99L)).thenReturn(null);

        assertThatCode(() -> executionService.revokeAll(99L, "op")).doesNotThrowAnyException();
        verify(userRoleRuleGrantMapper, never()).selectList(any());
        verify(userRoleRuleGrantMapper, never()).delete(any());
    }

    /**
     * {@code execute} 的 {@code operator} 必须由调用方显式传入：被测类构造参数中不包含
     * {@code CurrentOperatorService} 依赖，物理上不可能调用它解析当前登录用户；本用例全程
     * 不设置任何登录上下文（不调用 {@code CurrentUserContext.setUserId}），验证方法仍能
     * 正常执行完成，规则的 {@code lastExecBy} 精确等于显式传入的 {@code operator} 字符串
     * （而非从上下文解析得到的值），证明未重蹈 close-sso-log-and-policy-gaps change 归档
     * 记录过的历史 bug。
     */
    @Test
    void execute_shouldUseExplicitOperator_withoutAnyLoginContext() {
        UserRoleRuleEntity rule = UserRoleRuleEntity.builder().id(1L).roleId(100L).build();
        when(userRoleRuleMapper.selectById(1L)).thenReturn(rule);
        when(conditionResolver.resolve(List.of(), List.of())).thenReturn(Set.of());
        when(userRoleRuleGrantMapper.selectList(any())).thenReturn(List.of());

        assertThatCode(() -> executionService.execute(1L, "event-driven-operator")).doesNotThrowAnyException();

        ArgumentCaptor<UserRoleRuleEntity> ruleCaptor = ArgumentCaptor.forClass(UserRoleRuleEntity.class);
        verify(userRoleRuleMapper).updateById(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getLastExecBy()).isEqualTo("event-driven-operator");
    }

    /**
     * 加载规则条件时应把持久化的组织范围/用户属性条件实体转换为
     * {@code UserMatchConditionResolver} 的输入类型并原样传入。
     */
    @Test
    void execute_shouldConvertPersistedConditions_toResolverInputTypes() {
        UserRoleRuleEntity rule = UserRoleRuleEntity.builder().id(1L).roleId(100L).build();
        when(userRoleRuleMapper.selectById(1L)).thenReturn(rule);
        when(userRoleRuleOrgScopeMapper.selectList(any())).thenReturn(List.of(
                UserRoleRuleOrgScopeEntity.builder().ruleId(1L).orgId(10L).includeChildren(true).build()));
        when(userRoleRuleUserAttrMapper.selectList(any())).thenReturn(List.of(
                UserRoleRuleUserAttrEntity.builder().ruleId(1L).metadataFieldId(2L).operator("EQ")
                        .attrValue("male").build()));
        when(userRoleRuleGrantMapper.selectList(any())).thenReturn(List.of());
        when(conditionResolver.resolve(any(), any())).thenReturn(Set.of());

        executionService.execute(1L, "op-5");

        ArgumentCaptor<List> orgScopeCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> userAttrCaptor = ArgumentCaptor.forClass(List.class);
        verify(conditionResolver).resolve(orgScopeCaptor.capture(), userAttrCaptor.capture());
        assertThat(orgScopeCaptor.getValue()).hasSize(1);
        assertThat(userAttrCaptor.getValue()).hasSize(1);
    }
}
