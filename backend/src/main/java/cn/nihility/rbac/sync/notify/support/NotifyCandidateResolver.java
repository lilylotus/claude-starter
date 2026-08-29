package cn.nihility.rbac.sync.notify.support;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.mapper.NotifyTargetMapper;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.sync.scope.ScopePrefix;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 通知候选应用判定组件：给定一条领域变更事件，直接判定当前哪些应用应该收到本次变更的通知，
 * 判定结果不持久化，仅用于当次触发（app-sync-drop-changelog change design.md Decision 6）。
 * 组织范围过滤逻辑原样迁移自原 {@code AppDataChangeLogServiceImpl.filterByOrgScope}
 * （app-sync-org-scope-and-app-change-log change design.md Decision 1/4）。
 */
@Component
@RequiredArgsConstructor
public class NotifyCandidateResolver {

    /** 通知候选应用查询数据访问接口。 */
    private final NotifyTargetMapper notifyTargetMapper;

    /** 应用同步组织范围解析业务逻辑组件。 */
    private final AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    /**
     * 解析一条领域变更事件当前应该触发通知的候选应用 id 列表：先查询该数据类型下已启用同步、
     * 总开关开启、同步方式为通知的候选应用，再对 ORG/USER/POSITION 三个数据域按每个候选应用
     * 配置的组织范围过滤一遍。
     *
     * @param event 领域变更事件
     * @return 最终匹配的应用 id 列表，候选应用为空或全部被组织范围过滤掉时返回空列表
     */
    public List<Long> resolveCandidateAppRefIds(DomainChangeEvent event) {
        List<Long> candidateAppRefIds = notifyTargetMapper.selectCandidateAppRefIds(event.getDataType());
        if (candidateAppRefIds == null || candidateAppRefIds.isEmpty()) {
            return List.of();
        }
        return filterByOrgScope(event, candidateAppRefIds);
    }

    /**
     * 按数据域对候选应用列表做组织范围过滤：ORG/USER/POSITION 三个数据域各自按
     * {@link AppSyncOrgScopeResolver} 判断本次变更的业务对象是否落在每个候选应用配置的
     * 组织范围内，不落在范围内的应用从候选列表中剔除；APP/ROLE 数据域不做过滤，候选列表全部
     * 保留。
     *
     * @param event              领域变更事件
     * @param candidateAppRefIds 候选应用 id 列表
     * @return 过滤后的应用 id 列表
     */
    private List<Long> filterByOrgScope(DomainChangeEvent event, List<Long> candidateAppRefIds) {
        String dataType = event.getDataType();
        if (SyncDomain.ORG.equals(dataType) || SyncDomain.POSITION.equals(dataType)) {
            return candidateAppRefIds.stream()
                    .filter(appRefId -> matchesEventPaths(appRefId, dataType, event))
                    .toList();
        }
        if (SyncDomain.USER.equals(dataType)) {
            return candidateAppRefIds.stream()
                    .filter(appRefId -> appSyncOrgScopeResolver.isUserWithinScope(appRefId, event.getBizId()))
                    .toList();
        }
        return candidateAppRefIds;
    }

    /** 使用事件变更前后路径按边界安全规则匹配一个应用的组织范围。 */
    private boolean matchesEventPaths(Long appRefId, String dataType, DomainChangeEvent event) {
        List<ScopePrefix> prefixes = appSyncOrgScopeResolver.resolveScopePrefixes(appRefId, dataType);
        if (prefixes.isEmpty()) {
            return true;
        }
        return prefixes.stream().anyMatch(prefix -> matchesPath(event.getOrgScopePathBefore(), prefix)
                || matchesPath(event.getOrgScopePathAfter(), prefix));
    }

    /** 路径必须等于范围根，或在包含子孙时以“根路径/”开头。 */
    private boolean matchesPath(String path, ScopePrefix prefix) {
        if (path == null || prefix == null || prefix.getOrgPath() == null) {
            return false;
        }
        return path.equals(prefix.getOrgPath())
                || prefix.isIncludeChildren() && path.startsWith(prefix.getOrgPath() + "/");
    }
}
