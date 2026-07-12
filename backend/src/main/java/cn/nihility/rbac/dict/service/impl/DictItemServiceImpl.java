package cn.nihility.rbac.dict.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.constant.DictStatus;
import cn.nihility.rbac.dict.dto.DictItemCreateRequest;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.dto.DictItemUpdateRequest;
import cn.nihility.rbac.dict.dto.DictItemVO;
import cn.nihility.rbac.dict.entity.DictItemEntity;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.dict.mapstruct.DictConvert;
import cn.nihility.rbac.dict.service.DictItemService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 字典项业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 字典项数据访问接口。 */
    private final DictItemMapper dictItemMapper;

    /** 字典类型数据访问接口，用于回填字典类型名称及按编码解析字典类型。 */
    private final DictTypeMapper dictTypeMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<DictItemVO> getPage(Long dictTypeId, Integer page, Integer pageSize) {
        LambdaQueryWrapper<DictItemEntity> wrapper = new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDictTypeId, dictTypeId)
                .ne(DictItemEntity::getStatus, DictStatus.DELETED)
                .orderByDesc(DictItemEntity::getShowOrder)
                .orderByAsc(DictItemEntity::getId);

        Page<DictItemEntity> queryPage = new Page<>(page, pageSize);
        Page<DictItemEntity> resultPage = dictItemMapper.selectPage(queryPage, wrapper);
        List<DictItemVO> records = toVOListWithDictTypeName(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictItemVO getById(Long id) {
        DictItemEntity entity = getExistingEntity(id);
        return toVOListWithDictTypeName(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictItemVO create(DictItemCreateRequest request) {
        checkCodeUnique(request.getDictTypeId(), request.getCode(), null);

        DictItemEntity entity = DictConvert.INSTANCE.toItemEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(DictStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        dictItemMapper.insert(entity);

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictItemVO update(Long id, DictItemUpdateRequest request) {
        DictItemEntity entity = getExistingEntity(id);
        checkCodeUnique(entity.getDictTypeId(), request.getCode(), id);

        DictConvert.INSTANCE.updateItemEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        dictItemMapper.updateById(entity);

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictItemVO enable(Long id) {
        return changeStatus(id, DictStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DictItemVO disable(Long id) {
        return changeStatus(id, DictStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        DictItemEntity entity = getExistingEntity(id);
        entity.setStatus(DictStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        dictItemMapper.updateById(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DictItemOptionVO> getEnabledOptions(String typeCode) {
        DictTypeEntity dictType = dictTypeMapper.selectOne(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getCode, typeCode)
                .eq(DictTypeEntity::getStatus, DictStatus.ENABLED));
        if (dictType == null) {
            return List.of();
        }

        List<DictItemEntity> entities = dictItemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDictTypeId, dictType.getId())
                .eq(DictItemEntity::getStatus, DictStatus.ENABLED)
                .orderByDesc(DictItemEntity::getShowOrder)
                .orderByAsc(DictItemEntity::getId));
        return DictConvert.INSTANCE.toOptionVOList(entities);
    }

    /**
     * 变更字典项状态（启用/停用）并返回更新后的详情。
     *
     * @param id     字典项 id
     * @param status 目标状态
     * @return 更新后的字典项详情
     */
    private DictItemVO changeStatus(Long id, int status) {
        DictItemEntity entity = getExistingEntity(id);
        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        dictItemMapper.updateById(entity);
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的字典项，不存在时抛出业务异常。
     *
     * @param id 字典项 id
     * @return 字典项实体
     */
    private DictItemEntity getExistingEntity(Long id) {
        DictItemEntity entity = dictItemMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), DictStatus.DELETED)) {
            throw new BusinessException("字典项不存在");
        }
        return entity;
    }

    /**
     * 校验字典项编码在同一字典类型下未删除的字典项范围内是否唯一。
     *
     * @param dictTypeId 所属字典类型 id
     * @param code       待校验的字典项编码
     * @param excludeId  更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(Long dictTypeId, String code, Long excludeId) {
        LambdaQueryWrapper<DictItemEntity> wrapper = new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDictTypeId, dictTypeId)
                .eq(DictItemEntity::getCode, code)
                .ne(DictItemEntity::getStatus, DictStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(DictItemEntity::getId, excludeId);
        }
        Long count = dictItemMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("字典项编码[" + code + "]在该字典类型下已存在");
        }
    }

    /**
     * 把字典项实体列表转换为详情视图对象列表，并批量解析所属字典类型名称。
     *
     * @param entities 字典项实体列表
     * @return 详情视图对象列表
     */
    private List<DictItemVO> toVOListWithDictTypeName(List<DictItemEntity> entities) {
        List<DictItemVO> result = DictConvert.INSTANCE.toItemVOList(entities);
        if (result.isEmpty()) {
            return result;
        }

        List<Long> dictTypeIds = entities.stream().map(DictItemEntity::getDictTypeId).distinct().toList();
        List<DictTypeEntity> dictTypes = dictTypeMapper.selectList(new LambdaQueryWrapper<DictTypeEntity>()
                .in(DictTypeEntity::getId, dictTypeIds));
        Map<Long, String> dictTypeNameMap = dictTypes.stream()
                .collect(Collectors.toMap(DictTypeEntity::getId, DictTypeEntity::getName, (left, right) -> left));

        for (DictItemVO vo : result) {
            vo.setDictTypeName(dictTypeNameMap.get(vo.getDictTypeId()));
        }
        return result;
    }
}
