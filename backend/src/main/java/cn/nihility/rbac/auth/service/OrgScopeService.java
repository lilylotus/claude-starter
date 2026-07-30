package cn.nihility.rbac.auth.service;

import java.util.Optional;
import java.util.Set;

/**
 * 管辖组织范围解析业务逻辑接口：判断当前登录用户对应的启用状态管理员身份配置了哪些
 * {@code tab_admin_org_scope} 管辖组织范围，语义上和 {@link AuthorizationService}
 * （"给定当前会话身份，判断其菜单权限"）是同一类横切关注点——本接口回答的是"给定当前
 * 会话身份，判断其数据可见范围"（org-scope-data-permission change design.md Decision 1）。
 */
public interface OrgScopeService {

    /**
     * 解析当前用户的管辖组织范围。
     *
     * @param userId 用户 id（{@code tab_user.id}）
     * @return 空 {@link Optional} 表示不受限制（未配置管辖范围，或用户没有启用状态的管理员
     *         身份，二者统一处理为同一种"不受限制"结果，不额外区分）；非空时表示受限，
     *         {@link Set} 为已展开 {@code include_children} 子孙的允许组织 id 全集
     */
    Optional<Set<Long>> resolveAllowedOrgIds(Long userId);
}
