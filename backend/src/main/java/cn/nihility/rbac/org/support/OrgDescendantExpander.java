package cn.nihility.rbac.org.support;

import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组织子孙展开工具组件：给定一组根组织 id，返回其自身连同全部未逻辑删除子孙组织 id 的并集。
 * 独立成一个只依赖 {@link OrgMapper} 的组件，而不是 {@code OrgService} 接口方法，是为了让
 * {@code auth} 模块的 {@code OrgScopeService}（解析管辖组织范围时需要展开
 * {@code include_children} 配置）可以直接依赖这个单一职责的组件，不必反过来依赖
 * {@code OrgService}——{@code OrgServiceImpl} 本身依赖 {@code OrgScopeService} 过滤组织
 * 树/列表，如果 {@code OrgScopeService} 也依赖 {@code OrgService}，会构成 Spring 纯构造器
 * 注入无法解析的循环 bean 依赖（org-scope-data-permission change 实现过程中发现并规避的
 * 问题，见该 change design.md Decision 3 的补充说明）。
 * 展开算法延续 {@code OrgServiceImpl.getTree()} 已有的"全量加载未删除组织到内存、按
 * {@code parentId} 建邻接表"套路做 BFS 遍历，不使用 SQL 递归 CTE——运行环境是 MySQL 5.7，
 * 不支持 {@code WITH RECURSIVE}。
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

        Map<Long, List<Long>> childIdsByParentId = new HashMap<>();
        List<OrgEntity> entities = orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                .ne(OrgEntity::getStatus, OrgStatus.DELETED));
        for (OrgEntity entity : entities) {
            childIdsByParentId.computeIfAbsent(entity.getParentId(), key -> new ArrayList<>()).add(entity.getId());
        }

        Set<Long> result = new HashSet<>(rootOrgIds);
        Deque<Long> pending = new ArrayDeque<>(rootOrgIds);
        while (!pending.isEmpty()) {
            Long current = pending.poll();
            List<Long> childIds = childIdsByParentId.get(current);
            if (childIds == null) {
                continue;
            }
            for (Long childId : childIds) {
                if (result.add(childId)) {
                    pending.offer(childId);
                }
            }
        }
        return result;
    }
}
