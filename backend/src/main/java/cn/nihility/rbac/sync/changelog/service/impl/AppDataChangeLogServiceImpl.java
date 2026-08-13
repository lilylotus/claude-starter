package cn.nihility.rbac.sync.changelog.service.impl;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.mapper.NotifyTargetMapper;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 应用数据变更记录业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppDataChangeLogServiceImpl implements AppDataChangeLogService {

    /** 应用数据变更记录数据访问接口。 */
    private final AppDataChangeLogMapper appDataChangeLogMapper;

    /** 同步候选应用查询数据访问接口。 */
    private final NotifyTargetMapper notifyTargetMapper;

    /** 应用同步组织范围解析业务逻辑组件。 */
    private final AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    /** 用户任职记录数据访问接口，供 POSITION 数据域按 {@code bizId} 反查所属组织 id。 */
    private final UserPositionMapper userPositionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppDataChangeLogEntity> record(DomainChangeEvent event) {
        List<Long> candidateAppRefIds = notifyTargetMapper.selectCandidateAppRefIds(event.getDataType());
        if (candidateAppRefIds == null || candidateAppRefIds.isEmpty()) {
            return List.of();
        }

        List<Long> matchedAppRefIds = filterByOrgScope(event, candidateAppRefIds);
        if (matchedAppRefIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<AppDataChangeLogEntity> entities = new ArrayList<>(matchedAppRefIds.size());
        for (Long appRefId : matchedAppRefIds) {
            AppDataChangeLogEntity entity = AppDataChangeLogEntity.builder()
                    .appRefId(appRefId)
                    .dataType(event.getDataType())
                    .bizId(event.getBizId())
                    .operationType(event.getOperationType())
                    .createBy(event.getOperator())
                    .createTime(now)
                    .updateBy(event.getOperator())
                    .updateTime(now)
                    .build();
            appDataChangeLogMapper.insert(entity);
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 按数据域对候选应用列表做组织范围过滤：ORG/USER/POSITION 三个数据域各自按
     * {@link AppSyncOrgScopeResolver} 判断本次变更的业务对象是否落在每个候选应用配置的
     * 组织范围内，不落在范围内的应用从候选列表中剔除；APP/ROLE（及理论上不会出现在事件里的
     * DICT）数据域不做过滤，候选列表全部保留（app-sync-org-scope-and-app-change-log change
     * design.md Decision 1/4）。
     *
     * @param event              领域变更事件
     * @param candidateAppRefIds 候选应用 id 列表
     * @return 过滤后的应用 id 列表
     */
    private List<Long> filterByOrgScope(DomainChangeEvent event, List<Long> candidateAppRefIds) {
        String dataType = event.getDataType();
        if (SyncDomain.ORG.equals(dataType)) {
            return candidateAppRefIds.stream()
                    .filter(appRefId -> appSyncOrgScopeResolver.isOrgIdWithinScope(appRefId, SyncDomain.ORG,
                            event.getBizId()))
                    .toList();
        }
        if (SyncDomain.POSITION.equals(dataType)) {
            UserPositionEntity position = userPositionMapper.selectById(event.getBizId());
            if (position == null) {
                // 任职记录查不到时保守处理为不匹配，不落库给任何候选应用，与
                // BizSnapshotResolver 现有"查不到就跳过"的防御性风格一致（design.md Decision 4）。
                return List.of();
            }
            Long orgId = position.getOrgId();
            return candidateAppRefIds.stream()
                    .filter(appRefId -> appSyncOrgScopeResolver.isOrgIdWithinScope(appRefId, SyncDomain.POSITION,
                            orgId))
                    .toList();
        }
        if (SyncDomain.USER.equals(dataType)) {
            return candidateAppRefIds.stream()
                    .filter(appRefId -> appSyncOrgScopeResolver.isUserWithinScope(appRefId, event.getBizId()))
                    .toList();
        }
        return candidateAppRefIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppDataChangeLogEntity> selectLatestByBizIds(Long appRefId, String dataType, List<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            return List.of();
        }
        return appDataChangeLogMapper.selectLatestByBizIds(appRefId, dataType, bizIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppDataChangeLogEntity> selectBySequence(Long appRefId, Collection<String> dataTypes,
            Long fromSequence, int limit) {
        if (dataTypes == null || dataTypes.isEmpty()) {
            return List.of();
        }
        return appDataChangeLogMapper.selectBySequence(appRefId, dataTypes, fromSequence, limit);
    }
}
