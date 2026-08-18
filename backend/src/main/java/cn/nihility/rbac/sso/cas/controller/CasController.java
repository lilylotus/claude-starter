package cn.nihility.rbac.sso.cas.controller;

import cn.nihility.rbac.sso.cas.dto.CasTicketPayload;
import cn.nihility.rbac.sso.cas.service.CasTicketService;
import cn.nihility.rbac.sso.cas.support.CasXmlResponses;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sso.support.AppProtocolGuard;
import cn.nihility.rbac.sso.support.ProtocolResponseWriter;
import cn.nihility.rbac.sso.support.SsoProtocolException;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CAS 协议运行时端点（app-sso-protocol-runtime change design.md Decision 5）：
 * {@code /login}、{@code /p3/serviceValidate}、{@code /logout}。三个端点均绕开
 * {@code GlobalResponseAdvice} 的响应包装，方法签名声明为 {@code void}，直接操作
 * {@link HttpServletResponse} 写重定向/XML/纯文本响应（design.md Decision 4）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "CAS 单点登录", description = "CAS 协议运行时端点：登录、票据验证、登出")
public class CasController {

    /** 应用协议校验入口。 */
    private final AppProtocolGuard appProtocolGuard;

    /** CAS 服务票据业务逻辑接口。 */
    private final CasTicketService casTicketService;

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /** SSO 相关配置：会话 Cookie 是否要求 Secure。 */
    private final RbacSsoProperties ssoProperties;

    /** 用户数据访问接口，票据验证成功后按用户 id 查询用户标识/姓名。 */
    private final UserMapper userMapper;

    /**
     * CAS 单点登录：{@code service} 校验通过后，若当前浏览器持有有效 SSO 会话则签发服务
     * 票据并重定向回 {@code service}；否则重定向到 SSO 登录页。
     *
     * @param appId    应用对外标识（路径变量）
     * @param service  CAS {@code service} 参数
     * @param request  当前请求
     * @param response 当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "CAS 单点登录", description = "校验 service 白名单，已登录则签发服务票据并重定向，未登录则跳转 SSO 登录页")
    @GetMapping("/api/authn/cas/{appId}/login")
    public void login(@PathVariable String appId, @RequestParam String service, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            appProtocolGuard.assertCasServiceAllowed(appId, service);
        } catch (SsoProtocolException e) {
            ProtocolResponseWriter.text(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        String sessionToken = SsoSessionCookieUtils.extractSessionToken(request);
        Optional<Long> userIdOpt = ssoSessionService.verify(sessionToken);
        if (userIdOpt.isEmpty()) {
            ProtocolResponseWriter.redirect(response, ProtocolResponseWriter.ssoLoginRedirectLocation(request));
            return;
        }

        String ticket = casTicketService.issue(appId, service, userIdOpt.get());
        String separator = service.contains("?") ? "&" : "?";
        ProtocolResponseWriter.redirect(response, service + separator + "ticket=" + ticket);
    }

    /**
     * CAS 票据验证：校验并消费（一次性）票据，返回 CAS 3.0 格式的 XML 响应。
     *
     * @param appId    应用对外标识（路径变量，本端点不再重复校验，票据本身已绑定签发时的
     *                 应用与 {@code service}）
     * @param service  CAS {@code service} 参数
     * @param ticket   待验证的服务票据
     * @param response 当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "CAS 票据验证", description = "校验并一次性消费服务票据，返回 CAS 3.0 格式 XML 响应")
    @GetMapping("/api/authn/cas/{appId}/p3/serviceValidate")
    public void serviceValidate(@PathVariable String appId, @RequestParam String service,
            @RequestParam String ticket, HttpServletResponse response) throws IOException {
        Optional<CasTicketPayload> payloadOpt = casTicketService.consume(ticket);
        if (payloadOpt.isEmpty() || !Objects.equals(payloadOpt.get().service(), service)) {
            ProtocolResponseWriter.xml(response,
                    CasXmlResponses.failure("INVALID_TICKET", "Ticket 不存在、已过期或已被使用"));
            return;
        }

        UserEntity user = userMapper.selectById(payloadOpt.get().userId());
        if (user == null) {
            ProtocolResponseWriter.xml(response,
                    CasXmlResponses.failure("INVALID_TICKET", "Ticket 不存在、已过期或已被使用"));
            return;
        }
        ProtocolResponseWriter.xml(response, CasXmlResponses.success(user.getCode(), user.getName()));
    }

    /**
     * CAS 单点登出：清除当前浏览器持有的 SSO 会话，幂等（未持有会话时同样返回成功提示）。
     *
     * @param appId    应用对外标识（路径变量，登出不区分应用，仅用于保持路径结构一致）
     * @param request  当前请求
     * @param response 当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "CAS 单点登出", description = "清除当前浏览器持有的 SSO 会话，不做 back-channel 通知")
    @GetMapping("/api/authn/cas/{appId}/logout")
    public void logout(@PathVariable String appId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String sessionToken = SsoSessionCookieUtils.extractSessionToken(request);
        if (StringUtils.hasText(sessionToken)) {
            ssoSessionService.revoke(sessionToken);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, SsoSessionCookieUtils.buildClearCookieHeader(ssoProperties.isCookieSecure()));
        ProtocolResponseWriter.text(response, HttpServletResponse.SC_OK, "已登出");
    }
}
