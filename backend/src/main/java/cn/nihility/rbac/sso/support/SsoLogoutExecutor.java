package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 单点登出主流程的公共执行逻辑（add-sso-single-logout change tasks.md 4.2/4.3）：撤销当前
 * 浏览器持有的 SSO 会话、清除会话 Cookie、触发一次单点登出后端回调通知（fire-and-forget，
 * 不等待结果）、最终 302 重定向到调用方指定的回跳地址，供 CAS 单点登出接口
 * （{@code CasController#logout}）与全局登出接口（{@code SsoLogoutController#logout}）
 * 共用，两者的差异仅在于登出前对 {@code service} 参数的校验方式不同
 * （{@code AppProtocolGuard#assertCasServiceAllowed}/{@code assertLogoutServiceAllowed}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoLogoutExecutor {

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /** 单点登出后端回调通知业务逻辑接口。 */
    private final SsoLogoutNotifyService ssoLogoutNotifyService;

    /** SSO 相关配置：会话 Cookie 是否要求 Secure。 */
    private final RbacSsoProperties ssoProperties;

    /**
     * 执行登出主流程并 302 重定向到 {@code service}。
     *
     * @param request  当前请求，用于提取 SSO 会话 Cookie
     * @param response 当前响应
     * @param service  校验通过的回跳地址
     * @throws IOException 写响应失败
     */
    public void execute(HttpServletRequest request, HttpServletResponse response, String service) throws IOException {
        String sessionToken = SsoSessionCookieUtils.extractSessionToken(request);
        if (StringUtils.hasText(sessionToken)) {
            ssoSessionService.revoke(sessionToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, SsoSessionCookieUtils.buildClearCookieHeader(ssoProperties.isCookieSecure()));

        if (StringUtils.hasText(sessionToken)) {
            try {
                ssoLogoutNotifyService.notifyLogout(sessionToken);
            } catch (Exception e) {
                // 通知环节的任何异常均不应影响登出主流程与 302 响应（design.md Goals/Decision 2
                // 步骤 4；SsoLogoutNotifyService 内部已就地捕获单个应用通知失败/线程池拒绝，
                // 这里是最后一道防线，兜底捕获遗漏的异常）。
                log.warn("触发单点登出后端回调通知时发生未预期异常，不影响本次登出", e);
            }
        }

        ProtocolResponseWriter.redirect(response, service);
    }
}
