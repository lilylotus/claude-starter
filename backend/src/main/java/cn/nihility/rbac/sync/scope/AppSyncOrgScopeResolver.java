package cn.nihility.rbac.sync.scope;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 应用同步组织范围解析业务逻辑组件：按 {@code (appRefId, syncDomain)} 解析
 * {@code tab_app_sync_org_scope} 配置的允许组织 id 集合，语义与
 * {@code cn.nihility.rbac.auth.service.OrgScopeService}（管理员管辖组织范围解析）完全对齐
 * ——{@code Optional.empty()} 表示不限制，非空 {@link Set} 是已展开
 * {@code include_children} 子孙的完整允许组织 id 集合，复用同一个
 * {@link OrgDescendantExpander} 展开算法，但物理上是两张独立的表（按应用维度 vs. 按管理员
 * 维度），互不影响（app-sync-org-scope-and-app-change-log change design.md Decision 2）。
 * 不引入缓存，每次调用实时查库，与 {@code OrgScopeService} 现有"不缓存，实时查库"的约定
 * 保持一致。
 */
@Component
@RequiredArgsConstructor
public class AppSyncOrgScopeResolver {

    /** 应用同步组织范围数据访问接口。 */
    private final AppSyncOrgScopeMapper appSyncOrgScopeMapper;

    /** 组织子孙展开工具组件，用于展开 {@code include_children} 子孙组织 id。 */
    private final OrgDescendantExpander orgDescendantExpander;

    /** 用户任职记录数据访问接口，供 {@link #isUserWithinScope} 判断用户的组织归属。 */
    private final UserPositionMapper userPositionMapper;

    /**
     * 解析指定应用某个数据域配置的允许组织 id 集合。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域，应为 {@code SyncDomain.ORG_SCOPE_DOMAINS} 三者之一
     * @return 空 {@link Optional} 表示不受限制（该应用该数据域未配置任何组织范围行，即
     *         "全部数据"）；非空时表示受限，{@link Set} 为已展开 {@code include_children}
     *         子孙的允许组织 id 全集
     */
    public Optional<Set<Long>> resolveAllowedOrgIds(Long appRefId, String syncDomain) {
        List<AppSyncOrgScopeEntity> scopes = appSyncOrgScopeMapper.selectList(
                new LambdaQueryWrapper<AppSyncOrgScopeEntity>()
                        .eq(AppSyncOrgScopeEntity::getAppRefId, appRefId)
                        .eq(AppSyncOrgScopeEntity::getSyncDomain, syncDomain));
        if (scopes == null || scopes.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> recursiveRootOrgIds = new HashSet<>();
        Set<Long> allowedOrgIds = new HashSet<>();
        for (AppSyncOrgScopeEntity scope : scopes) {
            if (Boolean.TRUE.equals(scope.getIncludeChildren())) {
                recursiveRootOrgIds.add(scope.getOrgId());
            } else {
                allowedOrgIds.add(scope.getOrgId());
            }
        }
        if (!recursiveRootOrgIds.isEmpty()) {
            allowedOrgIds.addAll(orgDescendantExpander.expandWithDescendants(recursiveRootOrgIds));
        }
        return Optional.of(allowedOrgIds);
    }

    /**
     * 校验某个组织 id 是否落在指定应用某个数据域配置的允许组织范围内。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域，应为 {@code SyncDomain.ORG_SCOPE_DOMAINS} 三者之一
     * @param orgId      待校验的组织 id
     * @return {@link #resolveAllowedOrgIds} 解析结果为不受限制时恒为 {@code true}；受限时，
     *         {@code orgId} 落在允许集合内返回 {@code true}，否则返回 {@code false}
     */
    public boolean isOrgIdWithinScope(Long appRefId, String syncDomain, Long orgId) {
        Optional<Set<Long>> allowedOrgIds = resolveAllowedOrgIds(appRefId, syncDomain);
        return allowedOrgIds.isEmpty() || allowedOrgIds.get().contains(orgId);
    }

    /**
     * 校验某个用户是否落在指定应用 {@code USER} 数据域配置的允许组织范围内：先解析该应用
     * {@code USER} 数据域的允许组织集合，不受限制时恒为 {@code true}；受限时查询该用户全部
     * 未逻辑删除的任职记录（{@code tab_user_position}），只要存在至少一条任职记录的
     * {@code orgId} 落在允许集合内即视为命中（design.md Decision 3——一个用户可以同时在
     * 多个组织任职，"只要有一个任职落在应用允许范围内就该应用可见"是最贴近直觉的语义）。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param userId   待校验的用户 id
     * @return 是否落在允许组织范围内
     */
    public boolean isUserWithinScope(Long appRefId, Long userId) {
        Optional<Set<Long>> allowedOrgIds = resolveAllowedOrgIds(appRefId, SyncDomain.USER);
        if (allowedOrgIds.isEmpty()) {
            return true;
        }

        Set<Long> allowed = allowedOrgIds.get();
        List<UserPositionEntity> positions = userPositionMapper.selectList(
                new LambdaQueryWrapper<UserPositionEntity>()
                        .eq(UserPositionEntity::getUserId, userId)
                        .ne(UserPositionEntity::getStatus, PositionStatus.DELETED));
        return positions.stream().anyMatch(position -> allowed.contains(position.getOrgId()));
    }
}
