package cn.nihility.rbac.app.sync.service.impl;

import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.support.AppScopeGuard;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigUpdateRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigVO;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingRow;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingSaveRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingVO;
import cn.nihility.rbac.app.sync.dto.AppSyncOrgScopeRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncOrgScopeVO;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.entity.AppSyncFieldMappingEntity;
import cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.app.sync.mapper.AppSyncFieldMappingMapper;
import cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper;
import cn.nihility.rbac.app.sync.mapstruct.AppSyncDomainConfigConvert;
import cn.nihility.rbac.app.sync.mapstruct.AppSyncFieldMappingConvert;
import cn.nihility.rbac.app.sync.service.AppSyncConfigService;
import cn.nihility.rbac.app.sync.support.TransformScriptValidator;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 应用同步配置业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppSyncConfigServiceImpl implements AppSyncConfigService {

    /**
     * 6 个数据域的默认插入顺序，同时也是 {@link #listDomainConfigs} 返回列表的自然顺序
     * （任职域补入见 app-sync-notify-pull-api change design.md Decision 1）。
     */
    private static final List<String> DEFAULT_DOMAIN_ORDER = List.of(SyncDomain.ORG, SyncDomain.USER,
            SyncDomain.POSITION, SyncDomain.APP, SyncDomain.ROLE, SyncDomain.DICT);

    /** 新建应用时每个数据域默认的拉取分页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 应用同步数据域配置数据访问接口。 */
    private final AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    /** 应用同步字段映射数据访问接口。 */
    private final AppSyncFieldMappingMapper appSyncFieldMappingMapper;

    /** 应用同步组织范围数据访问接口。 */
    private final AppSyncOrgScopeMapper appSyncOrgScopeMapper;

    /** 应用数据访问接口，仅用于只读查询应用实体（校验存在性、管辖组织范围）。 */
    private final AppMapper appMapper;

    /** 元数据字段数据访问接口，校验字段映射请求中 {@code metadataFieldId} 的合法性。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 管辖组织范围解析业务逻辑接口，写操作按应用 {@code orgId} 复用管辖范围校验。 */
    private final OrgScopeService orgScopeService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@inheritDoc}
     */
    @Override
    public void createDefaultDomainConfigs(Long appRefId, String operator) {
        LocalDateTime now = LocalDateTime.now();
        for (String domain : DEFAULT_DOMAIN_ORDER) {
            AppSyncDomainConfigEntity entity = AppSyncDomainConfigEntity.builder()
                    .appRefId(appRefId)
                    .syncDomain(domain)
                    .syncEnabled(false)
                    .pageSize(DEFAULT_PAGE_SIZE)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            appSyncDomainConfigMapper.insert(entity);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppSyncDomainConfigVO> listDomainConfigs(Long appRefId) {
        List<AppSyncDomainConfigEntity> entities = appSyncDomainConfigMapper.selectList(
                new LambdaQueryWrapper<AppSyncDomainConfigEntity>()
                        .eq(AppSyncDomainConfigEntity::getAppRefId, appRefId)
                        .orderByAsc(AppSyncDomainConfigEntity::getId));
        return AppSyncDomainConfigConvert.INSTANCE.toVOList(entities);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppSyncDomainConfigVO updateDomainConfig(Long appRefId, String syncDomain,
            AppSyncDomainConfigUpdateRequest request) {
        if (!SyncDomain.ALL_DOMAINS.contains(syncDomain)) {
            throw new BusinessException("非法的数据域：" + syncDomain);
        }
        AppEntity appEntity = AppScopeGuard.getExistingAppInScope(appMapper, orgScopeService, appRefId);
        AppSyncDomainConfigEntity entity = findDomainConfig(appRefId, syncDomain);
        Map<String, Object> beforeSnapshot = toDomainConfigLogSnapshot(entity);

        entity.setSyncEnabled(request.getSyncEnabled());
        entity.setPageSize(request.getPageSize());
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appSyncDomainConfigMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                beforeSnapshot, toDomainConfigLogSnapshot(entity));
        return AppSyncDomainConfigConvert.INSTANCE.toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppSyncFieldMappingVO> listFieldMappings(Long appRefId, String syncDomain) {
        if (SyncDomain.DICT.equals(syncDomain)) {
            return List.of();
        }
        List<AppSyncFieldMappingRow> rows =
                appSyncFieldMappingMapper.selectByAppRefIdAndDomain(appRefId, syncDomain);
        return AppSyncFieldMappingConvert.INSTANCE.toVOList(rows);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<AppSyncFieldMappingVO> replaceFieldMappings(Long appRefId, String syncDomain,
            List<AppSyncFieldMappingSaveRequest> requests) {
        if (!SyncDomain.FIELD_MAPPING_DOMAINS.contains(syncDomain)) {
            throw new BusinessException("该数据域不支持字段级同步配置");
        }
        AppEntity appEntity = AppScopeGuard.getExistingAppInScope(appMapper, orgScopeService, appRefId);
        List<AppSyncFieldMappingSaveRequest> effectiveRequests = requests == null ? List.of() : requests;
        assertRequestsValid(syncDomain, effectiveRequests);

        LambdaQueryWrapper<AppSyncFieldMappingEntity> deleteWrapper =
                new LambdaQueryWrapper<AppSyncFieldMappingEntity>()
                        .eq(AppSyncFieldMappingEntity::getAppRefId, appRefId)
                        .eq(AppSyncFieldMappingEntity::getSyncDomain, syncDomain);
        Long beforeCount = appSyncFieldMappingMapper.selectCount(deleteWrapper);
        beforeCount = beforeCount == null ? 0L : beforeCount;
        appSyncFieldMappingMapper.delete(deleteWrapper);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        for (AppSyncFieldMappingSaveRequest request : effectiveRequests) {
            AppSyncFieldMappingEntity entity = AppSyncFieldMappingConvert.INSTANCE.toEntity(request);
            entity.setAppRefId(appRefId);
            entity.setSyncDomain(syncDomain);
            entity.setTransformValue(TransformType.NO_TRANSFORM.equals(request.getTransformType())
                    ? null : request.getTransformValue());
            entity.setCreateBy(operator);
            entity.setCreateTime(now);
            entity.setUpdateBy(operator);
            entity.setUpdateTime(now);
            appSyncFieldMappingMapper.insert(entity);
        }

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                Map.of("字段映射", beforeCount + " 行"), Map.of("字段映射", effectiveRequests.size() + " 行"));
        return listFieldMappings(appRefId, syncDomain);
    }

    /**
     * 逐项校验字段映射保存请求：{@code metadataFieldId} 必须存在、状态启用、
     * {@code bizType} 与 {@code syncDomain} 一致；同一请求列表内 {@code metadataFieldId}
     * 不允许重复；{@code transformType=FIXED_VALUE} 时 {@code transformValue} 必填；
     * {@code transformType=SCRIPT} 时 {@code transformValue} 必填且语法必须合法。
     *
     * @param syncDomain 数据域
     * @param requests   本次提交的完整字段映射行列表
     */
    private void assertRequestsValid(String syncDomain, List<AppSyncFieldMappingSaveRequest> requests) {
        // 重复校验独立成一趟纯内存遍历，先于任何数据库查询执行：既能在真正命中重复时快速失败，
        // 也让本方法不依赖某个具体请求项是否已通过其余校验，逻辑上更清晰。
        Set<Long> seenMetadataFieldIds = new HashSet<>();
        for (AppSyncFieldMappingSaveRequest request : requests) {
            if (!seenMetadataFieldIds.add(request.getMetadataFieldId())) {
                throw new BusinessException("同一数据域下，同一源字段不能重复添加映射");
            }
        }

        for (AppSyncFieldMappingSaveRequest request : requests) {
            // Spring MVC 对裸 List<T> 请求体的逐元素 Bean Validation 级联在不同版本间存在
            // 不确定性，这里不依赖 @Valid 是否生效，显式兜底校验应用字段名称/编码非空、转换
            // 方式取值合法，保证业务正确性不依赖框架行为（实现偏差说明见 tasks.md）。
            if (!StringUtils.hasText(request.getAppFieldName()) || !StringUtils.hasText(request.getAppFieldCode())) {
                throw new BusinessException("应用字段名称、应用字段编码不能为空");
            }
            if (!TransformType.ALL_TYPES.contains(request.getTransformType())) {
                throw new BusinessException("非法的转换方式：" + request.getTransformType());
            }
            assertMetadataFieldValid(syncDomain, request.getMetadataFieldId());
            assertTransformValueValid(request.getTransformType(), request.getTransformValue());
        }
    }

    /**
     * 校验字段映射请求中的源字段：必须存在、状态启用、{@code bizType} 与 {@code syncDomain}
     * 一致。
     *
     * @param syncDomain      数据域
     * @param metadataFieldId 源字段 id
     */
    private void assertMetadataFieldValid(String syncDomain, Long metadataFieldId) {
        MetadataFieldEntity metadataField = metadataFieldMapper.selectById(metadataFieldId);
        if (metadataField == null || !Objects.equals(metadataField.getStatus(), MetadataFieldStatus.ENABLED)) {
            throw new BusinessException("源字段不存在或未启用：" + metadataFieldId);
        }
        if (!Objects.equals(metadataField.getBizType(), syncDomain)) {
            throw new BusinessException("源字段[" + metadataField.getFieldName() + "]不属于数据域 " + syncDomain);
        }
    }

    /**
     * 按转换方式校验转换取值：{@code FIXED_VALUE} 要求非空；{@code SCRIPT} 要求非空且语法
     * 合法（{@link TransformScriptValidator#validateSyntax}）；{@code NO_TRANSFORM} 不校验。
     *
     * @param transformType  转换方式
     * @param transformValue 转换取值
     */
    private void assertTransformValueValid(String transformType, String transformValue) {
        if (TransformType.FIXED_VALUE.equals(transformType) && !StringUtils.hasText(transformValue)) {
            throw new BusinessException("转换方式为固定值时，转换取值不能为空");
        }
        if (TransformType.SCRIPT.equals(transformType)) {
            if (!StringUtils.hasText(transformValue)) {
                throw new BusinessException("转换方式为转换脚本时，转换取值不能为空");
            }
            TransformScriptValidator.validateSyntax(transformValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppSyncOrgScopeVO> listOrgScope(Long appRefId, String syncDomain) {
        if (!SyncDomain.ORG_SCOPE_DOMAINS.contains(syncDomain)) {
            throw new BusinessException("该数据域不支持同步范围配置");
        }
        return appSyncOrgScopeMapper.selectByAppRefIdAndDomain(appRefId, syncDomain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<AppSyncOrgScopeVO> replaceOrgScope(Long appRefId, String syncDomain,
            List<AppSyncOrgScopeRequest> requests) {
        if (!SyncDomain.ORG_SCOPE_DOMAINS.contains(syncDomain)) {
            throw new BusinessException("该数据域不支持同步范围配置");
        }
        AppEntity appEntity = AppScopeGuard.getExistingAppInScope(appMapper, orgScopeService, appRefId);
        List<AppSyncOrgScopeRequest> effectiveRequests = requests == null ? List.of() : requests;
        assertOrgScopeRequestsValid(effectiveRequests);

        LambdaQueryWrapper<AppSyncOrgScopeEntity> deleteWrapper = new LambdaQueryWrapper<AppSyncOrgScopeEntity>()
                .eq(AppSyncOrgScopeEntity::getAppRefId, appRefId)
                .eq(AppSyncOrgScopeEntity::getSyncDomain, syncDomain);
        Long beforeCount = appSyncOrgScopeMapper.selectCount(deleteWrapper);
        beforeCount = beforeCount == null ? 0L : beforeCount;
        appSyncOrgScopeMapper.delete(deleteWrapper);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        for (AppSyncOrgScopeRequest request : effectiveRequests) {
            AppSyncOrgScopeEntity entity = AppSyncOrgScopeEntity.builder()
                    .appRefId(appRefId)
                    .syncDomain(syncDomain)
                    .orgId(request.getOrgId())
                    .includeChildren(request.getIncludeChildren())
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            appSyncOrgScopeMapper.insert(entity);
        }

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                Map.of("同步范围", beforeCount + " 行"), Map.of("同步范围", effectiveRequests.size() + " 行"));
        return listOrgScope(appRefId, syncDomain);
    }

    /**
     * 逐项校验同步范围保存请求：{@code orgId} 必填；同一请求列表内 {@code orgId} 不允许重复。
     *
     * @param requests 本次提交的完整同步范围行列表
     */
    private void assertOrgScopeRequestsValid(List<AppSyncOrgScopeRequest> requests) {
        Set<Long> seenOrgIds = new HashSet<>();
        for (AppSyncOrgScopeRequest request : requests) {
            if (request.getOrgId() == null) {
                throw new BusinessException("组织不能为空");
            }
            if (!seenOrgIds.add(request.getOrgId())) {
                throw new BusinessException("同一数据域下，同一组织不能重复添加");
            }
        }
    }

    /**
     * 按 {@code (appRefId, syncDomain)} 查询数据域配置，理论上因 {@code createDefaultDomainConfigs}
     * 已预置 5 行必然存在，仍做防御性判空。
     *
     * @param appRefId   应用 id
     * @param syncDomain 数据域
     * @return 数据域配置实体
     */
    private AppSyncDomainConfigEntity findDomainConfig(Long appRefId, String syncDomain) {
        AppSyncDomainConfigEntity entity = appSyncDomainConfigMapper.selectOne(
                new LambdaQueryWrapper<AppSyncDomainConfigEntity>()
                        .eq(AppSyncDomainConfigEntity::getAppRefId, appRefId)
                        .eq(AppSyncDomainConfigEntity::getSyncDomain, syncDomain));
        if (entity == null) {
            throw new BusinessException("应用不存在");
        }
        return entity;
    }

    /**
     * 构造数据域配置的操作日志字段快照，key 为中文字段名。
     *
     * @param entity 数据域配置实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toDomainConfigLogSnapshot(AppSyncDomainConfigEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("数据域", entity.getSyncDomain());
        snapshot.put("是否启用", Boolean.TRUE.equals(entity.getSyncEnabled()) ? "开启" : "关闭");
        snapshot.put("拉取分页大小", entity.getPageSize());
        return snapshot;
    }
}
