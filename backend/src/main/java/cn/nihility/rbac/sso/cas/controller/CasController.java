package cn.nihility.rbac.sso.cas.controller;

import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.common.util.ClientRequestUtils;
import cn.nihility.rbac.sso.cas.dto.CasTicketPayload;
import cn.nihility.rbac.sso.cas.service.CasTicketService;
import cn.nihility.rbac.sso.cas.support.CasJsonResponses;
import cn.nihility.rbac.sso.cas.support.CasXmlResponses;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sso.support.AppAccessAuthorizationChecker;
import cn.nihility.rbac.sso.support.AppProtocolGuard;
import cn.nihility.rbac.sso.support.ProtocolResponseWriter;
import cn.nihility.rbac.sso.support.SsoLogoutExecutor;
import cn.nihility.rbac.sso.support.SsoProtocolException;
import cn.nihility.rbac.sso.support.SsoUserinfoAttributesResolver;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CAS 协议运行时端点（app-sso-protocol-runtime change design.md Decision 5）：
 * {@code /login}、{@code /p3/serviceValidate}、{@code /logout}。三个端点均绕开
 * {@code GlobalResponseAdvice} 的响应包装，方法签名声明为 {@code void}，直接操作
 * {@link HttpServletResponse} 写重定向/XML/JSON/纯文本响应（design.md Decision 4；
 * {@code serviceValidate} 支持 {@code format} 参数返回 XML/JSON 见
 * add-sso-userinfo-field-mapping change design.md Decision 1）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "CAS 单点登录", description = "CAS 协议运行时端点：登录、票据验证、登出")
public class CasController {

    /** {@code format} 参数标准化后表示"返回 XML"的取值，其余（含缺省）一律按 JSON 处理。 */
    private static final String FORMAT_XML = "XML";

    /** 应用协议校验入口。 */
    private final AppProtocolGuard appProtocolGuard;

    /** 应用访问授权校验入口（app-access-authorization change）。 */
    private final AppAccessAuthorizationChecker appAccessAuthorizationChecker;

    /** CAS 服务票据业务逻辑接口。 */
    private final CasTicketService casTicketService;

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /** 单点登出主流程公共执行组件，登出接口复用其撤销会话/清 Cookie/触发通知/302 重定向逻辑。 */
    private final SsoLogoutExecutor ssoLogoutExecutor;

    /** 用户数据访问接口，票据验证成功后按用户 id 查询用户标识/姓名。 */
    private final UserMapper userMapper;

    /** 用户信息响应属性运行时解析组件，按应用配置的字段映射生成 {@code cas:attributes}。 */
    private final SsoUserinfoAttributesResolver ssoUserinfoAttributesResolver;

