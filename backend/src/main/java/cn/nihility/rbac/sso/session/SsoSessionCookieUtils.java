package cn.nihility.rbac.sso.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * SSO 会话 Cookie 的构造/解析工具类（app-sso-protocol-runtime change design.md Decision 2）：
 * Set-Cookie 响应头手写为字符串（不使用 {@link Cookie}，避免不同 Servlet 容器/版本对
 * {@code SameSite} 属性支持不一致的问题），从请求中提取会话值仍复用
 * {@link HttpServletRequest#getCookies()}。
 */
public final class SsoSessionCookieUtils {

    /** SSO 会话 Cookie 名称。 */
    public static final String COOKIE_NAME = "sso_session";

    /**
     * 工具类不允许实例化。
     */
    private SsoSessionCookieUtils() {
    }

    /**
     * 构造签发 SSO 会话时使用的 Set-Cookie 响应头字符串。
     *
     * @param token          会话令牌
     * @param maxAgeSeconds  Cookie 有效期（秒）
     * @param cookieSecure   是否追加 {@code Secure} 属性
     * @return Set-Cookie 响应头值
     */
    public static String buildSetCookieHeader(String token, long maxAgeSeconds, boolean cookieSecure) {
        StringBuilder sb = new StringBuilder();
        sb.append(COOKIE_NAME).append('=').append(token)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAgeSeconds);
        if (cookieSecure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    /**
     * 构造登出时使用的清除 Cookie 的 Set-Cookie 响应头字符串（{@code Max-Age=0} 覆盖清除）。
     *
     * @param cookieSecure 是否追加 {@code Secure} 属性
     * @return Set-Cookie 响应头值
     */
    public static String buildClearCookieHeader(boolean cookieSecure) {
        StringBuilder sb = new StringBuilder();
        sb.append(COOKIE_NAME).append("=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        if (cookieSecure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    /**
     * 从请求中提取 SSO 会话 Cookie 的值。
     *
     * @param request 当前请求
     * @return 会话令牌，未携带该 Cookie 时返回 {@code null}
     */
    public static String extractSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (Objects.equals(COOKIE_NAME, cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
