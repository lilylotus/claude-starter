package cn.nihility.rbac.sso.controller;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.sso.support.AppProtocolGuard;
import cn.nihility.rbac.sso.support.ProtocolResponseWriter;
import cn.nihility.rbac.sso.support.SsoLogoutExecutor;
import cn.nihility.rbac.sso.support.SsoProtocolException;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogEventType;
import cn.nihility.rbac.ssoprotocollog.service.SsoProtocolLogRecorder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局单点登出接口（add-sso-single-logout change design.md Decision 5）：不依赖 CAS 票据
 * 流程，按 {@code appId} 解析应用当前的单点登录协议类型分派校验（CAS 校验 service 匹配列表，
 * OAuth2.0 校验 redirect_uri 匹配列表），校验通过后执行与 CAS 单点登出接口一致的登出逻辑，
 * 供 OAuth2.0 接入方、前端直接触发的"退出登录"等不经过 CAS 协议票据流程的场景统一调用。
 * 方法签名声明为 {@code void}，直接操作 {@link HttpServletResponse} 写重定向/纯文本响应
 * （同 {@code CasController}/{@code OAuthController} 既有模式，绕开
 * {@code GlobalResponseAdvice} 的响应包装）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "全局单点登出", description = "不依赖 CAS 票据流程，按应用协议类型校验 service/redirect_uri 后统一登出")
public class SsoLogoutController {

    /** 应用协议校验入口。 */
    private final AppProtocolGuard appProtocolGuard;

    /** 单点登出主流程公共执行组件。 */
    private final SsoLogoutExecutor ssoLogoutExecutor;

    /** SSO 协议调用记录组件（add-sso-protocol-access-log change design.md Decision 4）。 */
    private final SsoProtocolLogRecorder ssoProtocolLogRecorder;

    /**
     * 全局单点登出：按 {@code appId} 解析该应用的单点登录协议配置，依据其协议类型校验
     * {@code service} 是否匹配对应的匹配列表（CAS 匹配 service 列表，OAuth2.0 匹配
     * redirect_uri 列表），应用不存在、协议类型为"无"或不匹配任何规则时拒绝且不发生重定向、
     * 不清除会话、不触发回调通知。校验通过后撤销当前浏览器持有的 SSO 会话、清除会话 Cookie、
     * 触发一次单点登出后端回调通知（fire-and-forget，通知本次会话实际登录过的全部应用，
     * 不局限于路径上的 {@code appId}），最终 302 重定向到 {@code service}。
     *
     * @param appId    应用对外标识（路径变量）
     * @param service  回跳地址，须匹配该应用（按其协议类型）配置的匹配列表
     * @param request  当前请求
     * @param response 当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "全局单点登出", description = "按 appId 应用当前协议类型校验 service/redirect_uri，"
            + "撤销会话、清除 Cookie、触发登出后端回调通知后 302 重定向到 service")
    @GetMapping("/api/authn/{appId}/logout")
    public void logout(@PathVariable String appId, @RequestParam String service, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        AppAuthConfigEntity authConfig;
        try {
            authConfig = appProtocolGuard.assertLogoutServiceAllowed(appId, service);
        } catch (SsoProtocolException e) {
            Optional<AppAuthConfigEntity> resolved = appProtocolGuard.tryResolveAuthConfig(appId);
            String protocol = resolved.map(AppAuthConfigEntity::getAuthProtocol).orElse(AuthProtocol.NONE);
            Long appRefId = resolved.map(AppAuthConfigEntity::getAppRefId).orElse(null);
            ssoProtocolLogRecorder.recordFailure(protocol, SsoProtocolLogEventType.LOGOUT, appId, appRefId, null, null,
                    e.getMessage());
            ProtocolResponseWriter.text(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        ssoLogoutExecutor.execute(request, response, service, appId, authConfig.getAuthProtocol());
    }
}
