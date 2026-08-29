package cn.nihility.rbac.sync.scope;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 应用同步组织范围解析业务逻辑组件：按 {@code (appRefId, syncDomain)} 解析
 * {@code tab_app_sync_org_scope} 配置的允许组织 id 集合，语义与
 * {@code cn.nihility.rbac.auth.service.OrgScopeService}（管理员管辖组织范围解析）完全对齐
 * ——{@code Optional.empty()} 表示不限制，非空 {@link Set} 是已展开
 * {@code include_children} 子孙的完整允许组织 id 集合，物理上是两张独立的表（按应用维度
 * vs. 按管理员维度），互不影响（app-sync-org-scope-and-app-change-log change design.md
 * Decision 2）。与 {@code OrgScopeService} 不同的是，本类展开子孙时使用
 * {@link OrgDescendantExpander#expandWithDescendantsIncludingDeleted}，不排除已逻辑删除
 * 的组织——组织被逻辑删除后仍应留在应用同步范围内，使其自身及归属该组织的用户/任职记录
 * 能继续通过 {@code /open/api/sync/pull} 拉取到并观察到 {@code bizStatus} 已变为已删除
 * （fix-app-sync-pull-deleted-org-scope change design.md Decision 1/2）。
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

    /** 组织数据访问接口，供 {@link #resolveScopePrefixes} 查询组织当前的 {@code orgPath}。 */
    private final OrgMapper orgMapper;

    /**
     * {@code orgPath} 合法字符白名单：仅允许数字与 {@code /} 分隔符，拒绝 {@code %}/{@code _}
     * 等 SQL {@code LIKE} 通配符，防止相邻编码前缀越权命中（app-sync-changelog-pull change
     * design.md Decision 4）。空字符串（顶级组织不应出现，但防御性放行，等值匹配零风险）也
     * 视为合法。
     */
    private static final Pattern ORG_PATH_PATTERN = Pattern.compile("^[0-9/]*$");

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
            allowedOrgIds.addAll(orgDescendantExpander.expandWithDescendantsIncludingDeleted(recursiveRootOrgIds));
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

    /**
     * 批量校验一批候选用户 id 是否落在指定应用 {@code USER} 数据域配置的允许组织范围内，
     * 一次 {@code IN} 查询完成，避免逐用户单独查询任职记录产生 N+1（app-sync-changelog-pull
     * change design.md Decision 4/Risks——"USER 范围过滤需要额外查询任职"，用批量查询规避
     * 单请求耗时失控）。语义与 {@link #isUserWithinScope} 单用户版本一致："任一未删除任职
     * 落在允许范围内即视为命中"。
     *
     * @param appRefId          应用 id（{@code tab_app.id}）
     * @param candidateUserIds  候选用户 id 集合，可为空集合（此时直接返回空集合，不触发查询）
     * @return 候选集合中落在允许组织范围内的用户 id 子集；{@code USER} 数据域不受限制时原样
     *         返回候选集合本身
     */
    public Set<Long> filterUsersWithinScope(Long appRefId, Set<Long> candidateUserIds) {
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            return Set.of();
        }
        Optional<Set<Long>> allowedOrgIds = resolveAllowedOrgIds(appRefId, SyncDomain.USER);
        if (allowedOrgIds.isEmpty()) {
            return candidateUserIds;
        }

        Set<Long> allowed = allowedOrgIds.get();
        List<UserPositionEntity> positions = userPositionMapper.selectList(
                new LambdaQueryWrapper<UserPositionEntity>()
                        .in(UserPositionEntity::getUserId, candidateUserIds)
                        .ne(UserPositionEntity::getStatus, PositionStatus.DELETED));
        return positions.stream().filter(position -> allowed.contains(position.getOrgId()))
                .map(UserPositionEntity::getUserId).collect(Collectors.toSet());
    }

    /**
     * 解析指定应用某个数据域配置的"原始范围前缀"列表：直接查询 {@code tab_app_sync_org_scope}
     * 原始行，逐行解析每个 {@code orgId} 当前的 {@code orgPath}（app-sync-changelog-pull
     * change design.md Decision 4）。与 {@link #resolveAllowedOrgIds} 不同，本方法不展开
     * {@code include_children} 子孙，保留原始路径前缀与是否包含子孙的语义，供调用方在 SQL
     * 层拼装边界安全的路径前缀过滤条件。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域，应为 {@code SyncDomain.ORG_SCOPE_DOMAINS} 三者之一（仅 ORG/
     *                   POSITION 两个数据域实际会调用本方法，USER 数据域不适用范围前缀过滤，
     *                   见 design.md Decision 4）
     * @return 空列表表示不受限制（该应用该数据域未配置任何组织范围行）；非空列表为原始范围
     *         前缀，组织已被物理删除、查不到 {@code orgPath} 的配置行会被静默跳过（防御性
     *         写法，正常情况下组织只做逻辑删除，物理删除极为罕见）
     * @throws BusinessException 当查到的 {@code orgPath} 含有 {@code %}/{@code _} 等
     *                            SQL {@code LIKE} 通配字符时抛出，防止相邻编码前缀越权命中
     *                            （防御性校验，正常路径下 {@code orgPath} 由服务端统一生成，
     *                            恒为数字与 {@code /} 组合，不应触发）
     */
    public List<ScopePrefix> resolveScopePrefixes(Long appRefId, String syncDomain) {
        List<AppSyncOrgScopeEntity> scopes = appSyncOrgScopeMapper.selectList(
                new LambdaQueryWrapper<AppSyncOrgScopeEntity>()
                        .eq(AppSyncOrgScopeEntity::getAppRefId, appRefId)
                        .eq(AppSyncOrgScopeEntity::getSyncDomain, syncDomain));
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }

        Set<Long> orgIds = scopes.stream().map(AppSyncOrgScopeEntity::getOrgId).collect(Collectors.toSet());
        Map<Long, String> orgPathById = orgMapper.selectByIds(orgIds).stream()
                .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getOrgPath, (a, b) -> a));

        List<ScopePrefix> prefixes = new ArrayList<>();
        for (AppSyncOrgScopeEntity scope : scopes) {
            String orgPath = orgPathById.get(scope.getOrgId());
            if (orgPath == null) {
                continue;
            }
            assertSafeOrgPath(orgPath);
            prefixes.add(new ScopePrefix(orgPath, Boolean.TRUE.equals(scope.getIncludeChildren())));
        }
        return prefixes;
    }

    /**
     * 防御性校验 {@code orgPath} 不包含 SQL {@code LIKE} 通配字符（{@code %}/{@code _}），
     * 只允许数字与 {@code /} 分隔符（app-sync-changelog-pull change design.md Decision 4）。
     *
     * @param orgPath 待校验的组织路径
     * @throws BusinessException 校验不通过时抛出
     */
    private void assertSafeOrgPath(String orgPath) {
        if (!ORG_PATH_PATTERN.matcher(orgPath).matches()) {
            throw new BusinessException("组织路径包含非法字符，拒绝用于范围查询：" + orgPath);
        }
    }
}
