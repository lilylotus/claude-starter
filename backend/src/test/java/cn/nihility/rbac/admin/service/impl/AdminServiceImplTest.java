package cn.nihility.rbac.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.dto.AdminCreateRequest;
import cn.nihility.rbac.admin.dto.AdminOrgScopeRequest;
import cn.nihility.rbac.admin.dto.AdminUpdateRequest;
import cn.nihility.rbac.admin.dto.AdminVO;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.entity.AdminOrgScopeEntity;
import cn.nihility.rbac.admin.entity.AdminRoleEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.admin.mapper.AdminOrgScopeMapper;
import cn.nihility.rbac.admin.mapper.AdminRoleMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AdminServiceImpl} 的单元测试，重点覆盖编码/关联用户唯一性校验范围、
 * 创建/更新时角色关联与组织管辖范围"先删后插"的整体同步行为、启停用不影响关联数据、
 * 逻辑删除、详情回填等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    /** 被测服务的管理员数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AdminMapper adminMapper;

    /** 被测服务的管理员角色关联数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AdminRoleMapper adminRoleMapper;

    /** 被测服务的管理员组织管辖范围数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AdminOrgScopeMapper adminOrgScopeMapper;

    /** 被测服务实例。 */
    private AdminServiceImpl adminService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code AdminConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(adminMapper, adminRoleMapper, adminOrgScopeMapper);
        lenient().when(adminRoleMapper.selectRolesByAdminId(anyLong())).thenReturn(List.of());
        lenient().when(adminOrgScopeMapper.selectOrgScopesByAdminId(anyLong())).thenReturn(List.of());
    }

    /**
     * 分页查询时，应返回携带总条数、页码、每页条数的分页结果。
     */
    @Test
    void getPage_shouldReturnPageResult() {
        AdminVO vo = buildVO(10L, AdminStatus.ENABLED);
        Page<AdminVO> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(vo));
        when(adminMapper.selectAdminPage(any(Page.class), any(Integer.class))).thenReturn(resultPage);

        var pageResult = adminService.getPage(1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(10);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(10L);
    }

    /**
     * 创建管理员时，若编码在未删除的管理员中已存在，应拒绝创建且不执行插入。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        AdminCreateRequest request = buildCreateRequest("测试管理员", "admin001", 100L);

        assertThatThrownBy(() -> adminService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin001");
        verify(adminMapper, never()).insert(any(AdminEntity.class));
    }

    /**
     * 创建管理员时，若关联用户 id 已被其他未删除管理员关联，应拒绝创建且不执行插入。
     */
    @Test
    void create_shouldThrowBusinessException_whenUserIdAlreadyLinked() {
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L).thenReturn(1L);

        AdminCreateRequest request = buildCreateRequest("测试管理员", "admin001", 100L);

        assertThatThrownBy(() -> adminService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100");
        verify(adminMapper, never()).insert(any(AdminEntity.class));
    }

    /**
     * 创建管理员时，应显式将状态置为启用，并按请求携带的角色 id 列表整批插入角色关联。
     */
    @Test
    void create_shouldSetEnabledStatus_andInsertRoles() {
        AdminVO detail = buildVO(10L, AdminStatus.ENABLED);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.selectAdminDetail(10L, AdminStatus.DELETED)).thenReturn(detail);
        doAnswer(invocation -> {
            AdminEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        }).when(adminMapper).insert(any(AdminEntity.class));

        AdminCreateRequest request = buildCreateRequest("测试管理员", "admin001", 100L);
        request.setRoleIds(List.of(1L, 2L));

        adminService.create(request);

        ArgumentCaptor<AdminEntity> captor = ArgumentCaptor.forClass(AdminEntity.class);
        verify(adminMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AdminStatus.ENABLED);

        // 创建场景下角色关联也走统一的"先删后插"整体同步逻辑，删除既有关联行是无操作的
        // 空删除（新建管理员尚无既有关联行），这里只关注最终整批插入的角色 id 是否与请求一致。
        verify(adminRoleMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<AdminRoleEntity> roleCaptor = ArgumentCaptor.forClass(AdminRoleEntity.class);
        verify(adminRoleMapper, times(2)).insert(roleCaptor.capture());
        assertThat(roleCaptor.getAllValues()).extracting(AdminRoleEntity::getRoleId).containsExactly(1L, 2L);
    }

    /**
     * 更新管理员时，应先物理删除既有角色关联与组织管辖范围，再按本次请求列表整批插入，
     * 且不修改状态字段。
     */
    @Test
    void update_shouldReplaceRolesAndOrgScopes_andNotChangeStatus() {
        AdminEntity entity = buildEntity(10L, AdminStatus.ENABLED);
        AdminVO detail = buildVO(10L, AdminStatus.ENABLED);
        when(adminMapper.selectById(10L)).thenReturn(entity);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.selectAdminDetail(10L, AdminStatus.DELETED)).thenReturn(detail);

        AdminOrgScopeRequest scopeRequest = new AdminOrgScopeRequest();
        scopeRequest.setOrgId(200L);
        scopeRequest.setIncludeChildren(true);

        AdminUpdateRequest request = buildUpdateRequest("新名称", "admin002", 101L);
        request.setRoleIds(List.of(3L));
        request.setOrgScopes(List.of(scopeRequest));

        adminService.update(10L, request);

        assertThat(entity.getStatus()).isEqualTo(AdminStatus.ENABLED);
        assertThat(entity.getName()).isEqualTo("新名称");
        verify(adminMapper).updateById(entity);

        verify(adminRoleMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<AdminRoleEntity> roleCaptor = ArgumentCaptor.forClass(AdminRoleEntity.class);
        verify(adminRoleMapper).insert(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRoleId()).isEqualTo(3L);

        verify(adminOrgScopeMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<AdminOrgScopeEntity> scopeCaptor = ArgumentCaptor.forClass(AdminOrgScopeEntity.class);
        verify(adminOrgScopeMapper).insert(scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().getOrgId()).isEqualTo(200L);
        assertThat(scopeCaptor.getValue().getIncludeChildren()).isTrue();
    }

    /**
     * 更新管理员时，若角色/组织管辖范围列表为空，仍应先删除既有关联，但不再插入新记录。
     */
    @Test
    void update_shouldOnlyDelete_whenRolesAndOrgScopesEmpty() {
        AdminEntity entity = buildEntity(10L, AdminStatus.ENABLED);
        AdminVO detail = buildVO(10L, AdminStatus.ENABLED);
        when(adminMapper.selectById(10L)).thenReturn(entity);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.selectAdminDetail(10L, AdminStatus.DELETED)).thenReturn(detail);

        AdminUpdateRequest request = buildUpdateRequest("新名称", "admin002", 101L);
        request.setRoleIds(List.of());
        request.setOrgScopes(List.of());

        adminService.update(10L, request);

        verify(adminRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(adminRoleMapper, never()).insert(any(AdminRoleEntity.class));
        verify(adminOrgScopeMapper).delete(any(LambdaQueryWrapper.class));
        verify(adminOrgScopeMapper, never()).insert(any(AdminOrgScopeEntity.class));
    }

    /**
     * 更新管理员时，若编码与另一个未删除管理员重复（非自身），应拒绝更新。
     */
    @Test
    void update_shouldThrowBusinessException_whenCodeConflictsWithAnotherAdmin() {
        AdminEntity entity = buildEntity(10L, AdminStatus.ENABLED);
        when(adminMapper.selectById(10L)).thenReturn(entity);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        AdminUpdateRequest request = buildUpdateRequest("新名称", "admin002", 101L);

        assertThatThrownBy(() -> adminService.update(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin002");
        verify(adminMapper, never()).updateById(any(AdminEntity.class));
    }

    /**
     * 更新管理员时，若编码与自身当前编码相同，不应被视为冲突（唯一性校验排除自身）。
     */
    @Test
    void update_shouldNotConflict_whenCodeEqualsSelf() {
        AdminEntity entity = buildEntity(10L, AdminStatus.ENABLED);
        AdminVO detail = buildVO(10L, AdminStatus.ENABLED);
        when(adminMapper.selectById(10L)).thenReturn(entity);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.selectAdminDetail(10L, AdminStatus.DELETED)).thenReturn(detail);

        AdminUpdateRequest request = buildUpdateRequest("新名称", "admin000", 100L);

        adminService.update(10L, request);

        verify(adminMapper).updateById(entity);
    }

    /**
     * 启用管理员时，应将状态置为启用，不触碰角色关联与组织管辖范围的写操作。
     */
    @Test
    void enable_shouldSetEnabledStatus_andNotTouchRelations() {
        AdminEntity entity = buildEntity(10L, AdminStatus.DISABLED);
        AdminVO detail = buildVO(10L, AdminStatus.ENABLED);
        when(adminMapper.selectById(10L)).thenReturn(entity);
        when(adminMapper.selectAdminDetail(10L, AdminStatus.DELETED)).thenReturn(detail);

        adminService.enable(10L);

        assertThat(entity.getStatus()).isEqualTo(AdminStatus.ENABLED);
        verify(adminMapper).updateById(entity);
        verify(adminRoleMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(adminOrgScopeMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    /**
     * 停用管理员时，应将状态置为停用。
     */
    @Test
    void disable_shouldSetDisabledStatus() {
        AdminEntity entity = buildEntity(10L, AdminStatus.ENABLED);
        AdminVO detail = buildVO(10L, AdminStatus.DISABLED);
        when(adminMapper.selectById(10L)).thenReturn(entity);
        when(adminMapper.selectAdminDetail(10L, AdminStatus.DELETED)).thenReturn(detail);

        adminService.disable(10L);

        assertThat(entity.getStatus()).isEqualTo(AdminStatus.DISABLED);
        verify(adminMapper).updateById(entity);
    }

    /**
     * 删除管理员时，应做逻辑删除（状态置为已删除），不做物理删除，也不清理关联数据。
     */
    @Test
    void delete_shouldSetDeletedStatus_andNotTouchRelations() {
        AdminEntity entity = buildEntity(10L, AdminStatus.ENABLED);
        when(adminMapper.selectById(10L)).thenReturn(entity);

        adminService.delete(10L);

        assertThat(entity.getStatus()).isEqualTo(AdminStatus.DELETED);
        verify(adminMapper).updateById(entity);
        verify(adminMapper, never()).deleteById(any(Long.class));
        verify(adminRoleMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(adminOrgScopeMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    /**
     * 查询一个不存在的管理员时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenAdminNotFound() {
        when(adminMapper.selectAdminDetail(99L, AdminStatus.DELETED)).thenReturn(null);

        assertThatThrownBy(() -> adminService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 查询管理员详情时，应回填其全部角色与全部管辖组织范围。
     */
    @Test
    void getById_shouldBackfillRolesAndOrgScopes() {
        AdminVO detail = buildVO(10L, AdminStatus.ENABLED);
        when(adminMapper.selectAdminDetail(10L, AdminStatus.DELETED)).thenReturn(detail);
        when(adminRoleMapper.selectRolesByAdminId(10L)).thenReturn(List.of(
                cn.nihility.rbac.admin.dto.AdminRoleVO.builder().roleId(1L).roleName("角色一").build()));
        when(adminOrgScopeMapper.selectOrgScopesByAdminId(10L)).thenReturn(List.of(
                cn.nihility.rbac.admin.dto.AdminOrgScopeVO.builder().orgId(200L).orgName("组织一")
                        .includeChildren(true).build()));

        AdminVO result = adminService.getById(10L);

        assertThat(result.getRoles()).hasSize(1);
        assertThat(result.getRoles().get(0).getRoleName()).isEqualTo("角色一");
        assertThat(result.getOrgScopes()).hasSize(1);
        assertThat(result.getOrgScopes().get(0).getOrgName()).isEqualTo("组织一");
    }

    /**
     * 构造创建管理员请求。
     *
     * @param name   管理员名称
     * @param code   管理员编码
     * @param userId 关联用户 id
     * @return 创建请求
     */
    private AdminCreateRequest buildCreateRequest(String name, String code, Long userId) {
        AdminCreateRequest request = new AdminCreateRequest();
        request.setName(name);
        request.setCode(code);
        request.setUserId(userId);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造更新管理员请求。
     *
     * @param name   管理员名称
     * @param code   管理员编码
     * @param userId 关联用户 id
     * @return 更新请求
     */
    private AdminUpdateRequest buildUpdateRequest(String name, String code, Long userId) {
        AdminUpdateRequest request = new AdminUpdateRequest();
        request.setName(name);
        request.setCode(code);
        request.setUserId(userId);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造一个测试用的管理员实体。
     *
     * @param id     主键 id
     * @param status 状态
     * @return 管理员实体
     */
    private AdminEntity buildEntity(long id, int status) {
        LocalDateTime now = LocalDateTime.now();
        return AdminEntity.builder()
                .id(id)
                .name("测试管理员")
                .code("admin000")
                .userId(100L)
                .showOrder(0)
                .status(status)
                .createBy("admin")
                .createTime(now)
                .updateBy("admin")
                .updateTime(now)
                .build();
    }

    /**
     * 构造一个测试用的管理员详情视图对象。
     *
     * @param id     主键 id
     * @param status 状态
     * @return 管理员详情视图对象
     */
    private AdminVO buildVO(long id, int status) {
        return AdminVO.builder()
                .id(id)
                .name("测试管理员")
                .code("admin000")
                .userId(100L)
                .showOrder(0)
                .status(status)
                .build();
    }
}
