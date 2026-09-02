package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.loginlog.constant.LoginMethod;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从 SSO 登录页的 {@code redirect} 参数反解出本次登录请求归属的应用及其当前允许的登录认证
 * 方式（add-sso-login-methods change design.md Decision 2）：{@code redirect} 承载的是
 * {@code ProtocolResponseWriter#ssoLoginRedirectLocation} 编码进去的原始请求完整 URL，
 * CAS 场景形如 {@code .../api/authn/cas/{appId}/login?service=...}，OAuth2 场景形如
 * {@code .../api/authn/oauth/authorize?client_id=...&...}。
 *
 * <p>解析失败（{@code redirect} 缺失/不匹配任何已知协议 URL 形状）或解析出的应用查不到单点
 * 登录协议配置时，统一返回"仅允许 {@code PASSWORD}"的保守结果，不抛出异常——直接访问登录页
 * （无 {@code redirect}）、{@code redirect} 被篡改成无法识别的地址等异常输入都不应该意外放开
 * 短信/扫码入口。短信发送、二维码会话创建/确认等新增接口须复用本组件再次校验一遍允许的登录
 * 方式，不能只依赖前端"要不要展示对应 Tab"这一层把关。
 */
@Component
@RequiredArgsConstructor
public class SsoLoginContextResolver {

    /** 仅允许口令登录的保守结果，解析失败/应用不存在/未配置认证协议时统一返回。 */
    private static final List<String> PASSWORD_ONLY = List.of(LoginMethod.PASSWORD);

    /** CAS 单点登录路径正则：{@code /api/authn/cas/{appId}/login}，路径变量不含 {@code /} 或 {@code ?}。 */
    private static final Pattern CAS_LOGIN_PATH_PATTERN = Pattern.compile("/api/authn/cas/([^/?]+)/login(?:[/?]|$)");

    /** OAuth2 授权端点路径标记，出现该子串时才尝试从查询串解析 {@code client_id}。 */
    private static final String OAUTH_AUTHORIZE_PATH_MARKER = "/api/authn/oauth/authorize";

    /** 查询串中 {@code client_id} 参数的取值正则。 */
    private static final Pattern OAUTH_CLIENT_ID_PATTERN = Pattern.compile("[?&]client_id=([^&]+)");

    /** 应用协议校验入口，复用其"按 appId 尝试解析认证配置、查不到不抛异常"的既有能力。 */
    private final AppProtocolGuard appProtocolGuard;

    /**
     * 解析本次 SSO 登录请求归属的应用及其当前允许的登录认证方式。
     *
     * @param redirect SSO 登录页 {@code redirect} 参数原始值（已被框架/前端解码为原始 URL 字符串）
     * @return 解析结果，解析失败时 {@code appId} 为 {@code null}、{@code allowedLoginMethods}
     *         为仅含 {@code PASSWORD} 的列表
     */
    public SsoLoginContext resolve(String redirect) {
        String appId = extractAppId(redirect);
        if (appId == null) {
            return new SsoLoginContext(null, PASSWORD_ONLY);
        }
        return appProtocolGuard.tryResolveAuthConfig(appId)
                .map(authConfig -> new SsoLoginContext(appId, parseLoginMethods(authConfig)))
                .orElseGet(() -> new SsoLoginContext(appId, PASSWORD_ONLY));
    }

    /**
     * 从 {@code redirect} 中按 CAS 分支（路径变量）、OAuth2 分支（查询串 {@code client_id}）
     * 依次尝试解析出应用对外标识，均不匹配时返回 {@code null}。
     *
     * @param redirect 原始 URL 字符串，可能为空
     * @return 解析出的应用对外标识，解析不出时为 {@code null}
     */
    private String extractAppId(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return null;
        }
        Matcher casMatcher = CAS_LOGIN_PATH_PATTERN.matcher(redirect);
        if (casMatcher.find()) {
            return decode(casMatcher.group(1));
        }
        if (redirect.contains(OAUTH_AUTHORIZE_PATH_MARKER)) {
            Matcher oauthMatcher = OAUTH_CLIENT_ID_PATTERN.matcher(redirect);
            if (oauthMatcher.find()) {
                return decode(oauthMatcher.group(1));
            }
        }
        return null;
    }

    /**
     * 对提取出的路径变量/查询参数值做一次 URL 解码（应用标识本身通常不含需要转义的字符，
     * 解码只是防御性处理，即便原值未编码也不受影响）。
     *
     * @param value 待解码的原始值
     * @return 解码后的值
     */
    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 解析应用单点登录协议配置中的登录认证方式列表，空/未设置时按仅允许口令登录处理。
     *
     * @param authConfig 应用单点登录协议配置
     * @return 登录认证方式列表，不为空、不为 {@code null}
     */
    private List<String> parseLoginMethods(AppAuthConfigEntity authConfig) {
        String loginMethodsJson = authConfig.getLoginMethods();
        if (!StringUtils.hasText(loginMethodsJson)) {
            return PASSWORD_ONLY;
        }
        List<String> loginMethods = JacksonUtils.toObj(loginMethodsJson, JacksonUtils.LIST_STRING_TYPE_REFERENCE);
        return loginMethods == null || loginMethods.isEmpty() ? PASSWORD_ONLY : loginMethods;
    }
}
