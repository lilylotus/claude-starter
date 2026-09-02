package cn.nihility.rbac.sso.qrcode.controller;

import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.loginlog.constant.LoginMethod;
import cn.nihility.rbac.loginlog.service.LoginLogRecorder;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.qrcode.constant.QrcodeSessionStatus;
import cn.nihility.rbac.sso.qrcode.dto.QrcodeSessionCreateRequest;
import cn.nihility.rbac.sso.qrcode.dto.QrcodeSessionPayload;
import cn.nihility.rbac.sso.qrcode.dto.QrcodeSessionVO;
import cn.nihility.rbac.sso.qrcode.dto.QrcodeStatusVO;
import cn.nihility.rbac.sso.qrcode.service.QrcodeSessionService;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionIdHasher;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sso.support.SsoLoginContext;
import cn.nihility.rbac.sso.support.SsoLoginContextResolver;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 扫码登录接口：PC 端创建会话/轮询状态，手机浏览器标记扫码/确认登录
 * （add-sso-login-methods change design.md Decision 5）。PC 端轮询命中"已确认"状态时
 * 在该次响应中为 PC 浏览器签发一套独立的 SSO 会话，与手机端各自的会话互不影响
 * （与口令、短信登录最终产出完全一致，同套 {@link SsoSessionService}）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "SSO 扫码登录", description = "SSO 登录页扫码登录会话创建、状态轮询，及手机浏览器确认页扫码标记/确认接口")
public class SsoQrcodeController {

    /** SSO 登录上下文解析器。 */
    private final SsoLoginContextResolver ssoLoginContextResolver;

    /** 二维码登录会话状态机业务逻辑接口。 */
    private final QrcodeSessionService qrcodeSessionService;

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /** 密码业务逻辑接口，用于判断登录成功用户是否待首次登录改密。 */
    private final PasswordService passwordService;

    /** SSO 相关配置：会话 Cookie 是否要求 Secure、会话有效期。 */
    private final RbacSsoProperties ssoProperties;

    /** 登录日志记录组件。 */
    private final LoginLogRecorder loginLogRecorder;

    /** 用户数据访问接口，PC 端签发会话时按用户 id 查询用户标识/姓名用于登录日志。 */
    private final UserMapper userMapper;

    /**
     * 创建一个二维码登录会话：目标应用当前不允许扫码登录时拒绝。
     *
     * @param request 创建会话请求
     * @return 会话令牌与确认页相对路径
     */
    @Operation(summary = "创建二维码登录会话", description = "目标应用需已开启扫码登录，返回会话令牌与扫码确认页相对路径（前端自行拼接 origin）")
    @PostMapping("/api/authn/sso/qrcode/session")
    public QrcodeSessionVO createSession(@Valid @RequestBody QrcodeSessionCreateRequest request) {
        SsoLoginContext context = ssoLoginContextResolver.resolve(request.getRedirect());
        if (!context.allows(LoginMethod.QRCODE)) {
            throw new BusinessException("该应用未开启扫码登录");
        }
        String token = qrcodeSessionService.create(request.getRedirect(), context.appId());
        return QrcodeSessionVO.builder()
                .token(token)
                .confirmPath("/sso/qrcode/confirm?token=" + token)
                .build();
    }

