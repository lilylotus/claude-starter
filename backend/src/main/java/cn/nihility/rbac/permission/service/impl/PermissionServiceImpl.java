package cn.nihility.rbac.permission.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.permission.constant.PermissionStatus;
import cn.nihility.rbac.permission.dto.PermissionCreateRequest;
import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.permission.dto.PermissionUpdateRequest;
import cn.nihility.rbac.permission.dto.PermissionVO;
import cn.nihility.rbac.permission.entity.PermissionEntity;
import cn.nihility.rbac.permission.mapper.PermissionMapper;
import cn.nihility.rbac.permission.mapstruct.PermissionConvert;
import cn.nihility.rbac.permission.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 权限管理业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    /** 权限点数据访问接口。 */
    private final PermissionMapper permissionMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 当前登录操作人账号编码解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<PermissionVO> getPage(Integer page, Integer pageSize) {
        LambdaQueryWrapper<PermissionEntity> wrapper = new LambdaQueryWrapper<PermissionEntity>()
                .ne(PermissionEntity::getStatus, PermissionStatus.DELETED)
                .orderByDesc(PermissionEntity::getShowOrder)
                .orderByAsc(PermissionEntity::getId);

        Page<PermissionEntity> queryPage = new Page<>(page, pageSize);
        Page<PermissionEntity> resultPage = permissionMapper.selectPage(queryPage, wrapper);
        List<PermissionVO> records = PermissionConvert.INSTANCE.toVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO getById(Long id) {
        PermissionEntity entity = getExistingEntity(id);
        return PermissionConvert.INSTANCE.toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO create(PermissionCreateRequest request) {
        checkCodeUnique(request.getCode(), null);

        String operator = currentOperatorService.resolveCode();
        PermissionEntity entity = PermissionConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(PermissionStatus.ENABLED);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        permissionMapper.insert(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.PERMISSION, entity.getId(), entity.getName(),
                toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO update(Long id, PermissionUpdateRequest request) {
        PermissionEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        PermissionConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        permissionMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.PERMISSION, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO enable(Long id) {
        return changeStatus(id, PermissionStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO disable(Long id) {
        return changeStatus(id, PermissionStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        PermissionEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(PermissionStatus.DELETED);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        permissionMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.PERMISSION, id, entity.getName(), beforeSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PermissionOptionVO> getEnabledOptions() {
        List<PermissionEntity> entities = permissionMapper.selectList(new LambdaQueryWrapper<PermissionEntity>()
                .eq(PermissionEntity::getStatus, PermissionStatus.ENABLED)
                .orderByDesc(PermissionEntity::getShowOrder)
                .orderByAsc(PermissionEntity::getId));
        return PermissionConvert.INSTANCE.toOptionVOList(entities);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PermissionVO> getAllList() {
        List<PermissionEntity> entities = permissionMapper.selectList(new LambdaQueryWrapper<PermissionEntity>()
                .ne(PermissionEntity::getStatus, PermissionStatus.DELETED)
                .orderByAsc(PermissionEntity::getShowOrder)
                .orderByAsc(PermissionEntity::getId));
        return PermissionConvert.INSTANCE.toVOList(entities);
    }

    /**
     * 变更权限点状态（启用/停用）并返回更新后的详情。
     *
     * @param id     权限点 id
     * @param status 目标状态
     * @return 更新后的权限点详情
     */
    private PermissionVO changeStatus(Long id, int status) {
        PermissionEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        permissionMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.PERMISSION, id, entity.getName(),
                status == PermissionStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的权限点，不存在时抛出业务异常。
     *
     * @param id 权限点 id
     * @return 权限点实体
     */
    private PermissionEntity getExistingEntity(Long id) {
        PermissionEntity entity = permissionMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), PermissionStatus.DELETED)) {
            throw new BusinessException("权限点不存在");
        }
        return entity;
    }

    /**
     * 校验权限编码在未删除的权限点中是否唯一。
     *
     * @param code      待校验的权限编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<PermissionEntity> wrapper = new LambdaQueryWrapper<PermissionEntity>()
                .eq(PermissionEntity::getCode, code)
                .ne(PermissionEntity::getStatus, PermissionStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(PermissionEntity::getId, excludeId);
        }
        Long count = permissionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("权限编码[" + code + "]已存在");
        }
    }

    /**
     * 构造权限点实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值。
     *
     * @param entity 权限点实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(PermissionEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("权限点名称", entity.getName());
        snapshot.put("权限点编码", entity.getCode());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));
        return snapshot;
    }

    /**
     * 把权限点状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, PermissionStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, PermissionStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
