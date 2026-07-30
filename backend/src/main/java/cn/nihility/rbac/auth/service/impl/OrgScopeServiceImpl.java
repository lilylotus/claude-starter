package cn.nihility.rbac.auth.service.impl;

import cn.nihility.rbac.admin.entity.AdminOrgScopeEntity;
import cn.nihility.rbac.admin.mapper.AdminOrgScopeMapper;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管辖组织范围解析业务逻辑实现：直接注入 {@code admin} 模块的 {@link AdminOrgScopeMapper}
 * 查询当前用户的管辖组织范围原始行，再委托 {@code org} 模块的 {@link OrgDescendantExpander}
 * 展开 {@code include_children} 子孙；不引入缓存，每次请求实时查库，与
 * {@code AuthorizationServiceImpl.hasPermission} 现有"不缓存，实时查库"的设计保持一致
 * （org-scope-data-permission change design.md Decision 1）。{@code auth} 模块跨模块直接
 * 注入其他模块 Mapper/组件已有先例（{@link AuthorizationServiceImpl} 注入 {@code permission}
 * 模块的 {@code PermissionMapper}），本实现延续同一模式。
 * <p>
 * 依赖 {@link OrgDescendantExpander}（只依赖 {@code OrgMapper} 的单一职责组件）而不是
 * {@code OrgService}：{@code OrgServiceImpl} 本身依赖本类过滤组织树/列表，如果本类反过来
 * 依赖 {@code OrgService}，会构成 Spring 纯构造器注入无法解析的循环 bean 依赖——这是
 * org-scope-data-permission change 实现过程中发现的问题，最终选择把"展开子孙组织 id"
 * 抽成独立组件规避循环，而不是靠 {@code @Lazy} 注入打补丁。
 */
@Service
@RequiredArgsConstructor
public class OrgScopeServiceImpl implements OrgScopeService {

    /** 管理员组织管辖范围数据访问接口。 */
    private final AdminOrgScopeMapper adminOrgScopeMapper;

    /** 组织子孙展开工具组件，用于展开 {@code include_children} 子孙组织 id。 */
    private final OrgDescendantExpander orgDescendantExpander;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Set<Long>> resolveAllowedOrgIds(Long userId) {
        List<AdminOrgScopeEntity> scopes = adminOrgScopeMapper.selectOrgScopesByUserId(userId);
        if (scopes == null || scopes.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> recursiveRootOrgIds = new HashSet<>();
        Set<Long> allowedOrgIds = new HashSet<>();
        for (AdminOrgScopeEntity scope : scopes) {
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
}