    /**
     * PC 端轮询二维码登录会话状态。命中"已确认"状态且是本次首次读到时，为当前发起轮询的
     * PC 浏览器签发 SSO 会话并写出 Set-Cookie，随后立即把会话标记为已消费，防止重复签发；
     * 非首次读到"已确认"（即已被消费）时对外展示为已过期。
     *
     * @param token    会话令牌
     * @param response 当前响应，命中首次"已确认"时用于写出 Set-Cookie 响应头
     * @return 会话状态查询结果
     */
    @Operation(summary = "查询二维码登录会话状态", description = "PENDING/SCANNED/CONFIRMED/EXPIRED 四选一；首次轮询到 CONFIRMED 时"
            + "为当前浏览器签发 SSO 会话并下发 Cookie，之后同一令牌的后续查询一律返回 EXPIRED")
    @GetMapping("/api/authn/sso/qrcode/{token}/status")
    public QrcodeStatusVO status(@PathVariable String token, HttpServletResponse response) {
        Optional<QrcodeSessionPayload> payloadOpt = qrcodeSessionService.find(token);
        if (payloadOpt.isEmpty()) {
            return QrcodeStatusVO.builder().status(QrcodeSessionStatus.EXPIRED).build();
        }

        QrcodeSessionPayload payload = payloadOpt.get();
        if (!QrcodeSessionStatus.CONFIRMED.equals(payload.getStatus())) {
            return QrcodeStatusVO.builder().status(payload.getStatus()).build();
        }
        if (payload.isConsumed()) {
            return QrcodeStatusVO.builder().status(QrcodeSessionStatus.EXPIRED).build();
        }

        Long userId = payload.getUserId();
        String sessionToken = ssoSessionService.issue(userId);
        boolean firstLogin = passwordService.isFirstLogin(userId);
        UserEntity user = userMapper.selectById(userId);
        loginLogRecorder.recordSuccess(user != null ? user.getCode() : null, userId,
                user != null ? user.getName() : null, SsoSessionIdHasher.hash(sessionToken), LoginMethod.QRCODE);
        response.addHeader(HttpHeaders.SET_COOKIE,
                SsoSessionCookieUtils.buildSetCookieHeader(sessionToken, ssoProperties.getSessionExpireSeconds(),
                        ssoProperties.isCookieSecure()));
        qrcodeSessionService.markConsumed(token, payload);

        return QrcodeStatusVO.builder().status(QrcodeSessionStatus.CONFIRMED).firstLogin(firstLogin).build();
    }

    /**
     * 手机浏览器扫码后标记状态，幂等、令牌无效或状态不是"待扫码"时静默忽略，接口始终返回成功。
     *
     * @param token 会话令牌
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "标记已扫码", description = "手机浏览器扫码打开确认页后调用，把状态从 PENDING 置为 SCANNED；"
            + "令牌无效或状态不是 PENDING 时静默忽略，接口始终返回成功")
    @PostMapping("/api/authn/sso/qrcode/{token}/scan")
    public Result<Void> scan(@PathVariable String token) {
        qrcodeSessionService.markScanned(token);
        return Result.success();
    }

    /**
     * 手机浏览器确认登录：要求当前请求携带有效 SSO 会话 Cookie（手机浏览器自身已登录），
     * 且目标应用当前仍允许扫码登录、令牌未过期/未被消费。此步骤不为 PC 端签发会话——手机端
     * 与 PC 端是两个独立浏览器，Cookie 无法跨端下发，PC 端会话由 {@link #status} 轮询接口
     * 在下一次命中"已确认"状态时签发。
     *
     * @param token   会话令牌
     * @param request 当前请求，用于读取手机浏览器自身的 SSO 会话 Cookie
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "确认登录", description = "要求当前请求携带有效 SSO 会话 Cookie，校验令牌状态为 PENDING/SCANNED 后置为 "
            + "CONFIRMED，不为当前浏览器签发会话")
    @PostMapping("/api/authn/sso/qrcode/{token}/confirm")
    public Result<Void> confirm(@PathVariable String token, HttpServletRequest request) {
        QrcodeSessionPayload payload = qrcodeSessionService.find(token)
                .orElseThrow(() -> new BusinessException("二维码已失效，请刷新后重试"));
        SsoLoginContext context = ssoLoginContextResolver.resolve(payload.getRedirect());
        if (!context.allows(LoginMethod.QRCODE)) {
            throw new BusinessException("该应用未开启扫码登录");
        }

        String sessionToken = SsoSessionCookieUtils.extractSessionToken(request);
        Long userId = ssoSessionService.verify(sessionToken)
                .orElseThrow(() -> new BusinessException("需要先完成登录"));
        qrcodeSessionService.confirm(token, userId);
        return Result.success();
    }
}
