package cn.nihility.rbac.sso.controller;

import cn.nihility.rbac.auth.config.RbacLoginProperties;
import cn.nihility.rbac.auth.dto.PublicKeyVO;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.common.util.RsaJdkUtils;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.dto.SsoLoginRequest;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 专用登录接口（app-sso-protocol-runtime change design.md Decision 3）：完全独立于管理端
 * 登录（{@code AuthController}/{@code AuthServiceImpl}），账号密码校验通过后签发一个
 * HttpOnly 的浏览器 SSO 会话 Cookie，供 CAS/OAuth2 协议端点判断当前浏览器是否已登录。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "SSO 登录", description = "独立于管理端登录的 SSO 专用登录接口")
public class SsoLoginController {

    /** 登录失败的统一提示信息，不区分账号不存在/密码错误/账号不可用，避免信息泄露。 */
    private static final String LOGIN_FAILED_MESSAGE = "账号或密码不正确";

    /** 登录相关配置，复用其 RSA 密钥材料。 */
    private final RbacLoginProperties loginProperties;

    /** SSO 相关配置：会话 Cookie 是否要求 Secure、会话有效期。 */
    private final RbacSsoProperties ssoProperties;

    /** 用户数据访问接口，登录时按账号（用户编号）查询用户。 */
    private final UserMapper userMapper;

    /** 密码业务逻辑接口，复用其密码校验能力。 */
    private final PasswordService passwordService;

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /**
     * 获取当前生效的 RSA 公钥（与管理端登录复用同一份密钥材料）。
     *
     * @return 公钥信息
     */
    @Operation(summary = "获取 SSO 登录公钥", description = "客户端登录前使用该公钥对账号、密码分别加密，无需身份校验")
    @GetMapping("/api/authn/sso/public-key")
    public PublicKeyVO publicKey() {
        return PublicKeyVO.builder().publicKey(loginProperties.getPublicKey()).build();
    }

    /**
     * SSO 登录：校验通过后签发 SSO 会话并通过 HttpOnly Cookie 下发给浏览器。不做"首次登录
     * 强制改密"拦截——SSO 登录场景没有对应的强制改密页面，若账号处于待首登改密状态仍允许
     * SSO 登录成功。
     *
     * @param request  登录请求
     * @param response 当前响应，用于写出 Set-Cookie 响应头
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "SSO 登录", description = "账号、密码均为 RSA 公钥加密后的 Base64 密文，成功后通过 HttpOnly Cookie 下发 SSO 会话")
    @PostMapping("/api/authn/sso/login")
    public Result<Void> login(@Valid @RequestBody SsoLoginRequest request, HttpServletResponse response) {
        String account = decrypt(request.getAccount());
        String password = decrypt(request.getPassword());

        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getCode, account));
        if (user == null || !Objects.equals(user.getStatus(), UserStatus.ENABLED)) {
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }
        if (!passwordService.verifyPassword(user.getId(), password)) {
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }

        String token = ssoSessionService.issue(user.getId());
        response.addHeader(HttpHeaders.SET_COOKIE,
                SsoSessionCookieUtils.buildSetCookieHeader(token, ssoProperties.getSessionExpireSeconds(),
                        ssoProperties.isCookieSecure()));
        return Result.success();
    }

    /**
     * 使用配置的 RSA 私钥解密客户端提交的密文，解密失败（密文格式错误、密钥不匹配等）统一
     * 归一为登录失败提示，不向客户端暴露解密异常细节。
     *
     * @param cipherTextBase64 Base64 编码的 RSA 密文
     * @return 解密后的明文
     */
    private String decrypt(String cipherTextBase64) {
        try {
            return RsaJdkUtils.decrypt(cipherTextBase64, loginProperties.getPrivateKey());
        } catch (Exception e) {
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }
    }
}
