package cn.nihility.rbac.auth.service;

/**
 * 运行时鉴权业务逻辑接口：判断当前登录用户是否有权访问某个 {@code menu} 资源编码
 * （rbac-permission-authorization change design.md Decision 4）。
 */
public interface AuthorizationService {

    /**
     * 判断给定用户当前是否拥有指定 {@code menu} 编码对应的权限点。
     *
     * @param userId   用户 id
     * @param menuCode {@code menu} 请求头对应的资源编码（三段式）
     * @return 是否拥有权限
     */
    boolean hasPermission(Long userId, String menuCode);
}
