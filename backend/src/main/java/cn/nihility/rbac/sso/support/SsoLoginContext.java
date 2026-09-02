package cn.nihility.rbac.sso.support;

import java.util.List;

/**
 * {@link SsoLoginContextResolver} 的解析结果：本次 SSO 登录请求归属的应用（解析不出时为
 * {@code null}）与该应用当前允许的登录认证方式列表（add-sso-login-methods change design.md
 * Decision 2/3）。
 *
 * @param appId               对外应用标识（AppId/client_id），解析不出时为 {@code null}
 * @param allowedLoginMethods 允许的登录认证方式列表，解析失败/应用不存在/未配置认证配置时
 *                            保守返回仅含 {@code PASSWORD} 的列表，不为空、不为 {@code null}
 */
public record SsoLoginContext(String appId, List<String> allowedLoginMethods) {

    /**
     * 判断某个登录认证方式当前是否被允许。
     *
     * @param loginMethod 登录认证方式取值，见 {@code LoginMethod}
     * @return 是否允许
     */
    public boolean allows(String loginMethod) {
        return allowedLoginMethods != null && allowedLoginMethods.contains(loginMethod);
    }
}
