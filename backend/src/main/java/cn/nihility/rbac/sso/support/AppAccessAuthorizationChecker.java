package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.appaccess.support.AppAccessAuthorizationDecision;
import cn.nihility.rbac.appaccess.support.AppAccessEffectivePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 应用访问授权校验入口（app-access-authorization change design.md Decision 5），与
 * {@link AppProtocolGuard} 同级、职责相邻：在 CAS 服务票据签发（{@code CasController.login}）
 * 与 OAuth2 授权码签发（{@code OAuthController.authorize}）两个"同时持有 userId 与 appId、
 * 且处于凭证签发源头"的位置各插入一次校验，未授权时拒绝且不签发凭证。委托
 * {@link AppAccessEffectivePermissionService#checkAuthorization(Long, Long, String, String)}
 * 完成"考虑请求上下文"的合并判定，不在本类重复实现判定逻辑（app-access-request-control
 * change design.md Decision 6：新增读取客户端 IP/User-Agent 并纳入授权判定；
 * policy-condition-exclusive-priority change design.md Decision：改用
 * {@code checkAuthorization} 以便把拒绝来源的策略 id 一并携带到异常里）。
 */
@Component
@RequiredArgsConstructor
public class AppAccessAuthorizationChecker {

    /** 最终生效权限计算与查询业务逻辑接口。 */
    private final AppAccessEffectivePermissionService appAccessEffectivePermissionService;

    /**
     * 校验指定用户是否具备访问指定应用的最终生效授权（考虑请求上下文：客户端 IP 与
     * {@code User-Agent}），不具备时抛出 {@link SsoProtocolException}（复用现有异常类型
     * 与拒绝响应机制，见 {@code CasController}/{@code OAuthController} 现有的
     * {@code catch (SsoProtocolException e)} 分支），并携带拒绝来源的策略 id（若适用）。
     *
     * @param userId    用户 id
     * @param appId     应用 id（{@code tab_app.id}）
     * @param clientIp  当前请求的客户端 IP，取不到时传 {@code null}
     * @param userAgent 当前请求的 {@code User-Agent}，取不到时传 {@code null}
     */
    public void assertAuthorized(Long userId, Long appId, String clientIp, String userAgent) {
        AppAccessAuthorizationDecision decision = appAccessEffectivePermissionService.checkAuthorization(userId,
                appId, clientIp, userAgent);
        if (!decision.authorized()) {
            throw new SsoProtocolException("当前用户无权访问该应用", decision.deniedByPolicyId());
        }
    }
}
