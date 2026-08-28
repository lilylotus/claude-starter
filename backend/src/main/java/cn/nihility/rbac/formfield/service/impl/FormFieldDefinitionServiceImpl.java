package cn.nihility.rbac.formfield.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.constant.DictStatus;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.constant.LockedFormFields;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionCreateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionUpdateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.dto.FormFieldDictOptionVO;
import cn.nihility.rbac.formfield.dto.FormFieldRenderItemVO;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.exception.DictTypeRequiredException;
import cn.nihility.rbac.formfield.exception.LockedFormFieldException;
import cn.nihility.rbac.formfield.exception.MetadataFieldAlreadyBoundException;
import cn.nihility.rbac.formfield.exception.MetadataFieldUnavailableException;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.formfield.mapstruct.FormFieldDefinitionConvert;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
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
 * 表单字段定义业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class FormFieldDefinitionServiceImpl implements FormFieldDefinitionService {

    /** 表单字段定义数据访问接口。 */
    private final FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 元数据字段数据访问接口，直接跨模块注入，用于校验绑定关系、解析列名/锁定判定。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 字典类型数据访问接口，直接跨模块注入，用于按编码校验字典类型是否存在、批量解析名称。 */
    private final DictTypeMapper dictTypeMapper;

    /** 字典项业务逻辑接口，用于按字典类型编码查询启用状态的字典项作为下拉选项来源。 */
    private final DictItemService dictItemService;

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
    public PageResult<FormFieldDefinitionVO> getPage(String bizType, Integer page, Integer pageSize) {
        LambdaQueryWrapper<FormFieldDefinitionEntity> wrapper = new LambdaQueryWrapper<FormFieldDefinitionEntity>()
                .eq(StringUtils.hasText(bizType), FormFieldDefinitionEntity::getBizType, bizType)
                .ne(FormFieldDefinitionEntity::getStatus, FormFieldStatus.DELETED)
                .orderByAsc(FormFieldDefinitionEntity::getShowOrder)
                .orderByAsc(FormFieldDefinitionEntity::getId);

        Page<FormFieldDefinitionEntity> queryPage = new Page<>(page, pageSize);
        Page<FormFieldDefinitionEntity> resultPage = formFieldDefinitionMapper.selectPage(queryPage, wrapper);
        List<FormFieldDefinitionVO> records = enrich(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormFieldDefinitionVO getById(Long id) {
        FormFieldDefinitionEntity entity = getExistingEntity(id);
        return enrich(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormFieldDefinitionVO create(FormFieldDefinitionCreateRequest request) {
        MetadataFieldEntity metadata = metadataFieldMapper.selectById(request.getMetadataFieldId());
        if (metadata == null || !Objects.equals(metadata.getStatus(), MetadataFieldStatus.ENABLED)) {
            throw new MetadataFieldUnavailableException("绑定的元数据字段不存在或未启用");
        }
        if (formFieldDefinitionMapper.existsActiveByMetadataFieldId(metadata.getId())) {
            throw new MetadataFieldAlreadyBoundException("该元数据字段已被其他表单字段定义绑定");
        }
        validateDictType(request.getControlType(), request.getDictTypeCode());

        FormFieldDefinitionEntity entity = FormFieldDefinitionConvert.INSTANCE.toEntity(request);
        entity.setBizType(metadata.getBizType());
        entity.setFieldCode(metadata.getFieldCode());
        if (!FormFieldControlType.DICT_TYPES.contains(entity.getControlType())) {
            entity.setDictTypeCode(null);
        }
        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(FormFieldStatus.ENABLED);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        formFieldDefinitionMapper.insert(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.FORM_FIELD_DEFINITION, entity.getId(),
                entity.getFieldName(), toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormFieldDefinitionVO update(Long id, FormFieldDefinitionUpdateRequest request) {
        FormFieldDefinitionEntity entity = getExistingEntity(id);
        boolean locked = computeLocked(entity.getBizType(), entity.getMetadataFieldId());
        if (locked) {
            if (!Boolean.TRUE.equals(request.getIsRequired())) {
                throw new LockedFormFieldException("承重字段的表单定义不允许配置为非必填");
            }
            if (!Boolean.TRUE.equals(request.getShowInCreate())) {
                throw new LockedFormFieldException("承重字段的表单定义不允许在新增表单中隐藏");
            }
            if (!Boolean.TRUE.equals(request.getShowInEdit())) {
                throw new LockedFormFieldException("承重字段的表单定义不允许在编辑表单中隐藏");
            }
        }

        boolean rebind = request.getMetadataFieldId() != null
                && !Objects.equals(request.getMetadataFieldId(), entity.getMetadataFieldId());
        if (rebind && locked) {
            throw new LockedFormFieldException("承重字段的表单定义不允许改绑元数据字段");
        }
        MetadataFieldEntity rebindTarget = null;
        if (rebind) {
            rebindTarget = validateRebindTarget(entity, request.getMetadataFieldId());
        }

        validateDictType(request.getControlType(), request.getDictTypeCode());

        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        FormFieldDefinitionConvert.INSTANCE.updateEntity(request, entity);
        if (rebind) {
            entity.setMetadataFieldId(request.getMetadataFieldId());
            entity.setFieldCode(rebindTarget.getFieldCode());
        }
        if (!FormFieldControlType.DICT_TYPES.contains(entity.getControlType())) {
            entity.setDictTypeCode(null);
        }
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        formFieldDefinitionMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.FORM_FIELD_DEFINITION, id, entity.getFieldName(),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormFieldDefinitionVO enable(Long id) {
        return changeStatus(id, FormFieldStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormFieldDefinitionVO disable(Long id) {
        FormFieldDefinitionEntity entity = getExistingEntity(id);
        if (computeLocked(entity.getBizType(), entity.getMetadataFieldId())) {
            throw new LockedFormFieldException("承重字段的表单定义不允许停用");
        }
        return changeStatus(id, FormFieldStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        FormFieldDefinitionEntity entity = getExistingEntity(id);
        if (computeLocked(entity.getBizType(), entity.getMetadataFieldId())) {
            throw new LockedFormFieldException("承重字段的表单定义不允许删除");
        }
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(FormFieldStatus.DELETED);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        formFieldDefinitionMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.FORM_FIELD_DEFINITION, id, entity.getFieldName(),
                beforeSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FormFieldDefinitionVO> listActiveByBizType(String bizType) {
        List<FormFieldDefinitionEntity> entities = formFieldDefinitionMapper.selectList(
                new LambdaQueryWrapper<FormFieldDefinitionEntity>()
                        .eq(FormFieldDefinitionEntity::getBizType, bizType)
                        .eq(FormFieldDefinitionEntity::getStatus, FormFieldStatus.ENABLED)
                        .orderByAsc(FormFieldDefinitionEntity::getShowOrder)
                        .orderByAsc(FormFieldDefinitionEntity::getId));

        return enrich(entities).stream()
                .filter(vo -> !Boolean.TRUE.equals(vo.getLocked()))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FormFieldRenderItemVO> buildRenderSchema(String bizType) {
        List<FormFieldDefinitionEntity> entities = formFieldDefinitionMapper.selectList(
                new LambdaQueryWrapper<FormFieldDefinitionEntity>()
                        .eq(FormFieldDefinitionEntity::getBizType, bizType)
                        .eq(FormFieldDefinitionEntity::getStatus, FormFieldStatus.ENABLED)
                        .orderByAsc(FormFieldDefinitionEntity::getShowOrder)
                        .orderByAsc(FormFieldDefinitionEntity::getId));
        List<FormFieldDefinitionVO> vos = enrich(entities);

        List<FormFieldRenderItemVO> result = new ArrayList<>();
        for (FormFieldDefinitionVO vo : vos) {
            result.add(FormFieldRenderItemVO.builder()
                    .fieldCode(vo.getFieldCode())
                    .fieldName(vo.getFieldName())
                    .columnName(vo.getColumnName())
                    .controlType(vo.getControlType())
                    .isRequired(vo.getIsRequired())
                    .isUnique(vo.getIsUnique())
                    .showInList(vo.getShowInList())
                    .showInCreate(vo.getShowInCreate())
                    .showInEdit(vo.getShowInEdit())
                    .showInExport(vo.getShowInExport())
                    .editable(vo.getEditable())
                    .locked(vo.getLocked())
                    .validateRegex(vo.getValidateRegex())
                    .placeholder(vo.getPlaceholder())
                    .showOrder(vo.getShowOrder())
                    .dictOptions(resolveDictOptions(vo.getControlType(), vo.getDictTypeCode()))
                    .build());
        }
        return result;
    }

    /**
     * 变更字段定义状态（启用/停用）并返回更新后的详情。
     *
     * @param id     字段定义 id
     * @param status 目标状态
     * @return 更新后的字段定义详情
     */
    private FormFieldDefinitionVO changeStatus(Long id, int status) {
        FormFieldDefinitionEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        formFieldDefinitionMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.FORM_FIELD_DEFINITION, id,
                entity.getFieldName(), status == FormFieldStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的表单字段定义，不存在时抛出业务异常。
     *
     * @param id 字段定义 id
     * @return 表单字段定义实体
     */
    private FormFieldDefinitionEntity getExistingEntity(Long id) {
        FormFieldDefinitionEntity entity = formFieldDefinitionMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), FormFieldStatus.DELETED)) {
            throw new BusinessException("表单字段定义不存在");
        }
        return entity;
    }

    /**
     * 校验非锁定定义编辑改绑的目标元数据字段：必须存在、状态为启用、{@code bizType}
     * 与当前定义一致，且未被其他有效定义占用（排除定义自身）。
     *
     * @param entity             待改绑的表单字段定义实体（改绑前状态）
     * @param newMetadataFieldId 改绑目标的元数据字段 id
     * @return 校验通过的改绑目标元数据字段实体，供调用方同步刷新 {@code fieldCode}
     */
    private MetadataFieldEntity validateRebindTarget(FormFieldDefinitionEntity entity, Long newMetadataFieldId) {
        MetadataFieldEntity metadata = metadataFieldMapper.selectById(newMetadataFieldId);
        if (metadata == null || !Objects.equals(metadata.getStatus(), MetadataFieldStatus.ENABLED)) {
            throw new MetadataFieldUnavailableException("改绑的元数据字段不存在或未启用");
        }
        if (!Objects.equals(metadata.getBizType(), entity.getBizType())) {
            throw new MetadataFieldUnavailableException("改绑的元数据字段业务对象类型与当前定义不一致");
        }
        if (formFieldDefinitionMapper.existsActiveByMetadataFieldIdExcluding(newMetadataFieldId, entity.getId())) {
            throw new MetadataFieldAlreadyBoundException("该元数据字段已被其他表单字段定义绑定");
        }
        return metadata;
    }

    /**
     * 控件类型为下拉单选字典或多选字典下拉时，校验必须关联一个存在且未被逻辑删除的
     * 字典类型；其余控件类型不做校验。
     *
     * @param controlType  控件类型
     * @param dictTypeCode 关联的字典类型编码
     */
    private void validateDictType(Integer controlType, String dictTypeCode) {
        if (!FormFieldControlType.DICT_TYPES.contains(controlType)) {
            return;
        }
        if (!StringUtils.hasText(dictTypeCode)) {
            throw new DictTypeRequiredException("控件类型为下拉单选字典或多选字典下拉时必须关联一个存在的字典类型");
        }
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getCode, dictTypeCode)
                .ne(DictTypeEntity::getStatus, DictStatus.DELETED));
        if (count == null || count == 0) {
            throw new DictTypeRequiredException("控件类型为下拉单选字典或多选字典下拉时必须关联一个存在的字典类型");
        }
    }

    /**
     * 根据业务对象类型与绑定的元数据字段 id，计算该定义是否为承重字段（锁定字段）。
     *
     * @param bizType         业务对象类型
     * @param metadataFieldId 绑定的元数据字段 id
     * @return 是否为承重字段
     */
    private boolean computeLocked(String bizType, Long metadataFieldId) {
        MetadataFieldEntity metadata = metadataFieldMapper.selectById(metadataFieldId);
        String columnName = metadata != null ? metadata.getColumnName() : null;
        return LockedFormFields.isLocked(bizType, columnName);
    }

    /**
     * 把实体列表转换为详情视图对象列表，并批量回填 {@code columnName}/
     * {@code dictTypeName}/{@code locked}，避免逐条查询元数据字段表/字典类型表；
     * 同时批量回填 {@code createBy}/{@code updateBy} 审计字段展示名。
     *
     * @param entities 表单字段定义实体列表
     * @return 详情视图对象列表
     */
    private List<FormFieldDefinitionVO> enrich(List<FormFieldDefinitionEntity> entities) {
        List<FormFieldDefinitionVO> vos = FormFieldDefinitionConvert.INSTANCE.toVOList(entities);
        if (vos.isEmpty()) {
            return vos;
        }

        Map<Long, MetadataFieldEntity> metadataMap = fetchMetadataMap(entities);
        Map<String, String> dictTypeNameMap = fetchDictTypeNameMap(entities);
        Set<String> auditUserIdTexts = entities.stream()
                .flatMap(entity -> Stream.of(entity.getCreateBy(), entity.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);

        for (int i = 0; i < vos.size(); i++) {
            FormFieldDefinitionVO vo = vos.get(i);
            FormFieldDefinitionEntity entity = entities.get(i);
            MetadataFieldEntity metadata = metadataMap.get(entity.getMetadataFieldId());
            String columnName = metadata != null ? metadata.getColumnName() : null;
            vo.setColumnName(columnName);
            vo.setLocked(LockedFormFields.isLocked(entity.getBizType(), columnName));
            vo.setFieldCode(metadata != null ? metadata.getFieldCode() : entity.getFieldCode());
            if (entity.getDictTypeCode() != null) {
                vo.setDictTypeName(dictTypeNameMap.get(entity.getDictTypeCode()));
            }
            vo.setCreateBy(resolveDisplayName(entity.getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(entity.getUpdateBy(), displayNames));
        }
        return vos;
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
     * 按绑定的元数据字段 id 批量查询元数据字段实体，key 为元数据字段 id。
     *
     * @param entities 表单字段定义实体列表
     * @return 元数据字段 id -> 元数据字段实体
     */
    private Map<Long, MetadataFieldEntity> fetchMetadataMap(List<FormFieldDefinitionEntity> entities) {
        List<Long> ids = entities.stream().map(FormFieldDefinitionEntity::getMetadataFieldId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MetadataFieldEntity> metadataList = metadataFieldMapper.selectByIds(ids);
        return metadataList.stream()
                .collect(Collectors.toMap(MetadataFieldEntity::getId, entity -> entity, (left, right) -> left));
    }

    /**
     * 按关联的字典类型编码批量查询字典类型名称，key 为字典类型编码。
     *
     * @param entities 表单字段定义实体列表
     * @return 字典类型编码 -> 字典类型名称
     */
    private Map<String, String> fetchDictTypeNameMap(List<FormFieldDefinitionEntity> entities) {
        List<String> dictTypeCodes = entities.stream()
                .map(FormFieldDefinitionEntity::getDictTypeCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (dictTypeCodes.isEmpty()) {
            return Map.of();
        }
        List<DictTypeEntity> dictTypes = dictTypeMapper.selectList(new LambdaQueryWrapper<DictTypeEntity>()
                .in(DictTypeEntity::getCode, dictTypeCodes));
        return dictTypes.stream()
                .collect(Collectors.toMap(DictTypeEntity::getCode, DictTypeEntity::getName, (left, right) -> left));
    }

    /**
     * 控件类型为下拉单选字典或多选字典下拉时，解析其关联字典类型下的启用字典项
     * 作为可选项来源；其余控件类型或未关联字典类型编码时返回空列表。
     *
     * @param controlType  控件类型
     * @param dictTypeCode 关联的字典类型编码
     * @return 字典下拉可选项列表
     */
    private List<FormFieldDictOptionVO> resolveDictOptions(Integer controlType, String dictTypeCode) {
        if (!FormFieldControlType.DICT_TYPES.contains(controlType) || !StringUtils.hasText(dictTypeCode)) {
            return new ArrayList<>();
        }
        List<DictItemOptionVO> options = dictItemService.getEnabledOptions(dictTypeCode);
        return options.stream()
                .map(option -> FormFieldDictOptionVO.builder()
                        .label(option.getLabel())
                        .value(option.getCode())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 构造表单字段定义实体的操作日志字段快照，key 为中文字段名，value 为人类可读的
     * 格式化值；绑定的元数据字段列名需按 {@code metadataFieldId} 回查一次。
     *
     * @param entity 表单字段定义实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(FormFieldDefinitionEntity entity) {
        MetadataFieldEntity metadata = metadataFieldMapper.selectById(entity.getMetadataFieldId());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("业务对象类型", entity.getBizType());
        snapshot.put("绑定字段", metadata != null ? metadata.getColumnName() : null);
        snapshot.put("展示名称", entity.getFieldName());
        snapshot.put("字段标识", entity.getFieldCode());
        snapshot.put("控件类型", controlTypeLabel(entity.getControlType()));
        snapshot.put("是否唯一", entity.getIsUnique());
        snapshot.put("是否必填", entity.getIsRequired());
        snapshot.put("列表展示", entity.getShowInList());
        snapshot.put("新增展示", entity.getShowInCreate());
        snapshot.put("编辑展示", entity.getShowInEdit());
        snapshot.put("导出展示", entity.getShowInExport());
        snapshot.put("可编辑", entity.getEditable());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("状态", statusLabel(entity.getStatus()));
        return snapshot;
    }

    /**
     * 把控件类型码值转换为中文文案，供操作日志快照使用。
     *
     * @param controlType 控件类型码值
     * @return 中文文案
     */
    private String controlTypeLabel(Integer controlType) {
        if (Objects.equals(controlType, FormFieldControlType.NUMBER)) {
            return "数字框";
        }
        if (Objects.equals(controlType, FormFieldControlType.DICT)) {
            return "字典下拉";
        }
        if (Objects.equals(controlType, FormFieldControlType.DATE)) {
            return "日期";
        }
        if (Objects.equals(controlType, FormFieldControlType.MULTI_DICT)) {
            return "多选字典下拉";
        }
        return "文本框";
    }

    /**
     * 把表单字段定义状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, FormFieldStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, FormFieldStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
