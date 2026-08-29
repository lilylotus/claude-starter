package cn.nihility.rbac.app.service.impl;

import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.mapstruct.AppConvert;
import cn.nihility.rbac.app.service.AppConfigService;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.DynamicFieldValidator;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.event.DomainEventPublisher;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 应用管理业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    /**
     * {@code bizType=APP} 下允许被动态字段唯一性校验拼进 {@code ${column}} 的列名
     * 白名单，取自 {@code tab_metadata_field} 目录里 APP 的原有可配置列 +
     * {@code ext1}..{@code ext10}（design.md Decision 3/8）；{@code name}/
     * {@code code} 虽然也在目录中，但属于承重字段，不会经过这条动态校验管线，
     * 列入白名单只是双重防护，不影响实际调用路径。
     */
    private static final Set<String> ALLOWED_DYNAMIC_COLUMNS = Set.of(
            "name", "code", "show_order", "remark",
            "ext1", "ext2", "ext3", "ext4", "ext5", "ext6", "ext7", "ext8", "ext9", "ext10");

    /** 应用数据访问接口。 */
    private final AppMapper appMapper;

    /** 用户数据访问接口，仅用于回填负责人姓名。 */
    private final UserMapper userMapper;

    /** 组织数据访问接口，仅用于回填所属组织名称。 */
    private final OrgMapper orgMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 表单字段定义业务逻辑接口，用于驱动非锁定字段的必填/正则/唯一性校验。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /** 操作日志扩展字段快照填充组件，负责把字典类扩展字段的存储编码解析为标签。 */
    private final FormFieldSnapshotSupport formFieldSnapshotSupport;

    /**
     * 管辖组织范围解析业务逻辑接口，用于按当前登录用户的管辖组织范围过滤应用列表
     * （org-scope-data-permission change design.md Decision 6）。
     */
    private final OrgScopeService orgScopeService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 审计字段（{@code createBy}/{@code updateBy}）展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * 应用对外接口配置业务逻辑接口，新建应用时在同一事务内生成默认凭证配置
     * （app-api-credentials-config change design.md Decision 1）。
     */
    private final AppConfigService appConfigService;

    /**
     * 数据变更事件发布抽象，应用数据新增/编辑/启用/停用/删除写操作成功后紧邻
     * {@code operationLogRecorder} 调用之后发布一次同步事件
     * （app-sync-notify-pull-api change design.md Decision 5）。
     */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppVO> getPage(Integer page, Integer pageSize) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<AppEntity>()
                .ne(AppEntity::getStatus, AppStatus.DELETED)
                .orderByDesc(AppEntity::getShowOrder)
                .orderByAsc(AppEntity::getId);

        // 该接口此前完全没有组织维度的过滤逻辑；受限时追加 org_id IN (:allowedOrgIds)
        // 过滤条件，不受限时行为不变（org-scope-data-permission change design.md
        // Decision 6）。
        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        if (allowedOrgIds.isPresent()) {
            Set<Long> allowed = allowedOrgIds.get();
            if (allowed.isEmpty()) {
                // 防御性写法：按设计 resolveAllowedOrgIds 返回非空 Optional 时其内部 Set
                // 理论上必然非空，但不依赖这个隐含前提，避免不同版本 MyBatis-Plus 对空集合
                // .in() 生成的 SQL 行为不一致（可能生成恒真或语法错误的 SQL），改用恒不匹配
                // 的哨兵条件。
                wrapper.eq(AppEntity::getId, -1L);
            } else {
                wrapper.in(AppEntity::getOrgId, allowed);
            }
        }

        Page<AppEntity> queryPage = new Page<>(page, pageSize);
        Page<AppEntity> resultPage = appMapper.selectPage(queryPage, wrapper);
        List<AppVO> records = toVOListWithNames(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO getById(Long id) {
        AppEntity entity = getExistingEntity(id);
        return toVOListWithNames(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 创建应用与创建默认应用配置（{@link AppConfigService#createDefaultConfig}）需在同一
     * 事务内完成，因此整个方法标注 {@link Transactional}。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public AppVO create(AppCreateRequest request) {
        assertOrgInScope(request.getOrgId());
        checkCodeUnique(request.getCode(), null);
        validateDynamicFields(request, true, null);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        AppEntity entity = AppConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(AppStatus.ENABLED);
        entity.setVersion(1L);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        appMapper.insert(entity);
        appConfigService.createDefaultConfig(entity.getId(), operator);

        operationLogRecorder.recordCreate(OperationLogResourceType.APP, entity.getId(), entity.getName(),
                toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.APP)
                .bizId(entity.getId())
                .operationType(OperationType.CREATE)
                .entityVersion(entity.getVersion())
                .operator(entity.getCreateBy())
                .occurredAt(LocalDateTime.now())
                .build());

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO update(Long id, AppUpdateRequest request) {
        AppEntity entity = getExistingEntityInScope(id);
        assertOrgInScope(request.getOrgId());
        checkCodeUnique(request.getCode(), id);
        validateDynamicFields(request, false, id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        AppConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(entity);
        appMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.APP)
                .bizId(id)
                .operationType(OperationType.UPDATE)
                .entityVersion(entity.getVersion())
                .operator(entity.getUpdateBy())
                .occurredAt(LocalDateTime.now())
                .build());

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO enable(Long id) {
        return changeStatus(id, AppStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO disable(Long id) {
        return changeStatus(id, AppStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        AppEntity entity = getExistingEntityInScope(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(AppStatus.DELETED);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(entity);
        appMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        operationLogRecorder.recordDelete(OperationLogResourceType.APP, id, entity.getName(), beforeSnapshot);
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.APP)
                .bizId(id)
                .operationType(OperationType.DELETE)
                .entityVersion(entity.getVersion())
                .operator(entity.getUpdateBy())
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /**
     * {@inheritDoc}
     * <p>
     * 复用 {@link #getPage} 已具备的 {@code allowedOrgIds} 过滤逻辑，去掉分页
     * （master-data-excel-export change design.md Decision 1）。
     */
    @Override
    public List<AppVO> listAllForExport() {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<AppEntity>()
                .ne(AppEntity::getStatus, AppStatus.DELETED)
                .orderByAsc(AppEntity::getId);

        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        if (allowedOrgIds.isPresent()) {
            Set<Long> allowed = allowedOrgIds.get();
            if (allowed.isEmpty()) {
                wrapper.eq(AppEntity::getId, -1L);
            } else {
                wrapper.in(AppEntity::getOrgId, allowed);
            }
        }

        List<AppEntity> entities = appMapper.selectList(wrapper);
        return toVOListWithNames(entities);
    }

    /**
     * 变更应用状态（启用/停用）并返回更新后的详情。
     *
     * @param id     应用 id
     * @param status 目标状态
     * @return 更新后的应用详情
     */
    private AppVO changeStatus(Long id, int status) {
        AppEntity entity = getExistingEntityInScope(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(entity);
        appMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.APP, id, entity.getName(),
                status == AppStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.APP)
                .bizId(id)
                .operationType(status == AppStatus.ENABLED ? OperationType.ENABLE : OperationType.DISABLE)
                .entityVersion(entity.getVersion())
                .operator(entity.getUpdateBy())
                .occurredAt(LocalDateTime.now())
                .build());
        return getById(id);
    }

    /**
     * 校验应用编码在未删除的应用中是否唯一。
     *
     * @param code      待校验的应用编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getCode, code)
                .ne(AppEntity::getStatus, AppStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(AppEntity::getId, excludeId);
        }
        Long count = appMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("应用编码[" + code + "]已存在");
        }
    }

    /**
     * 对非锁定（{@code locked=false}）且适用于当前场景的字段定义执行必填、正则、
     * 唯一性校验（design.md Decision 9）。{@code name}/{@code code} 属于承重字段，
     * 已被 {@link FormFieldDefinitionService#listActiveByBizType} 排除，不受本方法
     * 影响，继续依赖上面既有的 {@code checkCodeUnique}/Bean Validation。
     *
     * @param request   创建或更新请求，按 {@code fieldCode} 反射读取字段值
     * @param creating  是否为新增场景
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 {@code null}
     */
    private void validateDynamicFields(Object request, boolean creating, Long excludeId) {
        List<FormFieldDefinitionVO> definitions =
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.APP);
        DynamicFieldValidator.validate(definitions, request, creating, (column, value) -> {
            if (!ALLOWED_DYNAMIC_COLUMNS.contains(column)) {
                throw new BusinessException("非法的动态字段列名：" + column);
            }
            return appMapper.countByColumnValue(column, value, excludeId);
        });
    }

    /**
     * 查询一个未被逻辑删除的应用，不存在时抛出业务异常。
     *
     * @param id 应用 id
     * @return 应用实体
     */
    private AppEntity getExistingEntity(Long id) {
        AppEntity entity = appMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), AppStatus.DELETED)) {
            throw new BusinessException("应用不存在");
        }
        return entity;
    }

    /**
     * 查询一个未被逻辑删除、且当前所属组织落在当前登录用户管辖组织范围内的应用，供
     * {@code update}/{@code changeStatus}/{@code delete} 等写操作复用（org-scope-write-guard
     * change design.md Decision 3）。管辖范围不受限时行为等同于 {@link #getExistingEntity}；
     * 受限且该应用所属组织不在允许集合内时，复用"应用不存在"错误文案而不是单独的"无权限"
     * 提示，避免暴露"该 id 存在但你无权限"这一越权探测信号（design.md Decision 2）。
     *
     * @param id 应用 id
     * @return 应用实体
     */
    private AppEntity getExistingEntityInScope(Long id) {
        AppEntity entity = getExistingEntity(id);
        if (!orgScopeService.isOrgIdAllowed(CurrentUserContext.getUserId(), entity.getOrgId())) {
            throw new BusinessException("应用不存在");
        }
        return entity;
    }

    /**
     * 校验新增/移动应用时指定的所属组织 id 是否落在当前登录用户的管辖组织范围内，
     * 不受限时恒放行；受限且不在允许集合内时直接拒绝，因为校验对象是"要挂到哪个组织下"
     * 而非某条具体记录，不需要伪装成"不存在"（org-scope-write-guard change design.md
     * Decision 2）。
     *
     * @param orgId 所属组织 id
     */
    private void assertOrgInScope(Long orgId) {
        if (!orgScopeService.isOrgIdAllowed(CurrentUserContext.getUserId(), orgId)) {
            throw new BusinessException("无权限在管辖范围之外的组织下操作");
        }
    }

    /**
     * 把应用实体列表转换为视图对象列表，并批量解析负责人姓名、所属组织名称与
     * 创建人/更新人展示名，避免逐条查询用户表/组织表。
     *
     * @param entities 应用实体列表
     * @return 应用视图对象列表
     */
    private List<AppVO> toVOListWithNames(List<AppEntity> entities) {
        List<AppVO> result = AppConvert.INSTANCE.toVOList(entities);
        if (result.isEmpty()) {
            return result;
        }

        List<Long> ownerIds = entities.stream().map(AppEntity::getOwnerId).distinct().toList();
        Map<Long, String> ownerNameMap;
        if (ownerIds.isEmpty()) {
            ownerNameMap = Map.of();
        } else {
            List<UserEntity> owners = userMapper.selectList(
                    new LambdaQueryWrapper<UserEntity>().in(UserEntity::getId, ownerIds));
            ownerNameMap = owners.stream()
                    .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName, (left, right) -> left));
        }

        List<Long> orgIds = entities.stream().map(AppEntity::getOrgId).distinct().toList();
        Map<Long, String> orgNameMap;
        if (orgIds.isEmpty()) {
            orgNameMap = Map.of();
        } else {
            List<OrgEntity> orgs = orgMapper.selectList(
                    new LambdaQueryWrapper<OrgEntity>().in(OrgEntity::getId, orgIds));
            orgNameMap = orgs.stream()
                    .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getName, (left, right) -> left));
        }

        Set<String> auditUserIdTexts = entities.stream()
                .flatMap(entity -> Stream.of(entity.getCreateBy(), entity.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);

        for (int i = 0; i < entities.size(); i++) {
            AppVO vo = result.get(i);
            vo.setOwnerName(ownerNameMap.get(vo.getOwnerId()));
            vo.setOrgName(orgNameMap.get(vo.getOrgId()));
            vo.setCreateBy(resolveDisplayName(entities.get(i).getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(entities.get(i).getUpdateBy(), displayNames));
        }
        return result;
    }

    /**
     * 把审计字段原始的用户 id 文本解析为人可读展示名，查不到时降级为"未知用户"，
     * 避免把裸的用户 id/账号编码直接透出给前端。
     *
     * @param userIdText   审计字段原始值（用户 id 文本）
     * @param displayNames 批量解析得到的用户 id 文本到展示名的映射
     * @return 人可读展示名，原始值为空白时返回空字符串
     */
    private String resolveDisplayName(String userIdText, Map<String, String> displayNames) {
        if (!StringUtils.hasText(userIdText)) {
            return "";
        }
        return displayNames.getOrDefault(userIdText, "未知用户");
    }

    /**
     * 构造应用实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值；
     * 负责人姓名、所属组织名称需分别按 {@code ownerId}/{@code orgId} 回查一次；末尾追加
     * 当前启用的 {@code ext1}..{@code ext10} 扩展字段（key 使用字段定义的展示名）。
     *
     * @param entity 应用实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(AppEntity entity) {
        UserEntity owner = entity.getOwnerId() != null ? userMapper.selectById(entity.getOwnerId()) : null;
        OrgEntity org = entity.getOrgId() != null ? orgMapper.selectById(entity.getOrgId()) : null;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("应用名称", entity.getName());
        snapshot.put("应用编码", entity.getCode());
        snapshot.put("负责人", owner != null ? owner.getName() : null);
        snapshot.put("所属组织", org != null ? org.getName() : null);
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));

        List<FormFieldDefinitionVO> definitions =
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.APP);
        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, definitions, extValues(entity));
        return snapshot;
    }

    /**
     * 把应用实体的 {@code ext1}..{@code ext10} 逐一收集为列名到当前值的映射，
     * 供 {@link FormFieldSnapshotSupport#appendExtFieldSnapshot} 使用。
     *
     * @param entity 应用实体
     * @return {@code ext1}..{@code ext10} 列名到当前值的映射
     */
    private Map<String, String> extValues(AppEntity entity) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ext1", entity.getExt1());
        values.put("ext2", entity.getExt2());
        values.put("ext3", entity.getExt3());
        values.put("ext4", entity.getExt4());
        values.put("ext5", entity.getExt5());
        values.put("ext6", entity.getExt6());
        values.put("ext7", entity.getExt7());
        values.put("ext8", entity.getExt8());
        values.put("ext9", entity.getExt9());
        values.put("ext10", entity.getExt10());
        return values;
    }

    /**
     * 把应用状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, AppStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, AppStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
