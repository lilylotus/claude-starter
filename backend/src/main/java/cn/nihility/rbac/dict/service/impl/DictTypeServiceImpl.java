package cn.nihility.rbac.dict.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.constant.DictStatus;
import cn.nihility.rbac.dict.dto.DictTypeCreateRequest;
import cn.nihility.rbac.dict.dto.DictTypeUpdateRequest;
import cn.nihility.rbac.dict.dto.DictTypeVO;
import cn.nihility.rbac.dict.entity.DictItemEntity;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.dict.mapstruct.DictConvert;
import cn.nihility.rbac.dict.service.DictTypeService;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 字典类型业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class DictTypeServiceImpl implements DictTypeService {

    /** 字典类型数据访问接口。 */
    private final DictTypeMapper dictTypeMapper;

    /** 字典项数据访问接口，用于删除前校验是否存在未删除的字典项。 */
    private final DictItemMapper dictItemMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 当前登录操作人账号编码解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<DictTypeVO> getPage(String keyword, Integer page, Integer pageSize) {
        LambdaQueryWrapper<DictTypeEntity> wrapper = new LambdaQueryWrapper<DictTypeEntity>()
                .ne(DictTypeEntity::getStatus, DictStatus.DELETED);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DictTypeEntity::getName, keyword)
                    .or()
                    .like(DictTypeEntity::getCode, keyword));
        }
        wrapper.orderByDesc(DictTypeEntity::getShowOrder).orderByAsc(DictTypeEntity::getId);

        Page<DictTypeEntity> queryPage = new Page<>(page, pageSize);
        Page<DictTypeEntity> resultPage = dictTypeMapper.selectPage(queryPage, wrapper);
        List<DictTypeVO> records = DictConvert.INSTANCE.toTypeVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictTypeVO getById(Long id) {
        DictTypeEntity entity = getExistingEntity(id);
        return DictConvert.INSTANCE.toTypeVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictTypeVO create(DictTypeCreateRequest request) {
        checkCodeUnique(request.getCode(), null);

        String operator = currentOperatorService.resolveCode();
        DictTypeEntity entity = DictConvert.INSTANCE.toTypeEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(DictStatus.ENABLED);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        dictTypeMapper.insert(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.DICT_TYPE, entity.getId(), entity.getName(),
                toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictTypeVO update(Long id, DictTypeUpdateRequest request) {
        DictTypeEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        DictConvert.INSTANCE.updateTypeEntity(request, entity);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        dictTypeMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.DICT_TYPE, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictTypeVO enable(Long id) {
        return changeStatus(id, DictStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictTypeVO disable(Long id) {
        return changeStatus(id, DictStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        DictTypeEntity entity = getExistingEntity(id);

        Long itemCount = dictItemMapper.selectCount(new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDictTypeId, id)
                .ne(DictItemEntity::getStatus, DictStatus.DELETED));
        if (itemCount != null && itemCount > 0) {
            throw new BusinessException("该字典类型下存在未删除的字典项，无法删除");
        }

        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);
        entity.setStatus(DictStatus.DELETED);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        dictTypeMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.DICT_TYPE, id, entity.getName(), beforeSnapshot);
    }

    /**
     * 变更字典类型状态（启用/停用）并返回更新后的详情。
     *
     * @param id     字典类型 id
     * @param status 目标状态
     * @return 更新后的字典类型详情
     */
    private DictTypeVO changeStatus(Long id, int status) {
        DictTypeEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(currentOperatorService.resolveCode());
        entity.setUpdateTime(LocalDateTime.now());
        dictTypeMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.DICT_TYPE, id, entity.getName(),
                status == DictStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的字典类型，不存在时抛出业务异常。
     *
     * @param id 字典类型 id
     * @return 字典类型实体
     */
    private DictTypeEntity getExistingEntity(Long id) {
        DictTypeEntity entity = dictTypeMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), DictStatus.DELETED)) {
            throw new BusinessException("字典类型不存在");
        }
        return entity;
    }

    /**
     * 校验字典类型编码在未删除的字典类型中是否全局唯一。
     *
     * @param code      待校验的字典类型编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<DictTypeEntity> wrapper = new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getCode, code)
                .ne(DictTypeEntity::getStatus, DictStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(DictTypeEntity::getId, excludeId);
        }
        Long count = dictTypeMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("字典类型编码[" + code + "]已存在");
        }
    }

    /**
     * 构造字典类型实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值。
     *
     * @param entity 字典类型实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(DictTypeEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("类型名称", entity.getName());
        snapshot.put("类型编码", entity.getCode());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));
        return snapshot;
    }

    /**
     * 把字典类型状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, DictStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, DictStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
