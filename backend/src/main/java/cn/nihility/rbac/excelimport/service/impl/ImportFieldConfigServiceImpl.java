package cn.nihility.rbac.excelimport.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.excelimport.constant.ImportBizTypes;
import cn.nihility.rbac.excelimport.constant.ImportFieldConfigStatus;
import cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigCreateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigUpdateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.entity.ImportFieldConfigEntity;
import cn.nihility.rbac.excelimport.exception.LockedImportFieldConfigException;
import cn.nihility.rbac.excelimport.mapper.ImportFieldConfigMapper;
import cn.nihility.rbac.excelimport.mapstruct.ImportFieldConfigConvert;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
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
 * Excel 导入字段配置业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class ImportFieldConfigServiceImpl implements ImportFieldConfigService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 导入字段配置数据访问接口。 */
    private final ImportFieldConfigMapper importFieldConfigMapper;

    /** 表单字段定义数据访问接口，直接跨模块注入，用于校验 formFieldDefinitionId 的可用性。 */
    private final FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ImportFieldConfigVO> getPage(String bizType, Integer page, Integer pageSize) {
        LambdaQueryWrapper<ImportFieldConfigEntity> wrapper = new LambdaQueryWrapper<ImportFieldConfigEntity>()
                .eq(StringUtils.hasText(bizType), ImportFieldConfigEntity::getBizType, bizType)
                .ne(ImportFieldConfigEntity::getStatus, ImportFieldConfigStatus.DELETED)
                .orderByAsc(ImportFieldConfigEntity::getShowOrder)
                .orderByAsc(ImportFieldConfigEntity::getId);

        Page<ImportFieldConfigEntity> queryPage = new Page<>(page, pageSize);
        Page<ImportFieldConfigEntity> resultPage = importFieldConfigMapper.selectPage(queryPage, wrapper);
        return PageResult.of(enrich(resultPage.getRecords()), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportFieldConfigVO getById(Long id) {
        ImportFieldConfigEntity entity = getExistingEntity(id);
        return enrich(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportFieldConfigVO create(ImportFieldConfigCreateRequest request) {
        if (!ImportBizTypes.isValid(request.getBizType())) {
            throw new BusinessException("不支持的业务对象类型：" + request.getBizType());
        }

        String fieldCode;
        if (request.getFormFieldDefinitionId() != null) {
            FormFieldDefinitionEntity definition = validateFormFieldDefinition(
                    request.getFormFieldDefinitionId(), request.getBizType());
            fieldCode = definition.getFieldCode();
        } else {
            if (!StringUtils.hasText(request.getFieldCode())) {
                throw new BusinessException("未关联表单字段定义时字段标识不能为空");
            }
            fieldCode = request.getFieldCode();
        }

        if (importFieldConfigMapper.existsActiveByFieldCode(request.getBizType(), fieldCode)) {
            throw new BusinessException("字段标识[" + fieldCode + "]已存在导入配置");
        }

        ImportFieldConfigEntity entity = ImportFieldConfigConvert.INSTANCE.toEntity(request);
        entity.setFieldCode(fieldCode);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(ImportFieldConfigStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        importFieldConfigMapper.insert(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.IMPORT_FIELD_CONFIG, entity.getId(),
                entity.getExcelHeaderName(), toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportFieldConfigVO update(Long id, ImportFieldConfigUpdateRequest request) {
        ImportFieldConfigEntity entity = getExistingEntity(id);
        boolean locked = LockedImportFieldConfigs.isLocked(entity.getBizType(), entity.getFieldCode());
        if (locked) {
            if (request.getFormFieldDefinitionId() != null) {
                throw new LockedImportFieldConfigException("系统保护的导入字段配置不允许改绑表单字段定义");
            }
            // 锁定行的主键/必填标记须保持种子数据预置的原值：POSITION/APP 的固定列均为
            // true，ORG 的 __parentCode 则为 false（顶级组织本无上级，允许留空），因此
            // 与实体当前值比对是否发生变化，而不是一律强制为 true。
            if (!Objects.equals(request.getIsPrimaryKey(), entity.getIsPrimaryKey())) {
                throw new LockedImportFieldConfigException("系统保护的导入字段配置不允许修改主键标记");
            }
            if (!Objects.equals(request.getIsRequired(), entity.getIsRequired())) {
                throw new LockedImportFieldConfigException("系统保护的导入字段配置不允许修改必填标记");
            }
        }

        boolean rebind = request.getFormFieldDefinitionId() != null
                && !Objects.equals(request.getFormFieldDefinitionId(), entity.getFormFieldDefinitionId());
        String rebindFieldCode = null;
        if (rebind) {
            FormFieldDefinitionEntity target =
                    validateFormFieldDefinition(request.getFormFieldDefinitionId(), entity.getBizType());
            rebindFieldCode = target.getFieldCode();
            if (!Objects.equals(rebindFieldCode, entity.getFieldCode())
                    && importFieldConfigMapper.existsActiveByFieldCodeExcluding(
                            entity.getBizType(), rebindFieldCode, id)) {
                throw new BusinessException("字段标识[" + rebindFieldCode + "]已存在导入配置");
            }
        }

        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        ImportFieldConfigConvert.INSTANCE.updateEntity(request, entity);
        if (rebind) {
            entity.setFormFieldDefinitionId(request.getFormFieldDefinitionId());
            entity.setFieldCode(rebindFieldCode);
        }
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        importFieldConfigMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.IMPORT_FIELD_CONFIG, id,
                entity.getExcelHeaderName(), beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        ImportFieldConfigEntity entity = getExistingEntity(id);
        if (LockedImportFieldConfigs.isLocked(entity.getBizType(), entity.getFieldCode())) {
            throw new LockedImportFieldConfigException("系统保护的导入字段配置不允许删除");
        }
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(ImportFieldConfigStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        importFieldConfigMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.IMPORT_FIELD_CONFIG, id,
                entity.getExcelHeaderName(), beforeSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ImportFieldConfigVO> listActiveByBizType(String bizType) {
        List<ImportFieldConfigEntity> entities = importFieldConfigMapper.selectList(
                new LambdaQueryWrapper<ImportFieldConfigEntity>()
                        .eq(ImportFieldConfigEntity::getBizType, bizType)
                        .eq(ImportFieldConfigEntity::getStatus, ImportFieldConfigStatus.ENABLED)
                        .orderByAsc(ImportFieldConfigEntity::getShowOrder)
                        .orderByAsc(ImportFieldConfigEntity::getId));
        return enrich(entities);
    }

    /**
     * 查询一个未被逻辑删除的导入字段配置，不存在时抛出业务异常。
     *
     * @param id 导入字段配置 id
     * @return 导入字段配置实体
     */
    private ImportFieldConfigEntity getExistingEntity(Long id) {
        ImportFieldConfigEntity entity = importFieldConfigMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), ImportFieldConfigStatus.DELETED)) {
            throw new BusinessException("导入字段配置不存在");
        }
        return entity;
    }

    /**
     * 校验一个 {@code formFieldDefinitionId} 是否指向一个存在、状态启用、
     * {@code bizType} 与当前配置一致的表单字段定义。
     *
     * @param formFieldDefinitionId 表单字段定义 id
     * @param bizType                当前导入字段配置的业务对象类型
     * @return 校验通过的表单字段定义实体
     */
    private FormFieldDefinitionEntity validateFormFieldDefinition(Long formFieldDefinitionId, String bizType) {
        FormFieldDefinitionEntity definition = formFieldDefinitionMapper.selectById(formFieldDefinitionId);
        if (definition == null || !Objects.equals(definition.getStatus(), FormFieldStatus.ENABLED)) {
            throw new BusinessException("关联的表单字段定义不存在或未启用");
        }
        if (!Objects.equals(definition.getBizType(), bizType)) {
            throw new BusinessException("关联的表单字段定义业务对象类型与当前配置不一致");
        }
        return definition;
    }

    /**
     * 把实体列表转换为详情视图对象列表，并回填 {@code locked} 标记。
     *
     * @param entities 导入字段配置实体列表
     * @return 详情视图对象列表
     */
    private List<ImportFieldConfigVO> enrich(List<ImportFieldConfigEntity> entities) {
        List<ImportFieldConfigVO> vos = ImportFieldConfigConvert.INSTANCE.toVOList(entities);
        for (int i = 0; i < vos.size(); i++) {
            ImportFieldConfigEntity entity = entities.get(i);
            vos.get(i).setLocked(LockedImportFieldConfigs.isLocked(entity.getBizType(), entity.getFieldCode()));
        }
        return vos;
    }

    /**
     * 构造导入字段配置实体的操作日志字段快照，key 为中文字段名，value 为人类可读的
     * 格式化值。
     *
     * @param entity 导入字段配置实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(ImportFieldConfigEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("业务对象类型", entity.getBizType());
        snapshot.put("字段标识", entity.getFieldCode());
        snapshot.put("Excel 表头", entity.getExcelHeaderName());
        snapshot.put("是否主键", entity.getIsPrimaryKey());
        snapshot.put("是否必填", entity.getIsRequired());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("状态", Objects.equals(entity.getStatus(), ImportFieldConfigStatus.ENABLED) ? "启用" : "已删除");
        return snapshot;
    }
}
