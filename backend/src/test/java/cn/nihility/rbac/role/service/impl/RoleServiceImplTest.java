package cn.nihility.rbac.role.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.dto.RoleCreateRequest;
import cn.nihility.rbac.role.dto.RoleUpdateRequest;
import cn.nihility.rbac.role.dto.RoleVO;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.entity.RolePermissionEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.role.mapper.RolePermissionMapper;
import cn.nihility.rbac.sync.event.DomainEventPublisher;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RoleServiceImpl} 的单元测试，重点覆盖分页查询、新增默认启用、编码唯一性校验、
 * 更新不改状态、启停用、逻辑删除、操作日志记录调用等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    /** 被测服务的角色数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private RoleMapper roleMapper;

    /** 被测服务的角色权限点关联数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private RolePermissionMapper rolePermissionMapper;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务的审计字段展示名批量解析依赖，使用 Mockito 打桩。 */
    @Mock
    private UserDisplayService userDisplayService;

    /** 被测服务的数据变更事件发布依赖，使用 Mockito 打桩。 */
    @Mock
    private DomainEventPublisher domainEventPublisher;

    /** 被测服务实例。 */
    private RoleServiceImpl roleService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code RoleConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleMapper, rolePermissionMapper, operationLogRecorder,
                currentOperatorService, userDisplayService, domainEventPublisher);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
    }

    /**
     * 分页查询时，应返回携带总条数、页码、每页条数的分页结果。
     */
    @Test
    void getPage_shouldReturnPageResult() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        Page<RoleEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(entity));
        when(roleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        PageResult<RoleVO> pageResult = roleService.getPage(1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(10);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(10L);
    }

    /**
     * 创建角色时，应显式将状态置为启用，并写入创建/更新审计信息。
     */
    @Test
    void create_shouldSetEnabledStatus_andAuditFields() {
        RoleEntity inserted = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(roleMapper.selectById(any())).thenReturn(inserted);

        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("测试角色");
        request.setCode("role001");
        request.setShowOrder(0);

        roleService.create(request);

        ArgumentCaptor<RoleEntity> captor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleMapper).insert(captor.capture());
        RoleEntity captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(RoleStatus.ENABLED);
        assertThat(captured.getCode()).isEqualTo("role001");
        assertThat(captured.getCreateBy()).isNotNull();
        assertThat(captured.getCreateTime()).isNotNull();
        verify(operationLogRecorder).recordCreate(org.mockito.ArgumentMatchers.eq("role"), any(),
                org.mockito.ArgumentMatchers.eq("测试角色"), any(Map.class));
    }

    /**
     * 创建角色成功后，应紧邻 {@code operationLogRecorder.recordCreate} 之后发布一次角色
     * 新增的同步事件（app-sync-notify-pull-api change design.md Decision 5）。
     */
    @Test
    void create_shouldPublishDomainChangeEvent() {
        RoleEntity inserted = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(roleMapper.selectById(any())).thenReturn(inserted);
        doAnswer(invocation -> {
            RoleEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        }).when(roleMapper).insert(any(RoleEntity.class));

        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("测试角色");
        request.setCode("role001");
        request.setShowOrder(0);

        roleService.create(request);

        ArgumentCaptor<cn.nihility.rbac.sync.event.DomainChangeEvent> eventCaptor =
                ArgumentCaptor.forClass(cn.nihility.rbac.sync.event.DomainChangeEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        cn.nihility.rbac.sync.event.DomainChangeEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getDataType()).isEqualTo(cn.nihility.rbac.app.sync.constant.SyncDomain.ROLE);
        assertThat(publishedEvent.getBizId()).isEqualTo(10L);
        assertThat(publishedEvent.getOperationType())
                .isEqualTo(cn.nihility.rbac.operationlog.constant.OperationType.CREATE);
    }

    /**
     * 创建角色时同时携带非空的权限点 id 列表，应先清空该角色既有关联再按列表批量插入
     * （spec.md "创建角色时同时分配权限点" Scenario）。
     */
    @Test
    void create_shouldSyncPermissions_whenPermissionIdsProvided() {
        RoleEntity inserted = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(roleMapper.selectById(any())).thenReturn(inserted);
        doAnswer(invocation -> {
            RoleEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        }).when(roleMapper).insert(any(RoleEntity.class));

        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("测试角色");
        request.setCode("role001");
        request.setShowOrder(0);
        request.setPermissionIds(List.of(1L, 2L));

        roleService.create(request);

        verify(rolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<RolePermissionEntity> captor = ArgumentCaptor.forClass(RolePermissionEntity.class);
        verify(rolePermissionMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(RolePermissionEntity::getPermissionId)
                .containsExactly(1L, 2L);
        assertThat(captor.getAllValues()).allMatch(entity -> entity.getRoleId().equals(10L));
    }

    /**
     * 创建角色时不携带权限点 id 列表，不应建立任何权限点关联
     * （spec.md "创建角色时不携带权限点" Scenario）。
     */
    @Test
    void create_shouldNotInsertPermissions_whenPermissionIdsAbsent() {
        RoleEntity inserted = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(roleMapper.selectById(any())).thenReturn(inserted);

        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("测试角色");
        request.setCode("role001");
        request.setShowOrder(0);

        roleService.create(request);

        verify(rolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        verify(rolePermissionMapper, never()).insert(any(RolePermissionEntity.class));
    }

    /**
     * 创建角色时，若角色编码已被其他未删除角色占用，应拒绝创建。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        RoleCreateRequest request = new RoleCreateRequest();
        request.setName("测试角色");
        request.setCode("role001");
        request.setShowOrder(0);

        assertThatThrownBy(() -> roleService.create(request))
                .isInstanceOf(BusinessException.class);
        verify(roleMapper, never()).insert(any(RoleEntity.class));
    }

    /**
     * 更新角色时，不应修改状态字段，仅更新允许修改的字段。
     */
    @Test
    void update_shouldNotChangeStatus() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        entity.setName("旧名称");
        when(roleMapper.selectById(10L)).thenReturn(entity);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("新名称");
        request.setCode("role002");
        request.setShowOrder(1);

        roleService.update(10L, request);

        assertThat(entity.getStatus()).isEqualTo(RoleStatus.ENABLED);
        assertThat(entity.getName()).isEqualTo("新名称");
        assertThat(entity.getCode()).isEqualTo("role002");
        verify(roleMapper).updateById(entity);
    }

    /**
     * 更新角色时携带的权限点 id 列表与既有分配不同，应整体覆盖同步：先清空既有关联，
     * 再按新列表批量插入（spec.md "更新角色时整体覆盖权限点分配" Scenario）。
     */
    @Test
    void update_shouldSyncPermissions_whenPermissionIdsChanged() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("新名称");
        request.setCode("role002");
        request.setShowOrder(1);
        request.setPermissionIds(List.of(3L));

        roleService.update(10L, request);

        verify(rolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<RolePermissionEntity> captor = ArgumentCaptor.forClass(RolePermissionEntity.class);
        verify(rolePermissionMapper).insert(captor.capture());
        assertThat(captor.getValue().getPermissionId()).isEqualTo(3L);
        assertThat(captor.getValue().getRoleId()).isEqualTo(10L);
    }

    /**
     * 更新角色时 {@code permissionIds} 为空数组，应移除该角色此前全部的权限点关联，
     * 不再插入任何新关联（spec.md "更新角色时清空权限点分配" Scenario）。
     */
    @Test
    void update_shouldClearPermissions_whenPermissionIdsEmpty() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("新名称");
        request.setCode("role002");
        request.setShowOrder(1);
        request.setPermissionIds(List.of());

        roleService.update(10L, request);

        verify(rolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        verify(rolePermissionMapper, never()).insert(any(RolePermissionEntity.class));
    }

    /**
     * 更新角色时，若角色编码已被其他未删除角色占用，应拒绝更新。
     */
    @Test
    void update_shouldThrowBusinessException_whenCodeUsedByAnotherRole() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setName("新名称");
        request.setCode("role002");
        request.setShowOrder(1);

        assertThatThrownBy(() -> roleService.update(10L, request))
                .isInstanceOf(BusinessException.class);
        verify(roleMapper, never()).updateById(any(RoleEntity.class));
    }

    /**
     * 启用角色时，应将状态置为启用。
     */
    @Test
    void enable_shouldSetEnabledStatus() {
        RoleEntity entity = buildEntity(10L, RoleStatus.DISABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);

        roleService.enable(10L);

        assertThat(entity.getStatus()).isEqualTo(RoleStatus.ENABLED);
        verify(roleMapper).updateById(entity);
    }

    /**
     * 停用角色时，应将状态置为停用。
     */
    @Test
    void disable_shouldSetDisabledStatus() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);

        roleService.disable(10L);

        assertThat(entity.getStatus()).isEqualTo(RoleStatus.DISABLED);
        verify(roleMapper).updateById(entity);
    }

    /**
     * 删除角色时，应做逻辑删除（状态置为已删除），不做物理删除。
     */
    @Test
    void delete_shouldSetDeletedStatus() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);

        roleService.delete(10L);

        assertThat(entity.getStatus()).isEqualTo(RoleStatus.DELETED);
        verify(roleMapper).updateById(entity);
        verify(roleMapper, never()).deleteById(any(Long.class));
        verify(operationLogRecorder).recordDelete(org.mockito.ArgumentMatchers.eq("role"),
                org.mockito.ArgumentMatchers.eq(10L), any(), any(Map.class));
    }

    /**
     * 查询角色详情时，应返回该角色已分配的权限点列表（spec.md "角色详情返回已分配权限点"
     * Scenario）。
     */
    @Test
    void getById_shouldReturnAssignedPermissions() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);
        PermissionOptionVO permission = PermissionOptionVO.builder().id(1L).name("查看").code("mod:res:view").build();
        when(rolePermissionMapper.selectPermissionsByRoleId(10L)).thenReturn(List.of(permission));

        RoleVO vo = roleService.getById(10L);

        assertThat(vo.getPermissions()).hasSize(1);
        assertThat(vo.getPermissions().get(0).getCode()).isEqualTo("mod:res:view");
    }

    /**
     * 查询一个未分配任何权限点的角色详情时，权限点列表应为空数组
     * （spec.md "角色未分配任何权限点" Scenario）。
     */
    @Test
    void getById_shouldReturnEmptyPermissions_whenNoneAssigned() {
        RoleEntity entity = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectById(10L)).thenReturn(entity);
        when(rolePermissionMapper.selectPermissionsByRoleId(10L)).thenReturn(List.of());

        RoleVO vo = roleService.getById(10L);

        assertThat(vo.getPermissions()).isEmpty();
    }

    /**
     * 查询一个不存在的角色时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenRoleNotFound() {
        when(roleMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 查询一个已被逻辑删除的角色时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenRoleAlreadyDeleted() {
        RoleEntity entity = buildEntity(10L, RoleStatus.DELETED);
        when(roleMapper.selectById(10L)).thenReturn(entity);

        assertThatThrownBy(() -> roleService.getById(10L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 查询启用角色选项时，只应返回状态为启用的角色，不包含已停用或已删除的角色。
     */
    @Test
    void getEnabledOptions_shouldOnlyReturnEnabledRoles() {
        RoleEntity enabled = buildEntity(10L, RoleStatus.ENABLED);
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(enabled));

        List<cn.nihility.rbac.role.dto.RoleOptionVO> options = roleService.getEnabledOptions();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getId()).isEqualTo(10L);
        assertThat(options.get(0).getCode()).isEqualTo("role000");
    }

    /**
     * 构造一个测试用的角色实体。
     *
     * @param id     主键 id
     * @param status 状态
     * @return 角色实体
     */
    private RoleEntity buildEntity(long id, int status) {
        LocalDateTime now = LocalDateTime.now();
        return RoleEntity.builder()
                .id(id)
                .name("测试角色")
                .code("role000")
                .showOrder(0)
                .status(status)
                .createBy("admin")
                .createTime(now)
                .updateBy("admin")
                .updateTime(now)
                .build();
    }
}
