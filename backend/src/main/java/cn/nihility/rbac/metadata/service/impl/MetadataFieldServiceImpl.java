package cn.nihility.rbac.metadata.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.dto.MetadataFieldUpdateRequest;
import cn.nihility.rbac.metadata.dto.MetadataFieldVO;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.exception.MetadataFieldCodeDuplicateException;
import cn.nihility.rbac.metadata.exception.MetadataFieldInUseException;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.metadata.mapstruct.MetadataFieldConvert;
import cn.nihility.rbac.metadata.service.MetadataFieldService;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 元数据字段配置业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class MetadataFieldServiceImpl implements MetadataFieldService {

    /** 元数据字段数据访问接口。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 表单字段定义数据访问接口，仅用于判断元数据字段是否被有效定义占用。 */
    private final FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 审计字段（{@code createBy}/{@code updateBy}）展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<MetadataFieldVO> getPage(String bizType, Integer page, Integer pageSize) {
        LambdaQueryWrapper<MetadataFieldEntity> wrapper = new LambdaQueryWrapper<MetadataFieldEntity>()
                .eq(StringUtils.hasText(bizType), MetadataFieldEntity::getBizType, bizType)
                .orderByAsc(MetadataFieldEntity::getId);

        Page<MetadataFieldEntity> queryPage = new Page<>(page, pageSize);
        Page<MetadataFieldEntity> resultPage = metadataFieldMapper.selectPage(queryPage, wrapper);
        List<MetadataFieldVO> records = toVOListWithDisplayName(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataFieldVO getById(Long id) {
        return toVOListWithDisplayName(List.of(getExistingEntity(id))).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataFieldVO update(Long id, MetadataFieldUpdateRequest request) {
        MetadataFieldEntity entity = getExistingEntity(id);
        if (!Objects.equals(request.getFieldCode(), entity.getFieldCode())) {
            checkFieldCodeUnique(entity.getBizType(), request.getFieldCode(), id);
        }
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        MetadataFieldConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        metadataFieldMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.METADATA_FIELD, id, entity.getFieldName(),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataFieldVO enable(Long id) {
        return changeStatus(id, MetadataFieldStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataFieldVO disable(Long id) {
        MetadataFieldEntity entity = getExistingEntity(id);
        if (formFieldDefinitionMapper.existsActiveByMetadataFieldId(id)) {
            throw new MetadataFieldInUseException(
                    "元数据字段[" + entity.getFieldName() + "]已被表单字段定义绑定，无法停用");
        }
        return changeStatus(id, MetadataFieldStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MetadataFieldVO> listAvailable(String bizType) {
        return listAvailable(bizType, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MetadataFieldVO> listAvailable(String bizType, Long excludeDefinitionId) {
        List<MetadataFieldEntity> enabled = metadataFieldMapper.selectList(new LambdaQueryWrapper<MetadataFieldEntity>()
                .eq(MetadataFieldEntity::getBizType, bizType)
                .eq(MetadataFieldEntity::getStatus, MetadataFieldStatus.ENABLED)
                .orderByAsc(MetadataFieldEntity::getId));

        List<MetadataFieldEntity> available = new ArrayList<>(enabled.stream()
                .filter(entity -> !formFieldDefinitionMapper.existsActiveByMetadataFieldId(entity.getId()))
                .toList());

        MetadataFieldEntity currentlyBound = resolveCurrentlyBoundMetadataField(excludeDefinitionId);
        if (currentlyBound != null
                && available.stream().noneMatch(entity -> entity.getId().equals(currentlyBound.getId()))) {
            available.add(currentlyBound);
        }
        return toVOListWithDisplayName(available);
    }

    /**
     * 编辑场景下解析"当前表单字段定义已绑定"的元数据字段：若 {@code definitionId}
     * 对应一条存在且未被逻辑删除的表单字段定义，且其绑定的元数据字段状态为启用，
     * 返回该元数据字段；否则返回 {@code null}（不额外纳入可用列表）。
     *
     * @param definitionId 表单字段定义 id，可为 null
     * @return 当前绑定且启用的元数据字段，不存在/未启用/定义不存在或已删除时返回 null
     */
    private MetadataFieldEntity resolveCurrentlyBoundMetadataField(Long definitionId) {
        if (definitionId == null) {
            return null;
        }
        FormFieldDefinitionEntity definition = formFieldDefinitionMapper.selectById(definitionId);
        if (definition == null || Objects.equals(definition.getStatus(), FormFieldStatus.DELETED)) {
            return null;
        }
        MetadataFieldEntity metadata = metadataFieldMapper.selectById(definition.getMetadataFieldId());
        if (metadata == null || !Objects.equals(metadata.getStatus(), MetadataFieldStatus.ENABLED)) {
            return null;
        }
        return metadata;
    }

    /**
     * 变更元数据字段状态（启用/停用）并返回更新后的详情。
     *
     * @param id     元数据字段 id
     * @param status 目标状态
     * @return 更新后的元数据字段详情
     */
    private MetadataFieldVO changeStatus(Long id, int status) {
        MetadataFieldEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        metadataFieldMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.METADATA_FIELD, id, entity.getFieldName(),
                status == MetadataFieldStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 校验 fieldCode 在同一业务对象类型下是否唯一。
     *
     * @param bizType   业务对象类型
     * @param fieldCode 待校验的字段标识
     * @param excludeId 更新场景下需要排除的自身 id
     */
    private void checkFieldCodeUnique(String bizType, String fieldCode, Long excludeId) {
        LambdaQueryWrapper<MetadataFieldEntity> wrapper = new LambdaQueryWrapper<MetadataFieldEntity>()
                .eq(MetadataFieldEntity::getBizType, bizType)
                .eq(MetadataFieldEntity::getFieldCode, fieldCode)
                .ne(MetadataFieldEntity::getId, excludeId);
        Long count = metadataFieldMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new MetadataFieldCodeDuplicateException("字段标识[" + fieldCode + "]在该业务对象类型下已存在");
        }
    }

    /**
     * 查询一个元数据字段，不存在时抛出业务异常。
     *
     * @param id 元数据字段 id
     * @return 元数据字段实体
     */
    private MetadataFieldEntity getExistingEntity(Long id) {
        MetadataFieldEntity entity = metadataFieldMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("元数据字段不存在");
        }
        return entity;
    }

    /**
     * 把元数据字段实体列表转换为详情视图对象列表，并批量回填
     * {@code createBy}/{@code updateBy} 审计字段展示名。
     *
     * @param entities 元数据字段实体列表
     * @return 详情视图对象列表
     */
    private List<MetadataFieldVO> toVOListWithDisplayName(List<MetadataFieldEntity> entities) {
        List<MetadataFieldVO> result = MetadataFieldConvert.INSTANCE.toVOList(entities);
        if (result.isEmpty()) {
            return result;
        }

        Set<String> auditUserIdTexts = entities.stream()
                .flatMap(entity -> Stream.of(entity.getCreateBy(), entity.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);

        for (int i = 0; i < entities.size(); i++) {
            MetadataFieldVO vo = result.get(i);
            vo.setCreateBy(resolveDisplayName(entities.get(i).getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(entities.get(i).getUpdateBy(), displayNames));
        }
        return result;
    }

    /**
     * 把审计字段原始存储的用户 id 文本解析为人可读展示名，查不到时兜底为"未知用户"，
     * 避免直接把不可读的 id 数字暴露给前端。
     *
     * @param userIdText   审计字段原始存储的用户 id 文本
     * @param displayNames 批量解析得到的用户 id 文本到展示名的映射
     * @return 人可读展示名
     */
    private String resolveDisplayName(String userIdText, Map<String, String> displayNames) {
        if (!StringUtils.hasText(userIdText)) {
            return "";
        }
        return displayNames.getOrDefault(userIdText, "未知用户");
    }

    /**
     * 构造元数据字段实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值。
     *
     * @param entity 元数据字段实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(MetadataFieldEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("业务对象类型", entity.getBizType());
        snapshot.put("表名称", entity.getTableName());
        snapshot.put("字段列名", entity.getColumnName());
        snapshot.put("字段类型", entity.getColumnType());
        snapshot.put("字段标识", entity.getFieldCode());
        snapshot.put("字段名称", entity.getFieldName());
        snapshot.put("状态", statusLabel(entity.getStatus()));
        return snapshot;
    }

    /**
     * 把元数据字段状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, MetadataFieldStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, MetadataFieldStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
