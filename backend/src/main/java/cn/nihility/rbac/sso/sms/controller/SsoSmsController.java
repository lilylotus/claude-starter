package cn.nihility.rbac.sso.sms.controller;

import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.loginlog.constant.LoginFailReason;
import cn.nihility.rbac.loginlog.constant.LoginMethod;
import cn.nihility.rbac.loginlog.service.LoginLogRecorder;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.dto.SsoLoginResponse;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionIdHasher;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sso.sms.dto.SmsCodeRequest;
import cn.nihility.rbac.sso.sms.dto.SmsLoginRequest;
import cn.nihility.rbac.sso.sms.service.SmsCodeService;
import cn.nihility.rbac.sso.support.SsoLoginContext;
import cn.nihility.rbac.sso.support.SsoLoginContextResolver;
import cn.nihility.rbac.sso.support.SsoMobileUserResolver;
import cn.nihility.rbac.user.entity.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 短信验证码登录接口：发送验证码、校验验证码并完成登录（add-sso-login-methods change
 * design.md Decision 4）。登录成功后与口令登录复用同一套 {@link SsoSessionService}
 * 会话签发 + Cookie 下发 + 首登标识判断 + 登录日志记录逻辑，风格对齐
 * {@code cn.nihility.rbac.sso.controller.SsoLoginController}。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "SSO 短信登录", description = "SSO 登录页短信验证码发送与登录接口")
public class SsoSmsController {

    /** 校验失败时对外统一返回的提示信息，不区分验证码错误/过期/手机号未匹配，避免信息泄露。 */
    private static final String LOGIN_FAILED_MESSAGE = "验证码不正确或已过期";

    /** SSO 登录上下文解析器，复用其解析结果再次校验目标应用是否允许短信登录。 */
    private final SsoLoginContextResolver ssoLoginContextResolver;

    /** 短信验证码业务逻辑接口。 */
    private final SmsCodeService smsCodeService;

    /** 手机号唯一匹配用户解析组件。 */
    private final SsoMobileUserResolver ssoMobileUserResolver;

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /** 密码业务逻辑接口，用于判断登录成功用户是否待首次登录改密。 */
    private final PasswordService passwordService;

    /** SSO 相关配置：会话 Cookie 是否要求 Secure、会话有效期。 */
    private final RbacSsoProperties ssoProperties;

    /** 登录日志记录组件。 */
    private final LoginLogRecorder loginLogRecorder;

    /**
     * 发送短信验证码：目标应用当前不允许短信登录时直接拒绝；通过限流校验后，无论手机号是否
     * 能唯一定位到一个可登录账号，均返回统一的成功响应（design.md Decision 4"防枚举优先"）。
     *
     * @param request 发送验证码请求
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "发送短信验证码", description = "目标应用需已开启短信登录；通过冷却/每日上限限流校验后统一返回成功，"
            + "不泄露手机号是否已注册")
    @PostMapping("/api/authn/sso/sms/code")
    public Result<Void> sendCode(@Valid @RequestBody SmsCodeRequest request) {
        assertSmsAllowed(request.getRedirect());
        smsCodeService.sendCode(request.getMobile());
        return Result.success();
    }

    /**
     * 短信验证码登录：验证码校验通过且手机号此刻唯一匹配一个启用状态账号时登录成功，签发
     * SSO 会话并通过 HttpOnly Cookie 下发。
     *
     * @param request  短信登录请求
     * @param response 当前响应，用于写出 Set-Cookie 响应头
     * @return SSO 登录结果
     */
    @Operation(summary = "短信验证码登录", description = "目标应用需已开启短信登录；验证码校验失败或手机号未唯一匹配可登录账号时，"
            + "统一返回“验证码不正确或已过期”，成功后通过 HttpOnly Cookie 下发 SSO 会话")
    @PostMapping("/api/authn/sso/sms/login")
    public Result<SsoLoginResponse> login(@Valid @RequestBody SmsLoginRequest request, HttpServletResponse response) {
        assertSmsAllowed(request.getRedirect());
        String mobile = request.getMobile();

        if (!smsCodeService.verifyCode(mobile, request.getCode())) {
            loginLogRecorder.recordFailure(mobile, null, null, LoginFailReason.SMS_CODE_MISMATCH, LoginMethod.SMS);
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }

        Optional<UserEntity> userOpt = ssoMobileUserResolver.resolveUniqueEnabledUser(mobile);
        if (userOpt.isEmpty()) {
            loginLogRecorder.recordFailure(mobile, null, null, LoginFailReason.MOBILE_NOT_MATCHED, LoginMethod.SMS);
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }

        UserEntity user = userOpt.get();
        String token = ssoSessionService.issue(user.getId());
        loginLogRecorder.recordSuccess(mobile, user.getId(), user.getName(), SsoSessionIdHasher.hash(token),
                LoginMethod.SMS);
        response.addHeader(HttpHeaders.SET_COOKIE,
                SsoSessionCookieUtils.buildSetCookieHeader(token, ssoProperties.getSessionExpireSeconds(),
                        ssoProperties.isCookieSecure()));
        return Result.success(SsoLoginResponse.builder()
                .firstLogin(passwordService.isFirstLogin(user.getId()))
                .build());
    }

    /**
     * 校验目标应用当前是否允许短信登录，不允许时拒绝；不能只依赖前端是否展示短信 Tab 把关，
     * 需在接口层再次校验一遍（design.md Decision 2）。
     *
     * @param redirect SSO 登录页 {@code redirect} 参数原始值
     */
    private void assertSmsAllowed(String redirect) {
        SsoLoginContext context = ssoLoginContextResolver.resolve(redirect);
        if (!context.allows(LoginMethod.SMS)) {
            throw new BusinessException("该应用未开启短信验证码登录");
        }
    }
}
