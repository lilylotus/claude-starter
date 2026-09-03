package cn.nihility.rbac.userrole.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.userrole.dto.UserRoleOrgScopeCondition;
import cn.nihility.rbac.userrole.dto.UserRoleRuleCreateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleOrgScopeVO;
import cn.nihility.rbac.userrole.dto.UserRoleRulePreviewRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleUpdateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleUserAttrRow;
import cn.nihility.rbac.userrole.dto.UserRoleRuleVO;
import cn.nihility.rbac.userrole.dto.UserRoleUserAttrCondition;
import cn.nihility.rbac.userrole.entity.UserRoleRuleEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleGrantEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleOrgScopeEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleUserAttrEntity;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleGrantMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleOrgScopeMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleUserAttrMapper;
import cn.nihility.rbac.userrole.service.UserRoleRuleExecutionService;
import cn.nihility.rbac.userrole.support.UserMatchConditionResolver;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserRoleRuleServiceImpl} 的单元测试，覆盖预览不写库、保存即执行、编辑后按新条件
 * 重新执行、两类条件均未配置时拒绝创建/编辑、删除时先收回再物理删除、列表接口不携带条件
 * 明细（避免 N+1）等分支（add-user-role-batch-assignment change tasks.md 3.5）。
 */
@ExtendWith(MockitoExtension.class)
class UserRoleRuleServiceImplTest {

    @Mock
    private UserRoleRuleMapper userRoleRuleMapper;

    @Mock
    private UserRoleRuleOrgScopeMapper userRoleRuleOrgScopeMapper;

    @Mock
    private UserRoleRuleUserAttrMapper userRoleRuleUserAttrMapper;

    @Mock
    private UserRoleRuleGrantMapper userRoleRuleGrantMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPositionMapper userPositionMapper;

    @Mock
    private UserMatchConditionResolver conditionResolver;

    @Mock
    private UserRoleRuleExecutionService executionService;

    @Mock
    private CurrentOperatorService currentOperatorService;

    private UserRoleRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleRuleServiceImpl(userRoleRuleMapper, userRoleRuleOrgScopeMapper,
                userRoleRuleUserAttrMapper, userRoleRuleGrantMapper, roleMapper, userMapper, userPositionMapper,
                conditionResolver, executionService, currentOperatorService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(9L);
        lenient().when(roleMapper.selectById(1L)).thenReturn(RoleEntity.builder().id(1L).status(RoleStatus.ENABLED).build());
        lenient().when(userRoleRuleOrgScopeMapper.selectByRuleId(any())).thenReturn(List.of());
        lenient().when(userRoleRuleUserAttrMapper.selectByRuleId(any())).thenReturn(List.of());
        lenient().when(userRoleRuleGrantMapper.selectCount(any())).thenReturn(0L);
    }

    /**
     * 预览命中用户时，只应查询数据、不应写入规则主表、条件子表或计算结果表。
     */
    @Test
    void preview_shouldNotWriteDatabase() {
        UserRoleOrgScopeCondition orgScope = new UserRoleOrgScopeCondition();
        orgScope.setOrgId(10L);
        orgScope.setIncludeChildren(true);
        UserRoleRulePreviewRequest request = new UserRoleRulePreviewRequest();
        request.setOrgScopes(List.of(orgScope));
        request.setPage(1);
        request.setPageSize(10);

        when(conditionResolver.resolve(List.of(orgScope), List.of())).thenReturn(Set.of(1L, 2L));
        when(conditionResolver.expandOrgScopeIds(List.of(orgScope))).thenReturn(Set.of(10L));

        Page<UserEntity> resultPage = new Page<>(1, 10, 2L);
        resultPage.setRecords(List.of(
                UserEntity.builder().id(1L).name("张三").code("u001").build(),
                UserEntity.builder().id(2L).name("李四").code("u002").build()));
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);
        when(userPositionMapper.selectRepresentativeOrgNames(Set.of(1L, 2L), Set.of(10L), -1000))
                .thenReturn(List.of(PositionVO.builder().userId(1L).orgName("研发部").build()));

        PageResult<?> result = service.preview(request);

