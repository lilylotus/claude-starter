package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.event.DomainEventPublisher;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.mapstruct.PositionConvert;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserDisplayService;
import cn.nihility.rbac.user.service.support.PositionDynamicFieldSupport;
import cn.nihility.rbac.user.service.support.PositionLogSnapshotSupport;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 任职管理业务逻辑实现，复用用户管理模块既有的 {@code tab_user_position} 表/实体/Mapper，
 * 以组织为导航维度提供独立的查询与维护能力，不影响用户管理内嵌任职子表单的既有行为。
 */
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    /** 用户任职记录数据访问接口。 */
    private final UserPositionMapper userPositionMapper;

    /**
     * 组织数据访问接口，用于在任职记录所属组织变更时读取新旧组织当前的 {@code orgPath}，
     * 作为同步事件的 {@code orgScopePathBefore}/{@code orgScopePathAfter}
     * （app-sync-changelog-pull change design.md Decision 3）。
     */
    private final OrgMapper orgMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@code bizType=POSITION} 动态字段（必填/正则/唯一性）校验的共享组件，与用户管理
     * 内嵌任职子表单（{@code UserServiceImpl}）共用同一份校验逻辑。
     */
    private final PositionDynamicFieldSupport positionDynamicFieldSupport;

    /**
     * 任职记录操作日志的被操作对象名称快照与字段快照共享组件，与用户管理内嵌任职子表单
     * （{@code UserServiceImpl}，{@code syncPositions}）共用同一份快照逻辑。
     */
    private final PositionLogSnapshotSupport positionLogSnapshotSupport;

    /**
     * 管辖组织范围解析业务逻辑接口，用于按当前登录用户的管辖组织范围过滤任职列表
     * （org-scope-data-permission change design.md Decision 5）。
     */
    private final OrgScopeService orgScopeService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 审计字段（{@code createBy}/{@code updateBy}）展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * 数据变更事件发布抽象，任职数据新增/编辑/启用/停用/删除写操作成功后紧邻
     * {@code operationLogRecorder} 调用之后发布一次同步事件
     * （app-sync-notify-pull-api change design.md Decision 1/5）。
     */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * {@inheritDoc}
     * <p>
     * {@code selectPositionPage} 直接由 SQL JOIN 映射到 {@link PositionVO}，
     * {@code createBy}/{@code updateBy} 是登录用户 id 的原始字符串，需要就地批量回填为展示名。
     */
    @Override
    public PageResult<PositionVO> getPage(Long orgId, Integer page, Integer pageSize) {
        if (orgId == null) {
            throw new BusinessException("所属组织不能为空");
        }

        // 受限且请求的 orgId 不在允许集合内时，直接返回空分页而不是报错：这个接口的既有
        // 语义是"某个组织下没有任职记录时也是返回空分页，不是报错"，管辖范围之外的组织
        // 对当前调用者而言观感上应该和"这个组织下没有任职记录"一致，不额外暴露"这个 orgId
        // 存在但你无权查看"这种更具体的越权探测信号（org-scope-data-permission change
        // design.md Decision 5）。
        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        if (allowedOrgIds.isPresent() && !allowedOrgIds.get().contains(orgId)) {
            return new PageResult<>(List.of(), 0L, page, pageSize);
        }

        IPage<PositionVO> resultPage = userPositionMapper.selectPositionPage(
                new Page<>(page, pageSize), orgId, PositionStatus.DELETED);
        List<PositionVO> records = resultPage.getRecords();
        fillAuditDisplayNames(records);
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code selectPositionDetail} 直接由 SQL JOIN 映射到 {@link PositionVO}，
     * {@code createBy}/{@code updateBy} 是登录用户 id 的原始字符串，需要就地回填为展示名。
     */
    @Override
    public PositionVO getById(Long id) {
        PositionVO vo = userPositionMapper.selectPositionDetail(id, PositionStatus.DELETED);
        if (vo == null) {
            throw new BusinessException("任职记录不存在");
        }
        fillAuditDisplayNames(List.of(vo));
        return vo;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 复用 {@link UserPositionMapper#selectAllForExport} 按管辖组织范围过滤，不分页
     * （master-data-excel-export change design.md Decision 1）。
     */
    @Override
    public List<PositionVO> listAllForExport() {
        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        List<PositionVO> records =
                userPositionMapper.selectAllForExport(allowedOrgIds.orElse(null), PositionStatus.DELETED);
        fillAuditDisplayNames(records);
        return records;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> findActiveUserIdsByPositionType(String positionType) {
        if (!StringUtils.hasText(positionType)) {
            return Set.of();
        }
        List<UserPositionEntity> positions = userPositionMapper.selectList(
                new LambdaQueryWrapper<UserPositionEntity>()
                        .eq(UserPositionEntity::getPositionType, positionType)
                        .eq(UserPositionEntity::getStatus, PositionStatus.ENABLED));
        return positions.stream().map(UserPositionEntity::getUserId).collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO create(PositionCreateRequest request) {
        assertOrgInScope(request.getOrgId());
        positionDynamicFieldSupport.validate(request, true, null);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        UserPositionEntity entity = PositionConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(PositionStatus.ENABLED);
        entity.setVersion(1L);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        userPositionMapper.insert(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.POSITION, entity.getId(),
                positionLogSnapshotSupport.targetName(entity), positionLogSnapshotSupport.snapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.POSITION)
                .bizId(entity.getId())
                .operationType(OperationType.CREATE)
                .operator(entity.getCreateBy())
                .entityVersion(entity.getVersion())
                .orgScopePathBefore(null)
                .orgScopePathAfter(resolveOrgPath(entity.getOrgId()))
                .occurredAt(LocalDateTime.now())
                .build());

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     * <p>
     * 所属组织（{@code orgId}）变更时，写入前先读取旧组织当前的 {@code orgPath} 作为事件的
     * {@code orgScopePathBefore}，写入后用新组织的 {@code orgPath} 作为
     * {@code orgScopePathAfter}；未变更所属组织时前后路径相同（app-sync-changelog-pull
     * change design.md Decision 3）。
     */
    @Override
    public PositionVO update(Long id, PositionUpdateRequest request) {
        UserPositionEntity entity = getExistingEntityInScope(id);
        assertOrgInScope(request.getOrgId());
        positionDynamicFieldSupport.validate(request, false, id);
        Map<String, Object> beforeSnapshot = positionLogSnapshotSupport.snapshot(entity);

        boolean orgChanged = !Objects.equals(request.getOrgId(), entity.getOrgId());
        String previousOrgPath = orgChanged ? resolveOrgPath(entity.getOrgId()) : null;

        PositionConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);
        userPositionMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        String currentOrgPath = resolveOrgPath(entity.getOrgId());
        operationLogRecorder.recordUpdate(OperationLogResourceType.POSITION, id,
                positionLogSnapshotSupport.targetName(entity), beforeSnapshot,
                positionLogSnapshotSupport.snapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.POSITION)
                .bizId(id)
                .operationType(OperationType.UPDATE)
                .operator(entity.getUpdateBy())
                .entityVersion(entity.getVersion())
                .orgScopePathBefore(orgChanged ? previousOrgPath : currentOrgPath)
                .orgScopePathAfter(currentOrgPath)
                .occurredAt(LocalDateTime.now())
                .build());

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO enable(Long id) {
        return changeStatus(id, PositionStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO disable(Long id) {
        return changeStatus(id, PositionStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        UserPositionEntity entity = getExistingEntityInScope(id);
        Map<String, Object> beforeSnapshot = positionLogSnapshotSupport.snapshot(entity);
        String deletedOrgPath = resolveOrgPath(entity.getOrgId());

        entity.setStatus(PositionStatus.DELETED);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);
        userPositionMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        operationLogRecorder.recordDelete(OperationLogResourceType.POSITION, id,
                positionLogSnapshotSupport.targetName(entity), beforeSnapshot);
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.POSITION)
                .bizId(id)
                .operationType(OperationType.DELETE)
                .operator(entity.getUpdateBy())
                .entityVersion(entity.getVersion())
                .orgScopePathBefore(deletedOrgPath)
                .orgScopePathAfter(null)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /**
     * 变更任职记录状态（启用/停用）并返回更新后的详情。
     *
     * @param id     任职记录 id
     * @param status 目标状态
     * @return 更新后的任职记录详情
     */
    private PositionVO changeStatus(Long id, int status) {
        UserPositionEntity entity = getExistingEntityInScope(id);
        Map<String, Object> beforeSnapshot = positionLogSnapshotSupport.snapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);
        userPositionMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        String orgPath = resolveOrgPath(entity.getOrgId());
        operationLogRecorder.recordStatusChange(OperationLogResourceType.POSITION, id,
                positionLogSnapshotSupport.targetName(entity), status == PositionStatus.ENABLED, beforeSnapshot,
                positionLogSnapshotSupport.snapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.POSITION)
                .bizId(id)
                .operationType(status == PositionStatus.ENABLED ? OperationType.ENABLE : OperationType.DISABLE)
                .operator(entity.getUpdateBy())
                .entityVersion(entity.getVersion())
                .orgScopePathBefore(orgPath)
                .orgScopePathAfter(orgPath)
                .occurredAt(LocalDateTime.now())
                .build());
        return getById(id);
    }

    /**
     * 按组织 id 查询其当前的 {@code orgPath}，供任职记录同步事件的
     * {@code orgScopePathBefore}/{@code orgScopePathAfter} 使用（design.md Decision 3）。
     *
     * @param orgId 组织 id，可为 {@code null}
     * @return 组织当前的 {@code orgPath}，{@code orgId} 为空或组织不存在时返回 {@code null}
     */
    private String resolveOrgPath(Long orgId) {
        if (orgId == null) {
            return null;
        }
        OrgEntity org = orgMapper.selectById(orgId);
        return org != null ? org.getOrgPath() : null;
    }

    /**
     * 查询一个未被逻辑删除的任职记录，不存在时抛出业务异常。
     *
     * @param id 任职记录 id
     * @return 任职记录实体
     */
    private UserPositionEntity getExistingEntity(Long id) {
        UserPositionEntity entity = userPositionMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), PositionStatus.DELETED)) {
            throw new BusinessException("任职记录不存在");
        }
        return entity;
    }

    /**
     * 查询一个未被逻辑删除、且当前所属组织落在当前登录用户管辖组织范围内的任职记录，供
     * {@code update}/{@code changeStatus}/{@code delete} 等写操作复用（org-scope-write-guard
     * change design.md Decision 3）。管辖范围不受限时行为等同于 {@link #getExistingEntity}；
     * 受限且该记录所属组织不在允许集合内时，复用"任职记录不存在"错误文案而不是单独的
     * "无权限"提示，避免暴露"该 id 存在但你无权限"这一越权探测信号（design.md Decision 2）。
     *
     * @param id 任职记录 id
     * @return 任职记录实体
     */
    private UserPositionEntity getExistingEntityInScope(Long id) {
        UserPositionEntity entity = getExistingEntity(id);
        if (!orgScopeService.isOrgIdAllowed(CurrentUserContext.getUserId(), entity.getOrgId())) {
            throw new BusinessException("任职记录不存在");
        }
        return entity;
    }

    /**
     * 校验新增/移动任职记录时指定的所属组织 id 是否落在当前登录用户的管辖组织范围内，
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
     * 批量把一批任职记录视图对象自身携带的 {@code createBy}/{@code updateBy}（{@code selectPositionPage}/
     * {@code selectPositionDetail} 直接由 SQL JOIN 映射得到，原始内容是登录用户 id 的字符串）
     * 就地回填为"姓名（账号编码）"展示名，避免逐条查询造成 N+1。
     *
     * @param voList 任职记录视图对象列表，原地修改
     */
    private void fillAuditDisplayNames(List<PositionVO> voList) {
        if (voList.isEmpty()) {
            return;
        }
        Set<String> auditUserIdTexts = voList.stream()
                .flatMap(vo -> Stream.of(vo.getCreateBy(), vo.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);
        for (PositionVO vo : voList) {
            vo.setCreateBy(resolveDisplayName(vo.getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(vo.getUpdateBy(), displayNames));
        }
    }

    /**
     * 把审计字段原始值（登录用户 id 的字符串）解析为展示名；空白值回退空字符串，
     * 查不到对应用户（如账号已被物理清理）时回退"未知用户"文案。
     *
     * @param userIdText   审计字段原始值
     * @param displayNames 批量解析得到的"用户 id 文本 -> 展示名"映射
     * @return 展示名
     */
    private String resolveDisplayName(String userIdText, Map<String, String> displayNames) {
        if (!StringUtils.hasText(userIdText)) {
            return "";
        }
        return displayNames.getOrDefault(userIdText, "未知用户");
    }
}
