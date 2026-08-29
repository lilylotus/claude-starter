package cn.nihility.rbac.org.service.impl;

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
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgPathVersionRow;
import cn.nihility.rbac.org.dto.OrgTreeNodeVO;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.org.mapstruct.OrgConvert;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.event.DomainEventPublisher;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 组织机构业务逻辑实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgServiceImpl implements OrgService {

    /** 顶级组织的上级 id。 */
    private static final long ROOT_PARENT_ID = 0L;

    /**
     * {@code bizType=ORG} 下允许被动态字段唯一性校验拼进 {@code ${column}} 的列名
     * 白名单，取自 {@code tab_metadata_field} 目录里 ORG 的原有可配置列 +
     * {@code ext1}..{@code ext10}（design.md Decision 3/8）；{@code name}/
     * {@code code} 虽然也在目录中，但属于承重字段，不会经过这条动态校验管线，
     * 列入白名单只是双重防护，不影响实际调用路径。
     */
    private static final Set<String> ALLOWED_DYNAMIC_COLUMNS = Set.of(
            "name", "code", "show_order", "remark",
            "ext1", "ext2", "ext3", "ext4", "ext5", "ext6", "ext7", "ext8", "ext9", "ext10");

    /** 组织数据访问接口。 */
    private final OrgMapper orgMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 表单字段定义业务逻辑接口，用于驱动非锁定字段的必填/正则/唯一性校验。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /** 操作日志扩展字段快照填充组件，负责把字典类扩展字段的存储编码解析为标签。 */
    private final FormFieldSnapshotSupport formFieldSnapshotSupport;

    /**
     * 管辖组织范围解析业务逻辑接口，用于按当前登录用户的管辖组织范围过滤组织树/列表
     * （org-scope-data-permission change design.md Decision 4）。
     */
    private final OrgScopeService orgScopeService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 审计字段（{@code createBy}/{@code updateBy}）展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * 数据变更事件发布抽象，组织数据新增/编辑/启用/停用/删除写操作成功后紧邻
     * {@code operationLogRecorder} 调用之后发布一次同步事件
     * （app-sync-notify-pull-api change design.md Decision 5）。
     */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OrgTreeNodeVO> getTree() {
        List<OrgEntity> entities = listAllUndeletedOrdered();

        // 受限时先按"id 在允许集合内"过滤，再用下面完全不变的既有树组装算法组装：过滤后的
        // 实体列表里，某个允许节点的真实上级组织 id 可能已经不在过滤后的 nodeMap 里，
        // 导致该节点在原算法眼里"找不到父节点"从而被收进 roots——这就是"虚拟根节点"效果，
        // 是过滤 + 复用原算法的自然结果，不要在这里另写"虚拟根节点"的特判分支
        // （org-scope-data-permission change design.md Decision 4）。
        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        if (allowedOrgIds.isPresent()) {
            Set<Long> allowed = allowedOrgIds.get();
            entities = entities.stream().filter(entity -> allowed.contains(entity.getId())).toList();
        }

        Map<Long, OrgTreeNodeVO> nodeMap = new LinkedHashMap<>();
        for (OrgEntity entity : entities) {
            nodeMap.put(entity.getId(), OrgConvert.INSTANCE.toTreeNode(entity));
        }

        List<OrgTreeNodeVO> roots = new ArrayList<>();
        for (OrgEntity entity : entities) {
            OrgTreeNodeVO node = nodeMap.get(entity.getId());
            OrgTreeNodeVO parentNode = nodeMap.get(entity.getParentId());
            if (parentNode != null) {
                parentNode.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<OrgVO> getChildren(Long parentId, Integer page, Integer pageSize) {
        long effectiveParentId = parentId != null ? parentId : ROOT_PARENT_ID;

        // 不受限时行为完全不变：继续用 orgMapper.selectPage 做数据库侧分页，分页元信息
        // 直接取自其 IPage 返回值。受限时过滤发生在应用层（见 queryChildrenRespectingScope），
        // 不能再依赖 selectPage 的 IPage 元信息（那是过滤前的总数），需要基于过滤后的列表
        // 手工计算分页（org-scope-data-permission change design.md Decision 4 / tasks 4.4）。
        if (orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId()).isEmpty()) {
            Page<OrgEntity> queryPage = new Page<>(page, pageSize);
            Page<OrgEntity> resultPage = orgMapper.selectPage(queryPage, childrenQueryWrapper(effectiveParentId));
            List<OrgVO> records = toVOListWithParentName(resultPage.getRecords());
            return PageResult.of(records, resultPage);
        }

        List<OrgEntity> filtered = queryChildrenRespectingScope(effectiveParentId);
        return paginateFiltered(filtered, page, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OrgTreeNodeVO> getChildrenTreeNodes(Long parentId) {
        long effectiveParentId = parentId != null ? parentId : ROOT_PARENT_ID;
        List<OrgEntity> entities = queryChildrenRespectingScope(effectiveParentId);

        List<OrgTreeNodeVO> nodes = new ArrayList<>();
        for (OrgEntity entity : entities) {
            OrgTreeNodeVO node = OrgConvert.INSTANCE.toTreeNode(entity);
            node.setChildren(new ArrayList<>());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO getById(Long id) {
        OrgEntity entity = getExistingEntity(id);
        return toVOListWithParentName(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 写入组织记录与解析上级组织编码需在同一事务内完成，因此整个方法标注
     * {@link Transactional}（org-add-parent-code change design.md Decision 4）。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public OrgVO create(OrgCreateRequest request) {
        assertParentOrgInScope(request.getParentId());
        checkCodeUnique(request.getCode(), null);
        validateDynamicFields(request, true, null);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        OrgEntity entity = OrgConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(OrgStatus.ENABLED);
        entity.setVersion(1L);
        OrgEntity parent = resolveParent(request.getParentId());
        entity.setParentCode(parent == null ? null : parent.getCode());
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        orgMapper.insert(entity);
        entity.setOrgParentPath(parent == null ? null : parent.getOrgPath());
        entity.setOrgPath(joinPath(entity.getOrgParentPath(), entity.getId().toString()));
        entity.setOrgNamePath(joinPath(parent == null ? null : parent.getOrgNamePath(), entity.getName()));
        orgMapper.updateById(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.ORG, entity.getId(), entity.getName(),
                toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.ORG)
                .bizId(entity.getId())
                .operationType(OperationType.CREATE)
                .operator(entity.getCreateBy())
                .entityVersion(entity.getVersion())
                .orgScopePathBefore(null)
                .orgScopePathAfter(entity.getOrgPath())
                .occurredAt(LocalDateTime.now())
                .build());

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     * <p>
     * 主记录写入与"变更上级组织时重新解析 parentCode""自身 code 变更时级联更新直属
     * 子组织 parentCode"需在同一事务内完成，因此整个方法标注 {@link Transactional}
     * （org-add-parent-code change design.md Decision 2/4，Risks/Trade-offs）。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public OrgVO update(Long id, OrgUpdateRequest request) {
        OrgEntity entity = getExistingEntityInScope(id);
        checkCodeUnique(request.getCode(), id);
        if (Objects.equals(request.getParentId(), id)) {
            throw new BusinessException("上级组织不能是自身");
        }
        // 只在上级组织真的发生变化时才校验新 parentId 是否在管辖范围内：被编辑组织自身的
        // id 已经在 getExistingEntityInScope 校验过，但它的真实上级组织完全可能不在管辖
        // 范围内（管辖范围的"虚拟根节点"场景），不修改上级组织的编辑不应因此被拒绝
        // （org-scope-write-guard change design.md Decision 5）
        if (!Objects.equals(request.getParentId(), entity.getParentId())) {
            assertParentOrgInScope(request.getParentId());
        }
        validateDynamicFields(request, false, id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        Long previousParentId = entity.getParentId();
        String previousCode = entity.getCode();
        String previousName = entity.getName();
        String previousOrgPath = entity.getOrgPath();
        String previousOrgNamePath = entity.getOrgNamePath();
        boolean parentChanged = !Objects.equals(request.getParentId(), previousParentId);
        OrgEntity newParent = parentChanged ? resolveParent(request.getParentId()) : null;
        if (newParent != null && (newParent.getOrgPath().equals(previousOrgPath)
                || newParent.getOrgPath().startsWith(previousOrgPath + "/"))) {
            throw new BusinessException("上级组织不能是当前组织的下级组织");
        }

        // 上级组织变化前，先按旧路径前缀采集自身与全部子孙组织当前的 id/路径/版本快照，
        // 供级联更新完成后计算每个受影响组织各自的 orgScopePathBefore/entityVersion
        // （app-sync-changelog-pull change design.md Decision 2）。必须在任何 UPDATE 语句
        // 执行之前查询，否则采集到的就不是"旧"路径了。
        List<OrgPathVersionRow> affectedBefore = parentChanged
                ? orgMapper.selectPathAndVersionByPrefix(previousOrgPath)
                : List.of();

        OrgConvert.INSTANCE.updateEntity(request, entity);
        if (parentChanged) {
            entity.setParentCode(newParent == null ? null : newParent.getCode());
        }
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(entity);

        LocalDateTime pathUpdateTime = entity.getUpdateTime();
        String operator = entity.getUpdateBy();
        if (parentChanged || !Objects.equals(previousName, entity.getName())) {
            String parentNamePath = newParent == null ? null : newParent.getOrgNamePath();
            String newNamePath = joinPath(parentChanged ? parentNamePath
                    : parentNamePath(previousOrgNamePath), entity.getName());
            orgMapper.cascadeUpdateOrgNamePath(previousOrgNamePath, newNamePath, operator, pathUpdateTime);
            entity.setOrgNamePath(newNamePath);
        }
        if (parentChanged) {
            // cascadeUpdateOrgPath 已经把自身与全部子孙组织的 version 一并原子递增 1，
            // 不能再对自身重复调用 incrementVersion，否则自身版本会多加 1 次。
            String newParentPath = newParent == null ? null : newParent.getOrgPath();
            String newOrgPath = joinPath(newParentPath, id.toString());
            orgMapper.cascadeUpdateOrgPath(previousOrgPath, newOrgPath, operator, pathUpdateTime);
            entity.setOrgParentPath(newParentPath);
            entity.setOrgPath(newOrgPath);
        } else {
            orgMapper.incrementVersion(id);
        }
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        if (!Objects.equals(previousCode, entity.getCode())) {
            orgMapper.updateChildrenParentCode(id, entity.getCode());
        }

        operationLogRecorder.recordUpdate(OperationLogResourceType.ORG, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));
        publishUpdateEvents(id, entity, parentChanged, affectedBefore);

        return getById(id);
    }

    /**
     * 上级组织未变化时，为自身发布一条 {@code before=after=当前路径} 的 UPDATE 事件；
     * 上级组织已变化时，按级联更新前采集的快照逐一查询级联更新后的新路径/新版本，为自身与
     * 全部子孙组织各发布一条携带正确前后路径与递增后版本的 UPDATE 事件，避免子孙组织因为
     * "没人直接操作它"而漏发事件（design.md Decision 2）。
     *
     * @param id             被直接编辑的组织 id
     * @param entity         已完成本次更新后的组织实体（{@code parentChanged=false} 时使用其
     *                       {@code version}/{@code orgPath}）
     * @param parentChanged  本次更新是否变更了上级组织
     * @param affectedBefore 上级组织变化前采集的自身与全部子孙组织 id/旧路径/旧版本快照
     */
    private void publishUpdateEvents(Long id, OrgEntity entity, boolean parentChanged,
            List<OrgPathVersionRow> affectedBefore) {
        if (!parentChanged) {
            domainEventPublisher.publish(DomainChangeEvent.builder()
                    .dataType(SyncDomain.ORG)
                    .bizId(id)
                    .operationType(OperationType.UPDATE)
                    .operator(entity.getUpdateBy())
                    .entityVersion(entity.getVersion())
                    .orgScopePathBefore(entity.getOrgPath())
                    .orgScopePathAfter(entity.getOrgPath())
                    .occurredAt(LocalDateTime.now())
                    .build());
            return;
        }

        List<OrgPathVersionRow> affectedAfter = orgMapper.selectPathAndVersionByPrefix(entity.getOrgPath());
        Map<Long, OrgPathVersionRow> afterById = affectedAfter.stream()
                .collect(Collectors.toMap(OrgPathVersionRow::getId, row -> row, (left, right) -> left));
        for (OrgPathVersionRow before : affectedBefore) {
            OrgPathVersionRow after = afterById.get(before.getId());
            if (after == null) {
                // 级联更新只改路径/版本，不改变行数，理论上不会发生；出现说明级联更新与本次
                // 查询之间发生了并发变更，记录日志并跳过，不让个别子孙的异常阻断整个事务。
                log.warn("组织[{}]级联迁移后查不到子孙组织[{}]的新路径，跳过该条变更事件", id, before.getId());
                continue;
            }
            domainEventPublisher.publish(DomainChangeEvent.builder()
                    .dataType(SyncDomain.ORG)
                    .bizId(before.getId())
                    .operationType(OperationType.UPDATE)
                    .operator(entity.getUpdateBy())
                    .entityVersion(after.getVersion())
                    .orgScopePathBefore(before.getOrgPath())
                    .orgScopePathAfter(after.getOrgPath())
                    .occurredAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * 按上级组织 id 解析其当前的组织编码，供创建/变更上级组织时回填 {@code parentCode}
     * 使用；{@code parentId} 为 0（顶级组织）时返回 {@code null}（org-add-parent-code
     * change design.md Decision 3/4）。
     *
     * @param parentId 上级组织 id
     * @return 上级组织当前的编码，顶级组织或父组织不存在时为 {@code null}
     */
    private String resolveParentCode(Long parentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return null;
        }
        return Optional.ofNullable(orgMapper.selectById(parentId)).map(OrgEntity::getCode).orElse(null);
    }

    /** 查询路径维护所需的父组织；顶级组织返回 {@code null}。 */
    private OrgEntity resolveParent(Long parentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return null;
        }
        return Optional.ofNullable(orgMapper.selectById(parentId))
                .orElseThrow(() -> new BusinessException("上级组织不存在"));
    }

    /** 拼接物化路径，父路径为空时直接返回当前节点值。 */
    private String joinPath(String parentPath, String currentValue) {
        return parentPath == null || parentPath.isBlank() ? currentValue : parentPath + "/" + currentValue;
    }

    /** 截取名称路径中的父路径。 */
    private String parentNamePath(String orgNamePath) {
        int separatorIndex = orgNamePath == null ? -1 : orgNamePath.lastIndexOf('/');
        return separatorIndex < 0 ? null : orgNamePath.substring(0, separatorIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO enable(Long id) {
        return changeStatus(id, OrgStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO disable(Long id) {
        return changeStatus(id, OrgStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        OrgEntity entity = getExistingEntityInScope(id);

        // 存在未删除的下级组织时直接拒绝，组织删除不做级联删除子孙，因此不存在"子孙组织
        // 需要各自产生 tombstone 事件"的场景，delete 只需为自身发布一条 tombstone
        // （app-sync-changelog-pull change design.md Decision 3）。
        Long childCount = orgMapper.selectCount(new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getParentId, id)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该组织下存在未删除的下级组织，无法删除");
        }

        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);
        String deletedOrgPath = entity.getOrgPath();
        entity.setStatus(OrgStatus.DELETED);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(entity);
        orgMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        operationLogRecorder.recordDelete(OperationLogResourceType.ORG, id, entity.getName(), beforeSnapshot);
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.ORG)
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
     * {@inheritDoc}
     * <p>
     * 按 id 升序查询全部未删除组织，受限时按 {@code allowedOrgIds} 过滤，不做树形组装
     * （master-data-excel-export change design.md Decision 1）。
     */
    @Override
    public List<OrgVO> listAllForExport() {
        List<OrgEntity> entities = orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                .ne(OrgEntity::getStatus, OrgStatus.DELETED)
                .orderByAsc(OrgEntity::getId));

        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        if (allowedOrgIds.isPresent()) {
            Set<Long> allowed = allowedOrgIds.get();
            entities = entities.stream().filter(entity -> allowed.contains(entity.getId())).toList();
        }
        return toVOListWithParentName(entities);
    }

    /**
     * 变更组织状态（启用/停用）并返回更新后的详情。
     *
     * @param id     组织 id
     * @param status 目标状态
     * @return 更新后的组织详情
     */
    private OrgVO changeStatus(Long id, int status) {
        OrgEntity entity = getExistingEntityInScope(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(entity);
        orgMapper.incrementVersion(id);
        entity.setVersion(entity.getVersion() == null ? 2L : entity.getVersion() + 1L);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.ORG, id, entity.getName(),
                status == OrgStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.ORG)
                .bizId(id)
                .operationType(status == OrgStatus.ENABLED ? OperationType.ENABLE : OperationType.DISABLE)
                .operator(entity.getUpdateBy())
                .entityVersion(entity.getVersion())
                .orgScopePathBefore(entity.getOrgPath())
                .orgScopePathAfter(entity.getOrgPath())
                .occurredAt(LocalDateTime.now())
                .build());
        return getById(id);
    }

    /**
     * 查询全部未删除的组织，按显示序号降序、id 升序排列。
     *
     * @return 未删除的组织实体列表
     */
    private List<OrgEntity> listAllUndeletedOrdered() {
        return orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                .ne(OrgEntity::getStatus, OrgStatus.DELETED)
                .orderByDesc(OrgEntity::getShowOrder)
                .orderByAsc(OrgEntity::getId));
    }

    /**
     * 构造查询某个上级组织下未删除直属子组织的条件，按显示序号降序、id 升序排列。
     * 供分页查询（{@link #getChildren}）与树懒加载查询（{@link #getChildrenTreeNodes}）共用。
     *
     * @param parentId 上级组织 id
     * @return 查询条件
     */
    private LambdaQueryWrapper<OrgEntity> childrenQueryWrapper(long parentId) {
        return new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getParentId, parentId)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED)
                .orderByDesc(OrgEntity::getShowOrder)
                .orderByAsc(OrgEntity::getId);
    }

    /**
     * 按当前用户管辖组织范围查询某个上级组织的全部直属子组织实体，供 {@link #getChildren}
     * 与 {@link #getChildrenTreeNodes} 共用（org-scope-data-permission change design.md
     * Decision 4）。不受限时行为完全不变，直接走原有 {@link #childrenQueryWrapper} 查询；
     * 受限时：
     * <ul>
     * <li>{@code effectiveParentId == 0}（顶层查询）：不能再简单按 {@code parentId = 0} 查——
     * 管辖范围中间层节点的真实 {@code parentId} 不是 0，直接查会漏掉它，改为在全部未删除组织中
     * 过滤出"id 在允许集合内，且真实 parentId 不在允许集合内"的节点，这正是"虚拟根节点"
     * 的定义（自身可见，但上级不可见，所以对当前调用者而言它就是顶层）。</li>
     * <li>{@code effectiveParentId != 0}：若该 id 本身不在允许集合内，直接返回空列表
     * （不区分"该 id 不存在"和"存在但不在管辖范围内"，避免用错误信息反向确认某个 org id
     * 是否存在）；若在允许集合内，按原查询条件查询后再按允许集合过滤一次（正常情况下这层
     * 过滤是多余的——include_children 展开已保证任何允许节点的直属子节点也在允许集合里——
     * 但作为防御性兜底保留）。</li>
     * </ul>
     *
     * @param effectiveParentId 上级组织 id，0 表示顶层
     * @return 直属子组织实体列表（受限顶层查询时可能是不同真实 parentId 的"虚拟根节点"集合）
     */
    private List<OrgEntity> queryChildrenRespectingScope(long effectiveParentId) {
        Optional<Set<Long>> allowedOrgIdsOpt = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        if (allowedOrgIdsOpt.isEmpty()) {
            return orgMapper.selectList(childrenQueryWrapper(effectiveParentId));
        }

        Set<Long> allowedOrgIds = allowedOrgIdsOpt.get();
        if (effectiveParentId == ROOT_PARENT_ID) {
            return listAllUndeletedOrdered().stream()
                    .filter(entity -> allowedOrgIds.contains(entity.getId())
                            && !allowedOrgIds.contains(entity.getParentId()))
                    .toList();
        }

        if (!allowedOrgIds.contains(effectiveParentId)) {
            return List.of();
        }
        return orgMapper.selectList(childrenQueryWrapper(effectiveParentId)).stream()
                .filter(entity -> allowedOrgIds.contains(entity.getId()))
                .toList();
    }

    /**
     * 对已按管辖范围过滤好的组织实体列表在应用层手工做分页切片并构造分页响应
     * （org-scope-data-permission change design.md Decision 4 / tasks 4.4）：过滤发生在
     * 应用层之后，不能再依赖 {@code orgMapper.selectPage} 返回的 {@code IPage} 元信息
     * （那是过滤前的总数），分页的 {@code total}/{@code page}/{@code pageSize} 需要基于
     * 过滤后的列表自行计算。
     *
     * @param filtered 已按管辖范围过滤好的组织实体列表（未分页）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页响应
     */
    private PageResult<OrgVO> paginateFiltered(List<OrgEntity> filtered, Integer page, Integer pageSize) {
        int effectivePage = page != null && page > 0 ? page : 1;
        int effectivePageSize = pageSize != null && pageSize > 0 ? pageSize : filtered.size();
        long total = filtered.size();

        int fromIndex = Math.min((effectivePage - 1) * effectivePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + effectivePageSize, filtered.size());
        List<OrgVO> records = toVOListWithParentName(filtered.subList(fromIndex, toIndex));
        return new PageResult<>(records, total, effectivePage, effectivePageSize);
    }

    /**
     * 查询一个未被逻辑删除的组织，不存在时抛出业务异常。
     *
     * @param id 组织 id
     * @return 组织实体
     */
    private OrgEntity getExistingEntity(Long id) {
        OrgEntity entity = orgMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), OrgStatus.DELETED)) {
            throw new BusinessException("组织不存在");
        }
        return entity;
    }

    /**
     * 查询一个未被逻辑删除、且自身 id 落在当前登录用户管辖组织范围内的组织，供
     * {@code update}/{@code changeStatus}/{@code delete} 等写操作复用（org-scope-write-guard
     * change design.md Decision 3）。管辖范围不受限时行为等同于 {@link #getExistingEntity}；
     * 受限且该组织不在允许集合内时，复用"组织不存在"错误文案而不是单独的"无权限"提示，
     * 避免向调用者暴露"该 id 存在但你无权限"这一越权探测信号（design.md Decision 2）。
     *
     * @param id 组织 id
     * @return 组织实体
     */
    private OrgEntity getExistingEntityInScope(Long id) {
        OrgEntity entity = getExistingEntity(id);
        if (!orgScopeService.isOrgIdAllowed(CurrentUserContext.getUserId(), entity.getId())) {
            throw new BusinessException("组织不存在");
        }
        return entity;
    }

    /**
     * 校验新增/移动组织时指定的上级组织 id 是否落在当前登录用户的管辖组织范围内，
     * 不受限时恒放行；受限且不在允许集合内时直接拒绝，因为校验对象是"要挂到哪个组织下"
     * 而非某条具体记录，不需要伪装成"不存在"（org-scope-write-guard change design.md
     * Decision 2）。
     *
     * @param parentId 上级组织 id
     */
    private void assertParentOrgInScope(Long parentId) {
        if (!orgScopeService.isOrgIdAllowed(CurrentUserContext.getUserId(), parentId)) {
            throw new BusinessException("无权限在管辖范围之外的组织下操作");
        }
    }

    /**
     * 校验组织编码在未删除的组织中是否唯一。
     *
     * @param code      待校验的组织编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<OrgEntity> wrapper = new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getCode, code)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(OrgEntity::getId, excludeId);
        }
        Long count = orgMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("组织编码[" + code + "]已存在");
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
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.ORG);
        DynamicFieldValidator.validate(definitions, request, creating, (column, value) -> {
            if (!ALLOWED_DYNAMIC_COLUMNS.contains(column)) {
                throw new BusinessException("非法的动态字段列名：" + column);
            }
            return orgMapper.countByColumnValue(column, value, excludeId);
        });
    }

    /**
     * 把组织实体列表转换为详情视图对象列表，并批量解析上级组织名称、
     * {@code createBy}/{@code updateBy} 审计字段展示名。
     *
     * @param entities 组织实体列表
     * @return 详情视图对象列表
     */
    private List<OrgVO> toVOListWithParentName(List<OrgEntity> entities) {
        Set<Long> parentIds = entities.stream()
                .map(OrgEntity::getParentId)
                .filter(parentId -> parentId != null && parentId != ROOT_PARENT_ID)
                .collect(Collectors.toSet());

        Map<Long, String> parentNameMap;
        if (parentIds.isEmpty()) {
            parentNameMap = Map.of();
        } else {
            List<OrgEntity> parents = orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                    .in(OrgEntity::getId, parentIds));
            parentNameMap = parents.stream()
                    .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getName, (left, right) -> left));
        }

        Set<String> auditUserIdTexts = entities.stream()
                .flatMap(entity -> Stream.of(entity.getCreateBy(), entity.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);

        List<OrgVO> result = OrgConvert.INSTANCE.toVOList(entities);
        for (int i = 0; i < entities.size(); i++) {
            OrgVO vo = result.get(i);
            vo.setParentName(parentNameMap.get(vo.getParentId()));
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
     * 构造组织实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值；
     * 上级组织名称需按 {@code parentId} 回查一次；末尾追加当前启用的 {@code ext1}..
     * {@code ext10} 扩展字段（key 使用字段定义的展示名）。
     *
     * @param entity 组织实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(OrgEntity entity) {
        String parentName = null;
        if (entity.getParentId() != null && entity.getParentId() != ROOT_PARENT_ID) {
            OrgEntity parent = orgMapper.selectById(entity.getParentId());
            parentName = parent != null ? parent.getName() : null;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("组织名称", entity.getName());
        snapshot.put("组织编码", entity.getCode());
        snapshot.put("上级组织", parentName);
        snapshot.put("上级组织编码", entity.getParentCode());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));

        List<FormFieldDefinitionVO> definitions =
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.ORG);
        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, definitions, extValues(entity));
        return snapshot;
    }

    /**
     * 把组织实体的 {@code ext1}..{@code ext10} 逐一收集为列名到当前值的映射，
     * 供 {@link FormFieldSnapshotSupport#appendExtFieldSnapshot} 使用。
     *
     * @param entity 组织实体
     * @return {@code ext1}..{@code ext10} 列名到当前值的映射
     */
    private Map<String, String> extValues(OrgEntity entity) {
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
     * 把组织状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, OrgStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, OrgStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
