package cn.nihility.rbac.abac.service;

/**
 * ABAC 运行时限制判定业务逻辑接口：给定用户 id 与资源编码，判断是否应被 ABAC 拒绝，供
 * {@code IdentityAuthFilter} 在 RBAC 权限点判定通过之后调用（abac-policy-engine change
 * design.md Decision 5）。判定结果仅为"拒绝"或"不拒绝"两种，不存在放行之外的中间态；
 * 该判定基于当前最新数据实时计算，不使用缓存。
 */
public interface AbacAuthorizationService {

    /**
     * 判断给定用户对给定资源编码的本次请求是否应被 ABAC 拒绝。
     *
     * @param userId       用户 id
     * @param resourceCode 资源编码（与 {@code menu} 运行时鉴权使用的三段式权限编码口径
     *                     一致）
     * @return {@code true} 表示应拒绝，{@code false} 表示不拒绝
     */
    boolean isDenied(Long userId, String resourceCode);
}