    /**
     * CAS 单点登录：{@code service} 校验通过后，若当前浏览器持有有效 SSO 会话则签发服务
     * 票据并重定向回 {@code service}；否则重定向到 SSO 登录页。授权校验读取当前请求的
     * 客户端 IP（{@link ClientRequestUtils#resolveClientIp}）与 {@code User-Agent}，
     * 纳入"考虑请求上下文"的最终生效权限判定（app-access-request-control change
     * design.md Decision 6）。
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

        try {
            String clientIp = ClientRequestUtils.resolveClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            appAccessAuthorizationChecker.assertAuthorized(userIdOpt.get(), appProtocolGuard.resolveAppRefId(appId),
                    clientIp, userAgent);
        } catch (SsoProtocolException e) {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_FORBIDDEN,
                    Result.error(HttpServletResponse.SC_FORBIDDEN, e.getMessage()));
            return;
        }

        String ticket = casTicketService.issue(appId, service, userIdOpt.get(), sessionToken);
        String separator = service.contains("?") ? "&" : "?";
        ProtocolResponseWriter.redirect(response, service + separator + "ticket=" + ticket);
    }

    /**
     * CAS 票据验证：校验并消费（一次性）票据，按 {@code format} 参数返回 CAS 3.0 格式的
     * XML 或 JSON 响应（大小写不敏感，仅 {@code format=XML} 时返回 XML，其余（含缺省）一律
     * 返回 JSON，默认值变更见 design.md Decision 1）。用户属性（{@code cas:attributes}/JSON
     * 对应节点）按该应用配置的用户信息字段映射动态生成。
     *
     * @param appId    应用对外标识（路径变量，本端点不再重复校验；解析应用 id 以查询用户信息
     *                 字段映射配置时改用票据自身绑定的 {@code appId}，见方法内 {@code payload}）
     * @param service  CAS {@code service} 参数
     * @param ticket   待验证的服务票据
     * @param format   响应格式，大小写不敏感，仅 {@code XML} 时返回 XML，其余（含缺省）返回 JSON
     * @param response 当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "CAS 票据验证", description = "校验并一次性消费服务票据，按 format 参数返回 CAS 3.0 格式 XML（format=XML）或 "
            + "JSON（缺省/其它取值，默认 JSON）响应，属性按应用配置的用户信息字段映射动态生成")
    @GetMapping("/api/authn/cas/{appId}/p3/serviceValidate")
    public void serviceValidate(@PathVariable String appId, @RequestParam String service,
            @RequestParam String ticket, @RequestParam(required = false) String format, HttpServletResponse response)
            throws IOException {
        boolean useXml = FORMAT_XML.equals(format == null ? null : format.trim().toUpperCase());

        Optional<CasTicketPayload> payloadOpt = casTicketService.consume(ticket);
        if (payloadOpt.isEmpty() || !Objects.equals(payloadOpt.get().service(), service)) {
            writeFailure(response, useXml);
            return;
        }

        CasTicketPayload payload = payloadOpt.get();
        UserEntity user = userMapper.selectById(payload.userId());
        if (user == null) {
            writeFailure(response, useXml);
            return;
        }

        Long appRefId = appProtocolGuard.resolveAppRefId(payload.appId());
        Map<String, Object> attributes = ssoUserinfoAttributesResolver.resolve(appRefId, user);
        if (useXml) {
            ProtocolResponseWriter.xml(response, CasXmlResponses.success(user.getCode(), attributes));
        } else {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_OK,
                    CasJsonResponses.success(user.getCode(), attributes));
        }
    }

    /**
     * 按 {@code format} 写出票据验证失败响应（XML 或 JSON）。
     *
     * @param response 当前响应
     * @param useXml   {@code true} 写 XML，{@code false} 写 JSON
     * @throws IOException 写响应失败
     */
    private void writeFailure(HttpServletResponse response, boolean useXml) throws IOException {
        String code = "INVALID_TICKET";
        String message = "Ticket 不存在、已过期或已被使用";
        if (useXml) {
            ProtocolResponseWriter.xml(response, CasXmlResponses.failure(code, message));
        } else {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_OK, CasJsonResponses.failure(code, message));
        }
    }

    /**
     * CAS 单点登出（**BREAKING**：新增必填 {@code service} 参数，不再直接返回登出成功文本）：
     * {@code service} 校验通过后，撤销当前浏览器持有的 SSO 会话、清除会话 Cookie、触发一次
     * 单点登出后端回调通知（fire-and-forget，不等待结果，通知本次会话实际登录过的全部应用），
     * 最终 302 重定向到 {@code service}（add-sso-single-logout change design.md Decision 4）。
     *
     * @param appId    应用对外标识（路径变量）
     * @param service  CAS {@code service} 参数，须匹配该应用配置的 service ANT 匹配列表
     * @param request  当前请求
     * @param response 当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "CAS 单点登出", description = "校验 service 白名单，撤销会话、清除 Cookie、触发登出后端回调通知后 302 重定向到 service")
    @GetMapping("/api/authn/cas/{appId}/logout")
    public void logout(@PathVariable String appId, @RequestParam String service, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            appProtocolGuard.assertCasServiceAllowed(appId, service);
        } catch (SsoProtocolException e) {
            ProtocolResponseWriter.text(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        ssoLogoutExecutor.execute(request, response, service);
    }
}
