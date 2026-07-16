package cn.nihility.rbac.permission.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.permission.constant.PermissionStatus;
import cn.nihility.rbac.permission.dto.PermissionCreateRequest;
import cn.nihility.rbac.permission.dto.PermissionUpdateRequest;
import cn.nihility.rbac.permission.dto.PermissionVO;
import cn.nihility.rbac.permission.entity.PermissionEntity;
import cn.nihility.rbac.permission.mapper.PermissionMapper;
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
 * {@link PermissionServiceImpl} 的单元测试，重点覆盖分页查询、新增默认启用、编码唯一性校验、
 * 更新不改状态、启停用、逻辑删除等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    /** 被测服务的权限点数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PermissionMapper permissionMapper;

    /** 被测服务实例。 */
    private PermissionServiceImpl permissionService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code PermissionConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        permissionService = new PermissionServiceImpl(permissionMapper);
    }

    /**
     * 分页查询时，应返回携带总条数、页码、每页条数的分页结果。
     */
    @Test
    void getPage_shouldReturnPageResult() {
        PermissionEntity entity = buildEntity(10L, PermissionStatus.ENABLED);
        Page<PermissionEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(entity));
        when(permissionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        PageResult<PermissionVO> pageResult = permissionService.getPage(1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(10);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(10L);
    }

    /**
     * 创建权限点时，应显式将状态置为启用，并写入创建/更新审计信息。
     */
    @Test
    void create_shouldSetEnabledStatus_andAuditFields() {
        PermissionEntity inserted = buildEntity(10L, PermissionStatus.ENABLED);
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(permissionMapper.selectById(any())).thenReturn(inserted);

        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setName("测试权限");
        request.setCode("permission001");
        request.setShowOrder(0);

        permissionService.create(request);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionMapper).insert(captor.capture());
        PermissionEntity captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        assertThat(captured.getCode()).isEqualTo("permission001");
        assertThat(captured.getCreateBy()).isNotNull();
        assertThat(captured.getCreateTime()).isNotNull();
    }

    /**
     * 创建权限点时，若权限编码已被其他未删除权限点占用，应拒绝创建。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        PermissionCreateRequest request = new PermissionCreateRequest();
        request.setName("测试权限");
        request.setCode("permission001");
        request.setShowOrder(0);

        assertThatThrownBy(() -> permissionService.create(request))
                .isInstanceOf(BusinessException.class);
        verify(permissionMapper, never()).insert(any(PermissionEntity.class));
    }

    /**
     * 更新权限点时，不应修改状态字段，仅更新允许修改的字段。
     */
    @Test
    void update_shouldNotChangeStatus() {
        PermissionEntity entity = buildEntity(10L, PermissionStatus.ENABLED);
        entity.setName("旧名称");
        when(permissionMapper.selectById(10L)).thenReturn(entity);
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("新名称");
        request.setCode("permission002");
        request.setShowOrder(1);

        permissionService.update(10L, request);

        assertThat(entity.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        assertThat(entity.getName()).isEqualTo("新名称");
        assertThat(entity.getCode()).isEqualTo("permission002");
        verify(permissionMapper).updateById(entity);
    }

    /**
     * 更新权限点时，若权限编码已被其他未删除权限点占用，应拒绝更新。
     */
    @Test
    void update_shouldThrowBusinessException_whenCodeUsedByAnotherPermission() {
        PermissionEntity entity = buildEntity(10L, PermissionStatus.ENABLED);
        when(permissionMapper.selectById(10L)).thenReturn(entity);
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        PermissionUpdateRequest request = new PermissionUpdateRequest();
        request.setName("新名称");
        request.setCode("permission002");
        request.setShowOrder(1);

        assertThatThrownBy(() -> permissionService.update(10L, request))
                .isInstanceOf(BusinessException.class);
        verify(permissionMapper, never()).updateById(any(PermissionEntity.class));
    }

    /**
     * 启用权限点时，应将状态置为启用。
     */
    @Test
    void enable_shouldSetEnabledStatus() {
        PermissionEntity entity = buildEntity(10L, PermissionStatus.DISABLED);
        when(permissionMapper.selectById(10L)).thenReturn(entity);

        permissionService.enable(10L);

        assertThat(entity.getStatus()).isEqualTo(PermissionStatus.ENABLED);
        verify(permissionMapper).updateById(entity);
    }

    /**
     * 停用权限点时，应将状态置为停用。
     */
    @Test
    void disable_shouldSetDisabledStatus() {
        PermissionEntity entity = buildEntity(10L, PermissionStatus.ENABLED);
        when(permissionMapper.selectById(10L)).thenReturn(entity);

        permissionService.disable(10L);

        assertThat(entity.getStatus()).isEqualTo(PermissionStatus.DISABLED);
        verify(permissionMapper).updateById(entity);
    }

    /**
     * 删除权限点时，应做逻辑删除（状态置为已删除），不做物理删除。
     */
    @Test
    void delete_shouldSetDeletedStatus() {
        PermissionEntity entity = buildEntity(10L, PermissionStatus.ENABLED);
        when(permissionMapper.selectById(10L)).thenReturn(entity);

        permissionService.delete(10L);

        assertThat(entity.getStatus()).isEqualTo(PermissionStatus.DELETED);
        verify(permissionMapper).updateById(entity);
        verify(permissionMapper, never()).deleteById(any(Long.class));
    }

    /**
     * 查询一个不存在的权限点时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenPermissionNotFound() {
        when(permissionMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> permissionService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 查询一个已被逻辑删除的权限点时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenPermissionAlreadyDeleted() {
        PermissionEntity entity = buildEntity(10L, PermissionStatus.DELETED);
        when(permissionMapper.selectById(10L)).thenReturn(entity);

        assertThatThrownBy(() -> permissionService.getById(10L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的权限点实体。
     *
     * @param id     主键 id
     * @param status 状态
     * @return 权限点实体
     */
    private PermissionEntity buildEntity(long id, int status) {
        LocalDateTime now = LocalDateTime.now();
        return PermissionEntity.builder()
                .id(id)
                .name("测试权限")
                .code("permission000")
                .showOrder(0)
                .status(status)
                .createBy("admin")
                .createTime(now)
                .updateBy("admin")
                .updateTime(now)
                .build();
    }
}
