package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.common.util.JacksonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.springframework.http.MediaType;

/**
 * CAS/OAuth2 协议端点响应写出工具（app-sso-protocol-runtime change design.md Decision 4）：
 * 直接操作 {@link HttpServletResponse} 写 redirect/xml/json/text，绕开
 * {@code GlobalResponseAdvice} 的 {@code {code,message,data}} 包装，保证协议响应格式符合
 * CAS/OAuth2 标准。供 {@code CasController}/{@code OAuthController} 复用，避免每个端点
 * 重复写字符编码/Content-Type 样板代码。
 */
public final class ProtocolResponseWriter {

    /** SSO 登录页路径，未建立有效 SSO 会话时统一重定向到该路径。 */
    private static final String SSO_LOGIN_PATH = "/sso/login";

    /**
     * 工具类不允许实例化。
     */
    private ProtocolResponseWriter() {
    }

    /**
     * 发起一次 302 重定向。
     *
     * @param response 当前响应
     * @param location 重定向目标地址
     * @throws IOException 写响应失败
     */
    public static void redirect(HttpServletResponse response, String location) throws IOException {
        response.sendRedirect(location);
    }

    /**
     * 写出一段 XML 响应体（CAS 协议响应）。
     *
     * @param response 当前响应
     * @param xmlBody  XML 响应体
     * @throws IOException 写响应失败
     */
    public static void xml(HttpServletResponse response, String xmlBody) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(xmlBody);
    }

    /**
     * 写出一段 JSON 响应体（标准 OAuth2 响应，不经过 {@code Result} 包装）。
     *
     * @param response   当前响应
     * @param httpStatus HTTP 状态码
     * @param body       待序列化的响应体对象
     * @throws IOException 写响应失败
     */
    public static void json(HttpServletResponse response, int httpStatus, Object body) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JacksonUtils.toJson(body));
    }

    /**
     * 写出一段纯文本响应体。
     *
     * @param response   当前响应
     * @param httpStatus HTTP 状态码
     * @param message    文本内容
     * @throws IOException 写响应失败
     */
    public static void text(HttpServletResponse response, int httpStatus, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(message);
    }

    /**
     * 构造"未建立有效 SSO 会话时"重定向到 SSO 登录页的目标地址，携带 {@code redirect} 查询
     * 参数（当前完整请求 URI + query，经 URL 编码），登录成功后前端据此回跳到本次协议请求。
     *
     * @param request 当前请求
     * @return SSO 登录页重定向地址
     */
    public static String ssoLoginRedirectLocation(HttpServletRequest request) {
        StringBuilder fullUrl = new StringBuilder(request.getRequestURL());
        if (request.getQueryString() != null) {
            fullUrl.append('?').append(request.getQueryString());
        }
        return SSO_LOGIN_PATH + "?redirect=" + URLEncoder.encode(fullUrl.toString(), StandardCharsets.UTF_8);
    }
}