        assertThat(result.getTotal()).isEqualTo(2L);
        verify(userRoleRuleMapper, never()).insert(any(UserRoleRuleEntity.class));
        verify(userRoleRuleOrgScopeMapper, never()).insert(any(UserRoleRuleOrgScopeEntity.class));
        verify(userRoleRuleUserAttrMapper, never()).insert(any(UserRoleRuleUserAttrEntity.class));
        verify(userRoleRuleGrantMapper, never()).insertBatch(any());
        verify(executionService, never()).execute(any(), any());
    }

    /**
     * 预览时两类条件均未配置应拒绝请求。
     */
    @Test
    void preview_shouldReject_whenNoConditionsConfigured() {
        UserRoleRulePreviewRequest request = new UserRoleRulePreviewRequest();

        assertThatThrownBy(() -> service.preview(request)).isInstanceOf(BusinessException.class);
        verify(conditionResolver, never()).resolve(any(), any());
    }

    /**
     * 新增规则时应落库规则主表与条件子表，并同步调用一次执行引擎。
     */
    @Test
    void create_shouldPersistRuleAndConditions_andExecuteImmediately() {
        UserRoleOrgScopeCondition orgScope = new UserRoleOrgScopeCondition();
        orgScope.setOrgId(10L);
        orgScope.setIncludeChildren(false);
        UserRoleRuleCreateRequest request = new UserRoleRuleCreateRequest();
        request.setRoleId(1L);
        request.setName("测试规则");
        request.setOrgScopes(List.of(orgScope));

        doAnswer(invocation -> {
            UserRoleRuleEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(userRoleRuleMapper).insert(any(UserRoleRuleEntity.class));
        when(userRoleRuleMapper.selectById(100L))
                .thenReturn(UserRoleRuleEntity.builder().id(100L).roleId(1L).name("测试规则").build());

        UserRoleRuleVO result = service.create(request);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<UserRoleRuleOrgScopeEntity> scopeCaptor = ArgumentCaptor.forClass(UserRoleRuleOrgScopeEntity.class);
        verify(userRoleRuleOrgScopeMapper).insert(scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().getRuleId()).isEqualTo(100L);
        assertThat(scopeCaptor.getValue().getOrgId()).isEqualTo(10L);
        verify(executionService).execute(100L, "9");
    }

    /**
     * 新增规则时两类条件均未配置应拒绝创建，且不落任何库、不触发执行。
     */
    @Test
    void create_shouldReject_whenBothConditionsEmpty() {
        UserRoleRuleCreateRequest request = new UserRoleRuleCreateRequest();
        request.setRoleId(1L);
        request.setName("测试规则");

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class);
        verify(userRoleRuleMapper, never()).insert(any(UserRoleRuleEntity.class));
        verify(executionService, never()).execute(any(), any());
    }

    /**
     * 新增规则时目标角色不存在应拒绝创建。
     */
    @Test
    void create_shouldReject_whenRoleNotFound() {
        when(roleMapper.selectById(99L)).thenReturn(null);
        UserRoleOrgScopeCondition orgScope = new UserRoleOrgScopeCondition();
        orgScope.setOrgId(10L);
        orgScope.setIncludeChildren(false);
        UserRoleRuleCreateRequest request = new UserRoleRuleCreateRequest();
        request.setRoleId(99L);
        request.setName("测试规则");
        request.setOrgScopes(List.of(orgScope));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class);
        verify(userRoleRuleMapper, never()).insert(any(UserRoleRuleEntity.class));
    }

    /**
     * 编辑规则时应整体替换条件子表（先删后插），并同步按新条件重新执行一次。
     */
    @Test
    void update_shouldReplaceConditions_andExecuteImmediately() {
        UserRoleRuleEntity existing = UserRoleRuleEntity.builder().id(100L).roleId(1L).name("旧名称").build();
        when(userRoleRuleMapper.selectById(100L)).thenReturn(existing);

        UserRoleUserAttrCondition attrCondition = new UserRoleUserAttrCondition();
        attrCondition.setMetadataFieldId(2L);
        attrCondition.setOperator("EQ");
        attrCondition.setAttrValue("male");
        UserRoleRuleUpdateRequest request = new UserRoleRuleUpdateRequest();
        request.setName("新名称");
        request.setUserAttrs(List.of(attrCondition));

        UserRoleRuleVO result = service.update(100L, request);

        assertThat(result).isNotNull();
        assertThat(existing.getName()).isEqualTo("新名称");
        verify(userRoleRuleOrgScopeMapper).delete(any());
        verify(userRoleRuleUserAttrMapper).delete(any());
        verify(userRoleRuleUserAttrMapper).insert(any(UserRoleRuleUserAttrEntity.class));
        verify(executionService).execute(100L, "9");
        verify(userRoleRuleMapper).updateById(existing);
    }

    /**
     * 编辑规则时目标规则不存在应拒绝。
     */
    @Test
    void update_shouldReject_whenRuleNotFound() {
        when(userRoleRuleMapper.selectById(999L)).thenReturn(null);
        UserRoleRuleUpdateRequest request = new UserRoleRuleUpdateRequest();
        request.setName("新名称");

        assertThatThrownBy(() -> service.update(999L, request)).isInstanceOf(BusinessException.class);
    }

    /**
     * 编辑规则时两类条件均未配置应拒绝编辑，且不触碰既有条件子表或重新执行。
     */
    @Test
    void update_shouldReject_whenBothConditionsEmpty() {
        when(userRoleRuleMapper.selectById(100L))
                .thenReturn(UserRoleRuleEntity.builder().id(100L).roleId(1L).build());
        UserRoleRuleUpdateRequest request = new UserRoleRuleUpdateRequest();
        request.setName("新名称");

        assertThatThrownBy(() -> service.update(100L, request)).isInstanceOf(BusinessException.class);
        verify(userRoleRuleOrgScopeMapper, never()).delete(any());
        verify(executionService, never()).execute(any(), any());
    }

    /**
     * 删除规则时应先调用执行引擎的 {@code revokeAll} 收回其已产生的全部角色关联，再物理
     * 删除规则本身及其条件子表记录。
     */
    @Test
    void delete_shouldRevokeThenPhysicallyDeleteRuleAndConditions() {
        when(userRoleRuleMapper.selectById(100L))
                .thenReturn(UserRoleRuleEntity.builder().id(100L).roleId(1L).build());

        service.delete(100L);

        verify(executionService).revokeAll(100L, "9");
        verify(userRoleRuleOrgScopeMapper).delete(any());
        verify(userRoleRuleUserAttrMapper).delete(any());
        verify(userRoleRuleMapper).deleteById(100L);
    }

    /**
     * 删除不存在的规则应拒绝，不产生任何收回/删除动作。
     */
    @Test
    void delete_shouldReject_whenRuleNotFound() {
        when(userRoleRuleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L)).isInstanceOf(BusinessException.class);
        verify(executionService, never()).revokeAll(any(), any());
        verify(userRoleRuleMapper, never()).deleteById(any(Long.class));
    }

    /**
     * 列表接口应只返回摘要字段（含当前命中人数），不携带组织范围/用户属性条件明细，避免
     * N+1（风格对齐 {@code AdminService} 分页列表不填充关联子集合的既有约定）。
     */
    @Test
    void listByRoleId_shouldReturnSummary_withoutConditionDetails() {
        when(userRoleRuleMapper.selectList(any())).thenReturn(List.of(
                UserRoleRuleEntity.builder().id(100L).roleId(1L).name("规则一").build()));
        when(userRoleRuleGrantMapper.selectCount(any())).thenReturn(3L);

        List<UserRoleRuleVO> result = service.listByRoleId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHitCount()).isEqualTo(3);
        assertThat(result.get(0).getOrgScopes()).isEmpty();
        assertThat(result.get(0).getUserAttrs()).isEmpty();
        verify(userRoleRuleOrgScopeMapper, never()).selectByRuleId(any());
        verify(userRoleRuleUserAttrMapper, never()).selectByRuleId(any());
    }

    /**
     * 详情接口应返回完整的组织范围/用户属性条件明细，供编辑表单回填。
     */
    @Test
    void getById_shouldReturnFullDetail_withConditions() {
        when(userRoleRuleMapper.selectById(100L))
                .thenReturn(UserRoleRuleEntity.builder().id(100L).roleId(1L).name("规则一").build());
        when(userRoleRuleOrgScopeMapper.selectByRuleId(100L)).thenReturn(List.of(
                UserRoleRuleOrgScopeVO.builder().orgId(10L).orgName("研发部").includeChildren(true).build()));
        when(userRoleRuleUserAttrMapper.selectByRuleId(100L)).thenReturn(List.of(
                UserRoleRuleUserAttrRow.builder().metadataFieldId(2L).fieldName("性别").fieldCode("gender")
                        .bizType("USER").operator("EQ").attrValue("male").build()));
        when(userRoleRuleGrantMapper.selectCount(any())).thenReturn(5L);

        UserRoleRuleVO result = service.getById(100L);

        assertThat(result.getHitCount()).isEqualTo(5);
        assertThat(result.getOrgScopes()).hasSize(1);
        assertThat(result.getOrgScopes().get(0).getOrgName()).isEqualTo("研发部");
        assertThat(result.getUserAttrs()).hasSize(1);
        assertThat(result.getUserAttrs().get(0).getValues()).containsExactly("male");
    }
}
