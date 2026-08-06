package cn.nihility.rbac.role.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.dto.RoleCreateRequest;
import cn.nihility.rbac.role.dto.RoleOptionVO;
import cn.nihility.rbac.role.dto.RoleUpdateRequest;
import cn.nihility.rbac.role.dto.RoleVO;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.entity.RolePermissionEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.role.mapper.RolePermissionMapper;
import cn.nihility.rbac.role.mapstruct.RoleConvert;
import cn.nihility.rbac.role.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 角色管理业务逻辑实现。角色权限点关联（{@code tab_role_permission}）在每次创建/更新时
 * 采用"先删后插"的整体同步策略，不做按行 diff，风格对齐 {@code AdminServiceImpl#syncRoles}。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    /** 角色数据访问接口。 */
    private final RoleMapper roleMapper;

    /** 角色权限点关联数据访问接口。 */
    private final RolePermissionMapper rolePermissionMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 当前登录操作人账号编码解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<RoleVO> getPage(Integer page, Integer pageSize) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<RoleEntity>()
                .ne(RoleEntity::getStatus, RoleStatus.DELETED)
                .orderByDesc(RoleEntity::getShowOrder)
                .orderByAsc(RoleEntity::getId);

        Page<RoleEntity> queryPage = new Page<>(page, pageSize);
        Page<RoleEntity> resultPage = roleMapper.selectPage(queryPage, wrapper);
        List<RoleVO> records = RoleConvert.INSTANCE.toVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleVO getById(Long id) {
        RoleEntity entity = getExistingEntity(id);
        RoleVO vo = RoleConvert.INSTANCE.toVO(entity);
        vo.setPermissions(rolePermissionMapper.selectPermissionsByRoleId(id));
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleVO create(RoleCreateRequest request) {
        checkCodeUnique(request.getCode(), null);

        String operator = currentOperatorService.resolveCode();
        RoleEntity entity = RoleConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(RoleStatus.ENABLED);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        roleMapper.insert(entity);

        syncPermissions(entity.getId(), request.getPermissionIds(), operator);

        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, entity.getId(), entity.getName(),
                toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleVO update(Long id, RoleUpdateRequest request) {
        RoleEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        String operator = currentOperatorService.resolveCode();
        RoleConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(entity);

        syncPermissions(id, request.getPermissionIds(), operator);

        operationLogRecorder.recordUpdate(OperationLogResourceType.ROLE, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleVO enable(Long id) {
        return changeStatus(id, RoleStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleVO disable(Long id) {
        return changeStatus(id, RoleStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        RoleEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(RoleStatus.DELETED);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.ROLE, id, entity.getName(), beforeSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoleOptionVO> getEnabledOptions() {
        List<RoleEntity> entities = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getStatus, RoleStatus.ENABLED)
                .orderByDesc(RoleEntity::getShowOrder)
                .orderByAsc(RoleEntity::getId));
        return RoleConvert.INSTANCE.toOptionVOList(entities);
    }

    /**
     * 变更角色状态（启用/停用）并返回更新后的详情。
     *
     * @param id     角色 id
     * @param status 目标状态
     * @return 更新后的角色详情
     */
    private RoleVO changeStatus(Long id, int status) {
        RoleEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.ROLE, id, entity.getName(),
                status == RoleStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的角色，不存在时抛出业务异常。
     *
     * @param id 角色 id
     * @return 角色实体
     */
    private RoleEntity getExistingEntity(Long id) {
        RoleEntity entity = roleMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), RoleStatus.DELETED)) {
            throw new BusinessException("角色不存在");
        }
        return entity;
    }

    /**
     * 校验角色编码在未删除的角色中是否唯一。
     *
     * @param code      待校验的角色编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCode, code)
                .ne(RoleEntity::getStatus, RoleStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(RoleEntity::getId, excludeId);
        }
        Long count = roleMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("角色编码[" + code + "]已存在");
        }
    }

    /**
     * 把请求中的权限点 id 列表与角色当前的权限点关联做整体同步：先物理删除该角色名下
     * 全部既有权限点关联行，再按请求列表整批插入，不做按行 diff，风格对齐
     * {@code AdminServiceImpl#syncRoles}。
     *
     * @param roleId        角色 id
     * @param permissionIds 请求中的权限点 id 列表，可为空（视为清空全部既有权限点关联）
     * @param operator      操作人账号编码，由调用方在方法内只解析一次后传入，避免重复解析
     */
    private void syncPermissions(Long roleId, List<Long> permissionIds, String operator) {
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, roleId));

        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long permissionId : permissionIds) {
            RolePermissionEntity entity = RolePermissionEntity.builder()
                    .roleId(roleId)
                    .permissionId(permissionId)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            rolePermissionMapper.insert(entity);
        }
    }

    /**
     * 构造角色实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值；
     * 已分配权限点按 {@code roleId} 查询当前关联行，汇总成一个可读字符串字段，不逐行 diff。
     *
     * @param entity 角色实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(RoleEntity entity) {
        List<PermissionOptionVO> permissions = rolePermissionMapper.selectPermissionsByRoleId(entity.getId());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("角色名称", entity.getName());
        snapshot.put("角色编码", entity.getCode());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));
        snapshot.put("已分配权限点", joinPermissionNames(permissions));
        return snapshot;
    }

    /**
     * 把已分配权限点列表汇总成一个以顿号分隔的可读字符串。
     *
     * @param permissions 已分配权限点列表
     * @return 汇总字符串，列表为空时返回 {@code null}
     */
    private String joinPermissionNames(List<PermissionOptionVO> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        return permissions.stream().map(PermissionOptionVO::getName).collect(Collectors.joining("、"));
    }

    /**
     * 把角色状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, RoleStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, RoleStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
