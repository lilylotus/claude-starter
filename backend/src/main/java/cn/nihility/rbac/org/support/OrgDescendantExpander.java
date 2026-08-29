package cn.nihility.rbac.org.support;

import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组织子孙展开工具组件：给定一组根组织 id，返回其自身连同全部子孙组织 id 的并集。
 * 独立成一个只依赖 {@link OrgMapper} 的组件，而不是 {@code OrgService} 接口方法，是为了让
 * {@code auth} 模块的 {@code OrgScopeService}（解析管辖组织范围时需要展开
 * {@code include_children} 配置）可以直接依赖这个单一职责的组件，不必反过来依赖
 * {@code OrgService}——{@code OrgServiceImpl} 本身依赖 {@code OrgScopeService} 过滤组织
 * 树/列表，如果 {@code OrgScopeService} 也依赖 {@code OrgService}，会构成 Spring 纯构造器
 * 注入无法解析的循环 bean 依赖（org-scope-data-permission change 实现过程中发现并规避的
 * 问题，见该 change design.md Decision 3 的补充说明）。
 * 使用物化路径前缀查询展开范围，不依赖 MySQL 8 的递归 CTE，也不再全表加载组织数据。
 * <p>
 * 类内提供两个展开方法，语义不同、互不共用同一段过滤逻辑，调用方须按场景选择：
 * <ul>
 *     <li>{@link #expandWithDescendants(Set)}：排除已逻辑删除的组织，供
 *         {@code auth.OrgScopeServiceImpl}（管理员管辖组织范围）与
 *         {@code appaccess.policy.service.impl.PolicyExecutionServiceImpl}（访问授权策略按
 *         组织范围匹配用户）使用——已删除组织不应出现在管理员可操作范围或策略匹配结果里。</li>
 *     <li>{@link #expandWithDescendantsIncludingDeleted(Set)}：不排除已逻辑删除的组织，供
 *         {@code sync.scope.AppSyncOrgScopeResolver}（应用同步组织范围）使用——
 *         {@code app-sync-notify-pull} 规范要求组织被逻辑删除后仍要留在应用同步范围内，
 *         使其自身及归属该组织的用户/任职记录能继续通过 {@code /open/api/sync/pull} 拉取到
 *         并观察到 {@code bizStatus} 已变为已删除（fix-app-sync-pull-deleted-org-scope
 *         change design.md Decision 1）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OrgDescendantExpander {

    /** 组织数据访问接口。 */
    private final OrgMapper orgMapper;

    /**
     * 展开一组根组织 id，返回其自身连同全部子孙组织 id 的并集（排除已逻辑删除的组织）。
     *
     * @param rootOrgIds 根组织 id 集合
     * @return 根组织自身连同全部子孙组织 id 的并集；{@code rootOrgIds} 为空时返回空集合，
     *         不发起任何数据库查询
     */
    public Set<Long> expandWithDescendants(Set<Long> rootOrgIds) {
        if (rootOrgIds == null || rootOrgIds.isEmpty()) {
            return new HashSet<>();
        }

        List<OrgEntity> roots = orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                .in(OrgEntity::getId, rootOrgIds)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED));
        if (roots.isEmpty()) {
            return new HashSet<>();
        }

        QueryWrapper<OrgEntity> pathQuery = new QueryWrapper<OrgEntity>()
                .select("id")
                .ne("status", OrgStatus.DELETED)
                .and(wrapper -> {
                    for (OrgEntity root : roots) {
                        String path = root.getOrgPath();
                        wrapper.or(item -> item.eq("org_path", path)
                                .or()
                                .likeRight("org_path", path + "/"));
                    }
                });
        Set<Long> result = new HashSet<>();
        for (OrgEntity entity : orgMapper.selectList(pathQuery)) {
            result.add(entity.getId());
        }
        return result;
    }

    /**
     * 展开一组根组织 id，返回其自身连同全部子孙组织 id 的并集（不排除已逻辑删除的组织）。
     * 与 {@link #expandWithDescendants(Set)} 结构完全一致，唯一区别是查询根组织与展开子孙
     * 时都不附加 {@code status != DELETED} 条件——根组织自身被删除时依然能查到其
     * {@code orgPath} 并据此展开删除前的全部子孙（fix-app-sync-pull-deleted-org-scope
     * change design.md Decision 3），仅供 {@code sync.scope.AppSyncOrgScopeResolver}
     * （应用同步组织范围）使用，不适用于管理员管辖范围或访问授权策略场景。
     *
     * @param rootOrgIds 根组织 id 集合
     * @return 根组织自身连同全部子孙组织 id 的并集（含已逻辑删除的组织）；{@code rootOrgIds}
     *         为空时返回空集合，不发起任何数据库查询
     */
    public Set<Long> expandWithDescendantsIncludingDeleted(Set<Long> rootOrgIds) {
        if (rootOrgIds == null || rootOrgIds.isEmpty()) {
            return new HashSet<>();
        }

        List<OrgEntity> roots = orgMapper.selectList(
                new LambdaQueryWrapper<OrgEntity>().in(OrgEntity::getId, rootOrgIds));
        if (roots.isEmpty()) {
            return new HashSet<>();
        }

        QueryWrapper<OrgEntity> pathQuery = new QueryWrapper<OrgEntity>()
                .select("id")
                .and(wrapper -> {
                    for (OrgEntity root : roots) {
                        String path = root.getOrgPath();
                        wrapper.or(item -> item.eq("org_path", path)
                                .or()
                                .likeRight("org_path", path + "/"));
                    }
                });
        Set<Long> result = new HashSet<>();
        for (OrgEntity entity : orgMapper.selectList(pathQuery)) {
            result.add(entity.getId());
        }
        return result;
    }
}
