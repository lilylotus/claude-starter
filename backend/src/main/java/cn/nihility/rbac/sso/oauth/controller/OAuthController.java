package cn.nihility.rbac.sso.oauth.controller;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.common.util.ClientRequestUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sso.oauth.dto.IssuedToken;
import cn.nihility.rbac.sso.oauth.dto.OAuthCodePayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthRefreshPayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthTokenPayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthTokenRequest;
import cn.nihility.rbac.sso.oauth.service.OAuthTokenService;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionIdHasher;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sso.support.AppAccessAuthorizationChecker;
import cn.nihility.rbac.sso.support.AppProtocolGuard;
import cn.nihility.rbac.sso.support.ProtocolResponseWriter;
import cn.nihility.rbac.sso.support.SsoProtocolException;
import cn.nihility.rbac.sso.support.SsoUserinfoAttributesResolver;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogEventType;
import cn.nihility.rbac.ssoprotocollog.service.SsoProtocolLogRecorder;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2.0 协议运行时端点（app-sso-protocol-runtime change design.md Decision 6）：
 * {@code /authorize}、{@code /token}、{@code /userinfo}。三个端点均绕开
 * {@code GlobalResponseAdvice} 的响应包装，方法签名声明为 {@code void}，直接操作
 * {@link HttpServletResponse} 写重定向/JSON 响应（design.md Decision 4）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "OAuth2.0 单点登录", description = "OAuth2.0 协议运行时端点：授权、令牌签发/刷新、用户信息查询")
public class OAuthController {

    /** {@code Authorization} 请求头 Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 应用协议校验入口。 */
    private final AppProtocolGuard appProtocolGuard;

    /** 应用访问授权校验入口（app-access-authorization change）。 */
    private final AppAccessAuthorizationChecker appAccessAuthorizationChecker;

    /** OAuth2 令牌业务逻辑接口。 */
    private final OAuthTokenService oAuthTokenService;

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /** 密码业务逻辑接口，用于阻止待首次登录改密账号获取 OAuth2 授权码。 */
    private final PasswordService passwordService;

    /** 应用对外接口凭证相关配置，提供 SM4 解密主密钥（校验 client_secret 时复用）。 */
    private final AppSecretProperties appSecretProperties;

    /** 用户数据访问接口，userinfo 端点按用户 id 查询用户标识/姓名。 */
    private final UserMapper userMapper;

    /** 用户信息响应属性运行时解析组件，按应用配置的字段映射生成 userinfo 响应字段。 */
    private final SsoUserinfoAttributesResolver ssoUserinfoAttributesResolver;

    /** SSO 协议调用记录组件（add-sso-protocol-access-log change design.md Decision 4）。 */
    private final SsoProtocolLogRecorder ssoProtocolLogRecorder;

    /**
     * OAuth2 授权：{@code redirect_uri} 校验通过后，若 {@code response_type} 非
     * {@code code} 则把错误原样重定向回 {@code redirect_uri}；若当前浏览器持有有效 SSO
     * 会话则签发授权码并重定向回 {@code redirect_uri}，否则重定向到 SSO 登录页。授权校验
     * 读取当前请求的客户端 IP（{@link ClientRequestUtils#resolveClientIp}）与
     * {@code User-Agent}，纳入"考虑请求上下文"的最终生效权限判定
     * （app-access-request-control change design.md Decision 6）。
     *
     * @param responseType 期望固定为 {@code code}
     * @param clientId     OAuth2 client_id（即应用对外标识）
     * @param redirectUri  回跳地址
     * @param scope        授权范围，原样透传，不做过滤
     * @param state        客户端自定义状态值，原样透传
     * @param request      当前请求
     * @param response     当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "OAuth2 授权", description = "校验 redirect_uri 白名单，已登录则签发授权码并重定向，未登录则跳转 SSO 登录页")
    @GetMapping("/api/authn/oauth/authorize")
    public void authorize(@RequestParam("response_type") String responseType, @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri, @RequestParam(required = false) String scope,
            @RequestParam(required = false) String state, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            appProtocolGuard.assertOAuthRedirectUriAllowed(clientId, redirectUri);
        } catch (SsoProtocolException e) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.AUTHORIZE, clientId,
                    appProtocolGuard.resolveAppRefIdOrNull(clientId), null, null, e.getMessage(), null);
            ProtocolResponseWriter.text(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        if (!"code".equals(responseType)) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.AUTHORIZE, clientId,
                    appProtocolGuard.resolveAppRefIdOrNull(clientId), null, null, "response_type 不支持", null);
            String location = redirectUri + (redirectUri.contains("?") ? "&" : "?") + "error=unsupported_response_type"
                    + (StringUtils.hasText(state) ? "&state=" + state : "");
            ProtocolResponseWriter.redirect(response, location);
            return;
        }

        String sessionToken = SsoSessionCookieUtils.extractSessionToken(request);
        Optional<Long> userIdOpt = ssoSessionService.verify(sessionToken);
        if (userIdOpt.isEmpty()) {
            ProtocolResponseWriter.redirect(response, ProtocolResponseWriter.ssoLoginRedirectLocation(request));
            return;
        }
        if (passwordService.isFirstLogin(userIdOpt.get())) {
            ProtocolResponseWriter.redirect(response,
                    ProtocolResponseWriter.ssoLoginRedirectLocation(request, true));
            return;
        }

        String sessionId = SsoSessionIdHasher.hash(sessionToken);
        Long appRefId = appProtocolGuard.resolveAppRefId(clientId);
        try {
            String clientIp = ClientRequestUtils.resolveClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            appAccessAuthorizationChecker.assertAuthorized(userIdOpt.get(), appRefId, clientIp, userAgent);
        } catch (SsoProtocolException e) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.AUTHORIZE, clientId,
                    appRefId, userIdOpt.get(), sessionId, e.getMessage(), e.getDeniedByPolicyId());
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_FORBIDDEN,
                    Result.error(HttpServletResponse.SC_FORBIDDEN, e.getMessage()));
            return;
        }

        String code = oAuthTokenService.issueCode(clientId, redirectUri, userIdOpt.get(), scope, sessionToken);
        ssoProtocolLogRecorder.recordSuccess(AuthProtocol.OAUTH2, SsoProtocolLogEventType.AUTHORIZE, clientId, appRefId,
                userIdOpt.get(), sessionId);
        String separator = redirectUri.contains("?") ? "&" : "?";
        String location = redirectUri + separator + "code=" + code + (StringUtils.hasText(state) ? "&state=" + state : "");
        ProtocolResponseWriter.redirect(response, location);
    }

    /**
     * OAuth2 令牌签发/刷新：按 {@code grant_type} 分支处理 {@code authorization_code}
     * 与 {@code refresh_token} 两种授权类型，返回标准 OAuth2 JSON 响应。
     *
     * @param clientId     client_id，{@code authorization_code} 授权类型下必填
     * @param clientSecret client_secret，{@code authorization_code} 授权类型下必填
     * @param redirectUri  redirect_uri，{@code authorization_code} 授权类型下必填
     * @param grantType    授权类型：{@code authorization_code} 或 {@code refresh_token}
     * @param code         授权码，{@code authorization_code} 授权类型下必填
     * @param refreshToken refresh token，{@code refresh_token} 授权类型下必填
     * @param response     当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "OAuth2 令牌签发/刷新",
            description = "grant_type=authorization_code 签发 access token 与 refresh token；"
                    + "grant_type=refresh_token 轮转刷新：旧 refresh token 立即一次性消费失效，"
                    + "签发新的 access token 与新的 refresh token（拥有完整有效期），响应体新增返回 refresh_token")
    @PostMapping("/api/authn/oauth/token")
    public void token(@RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(required = false) String code,
            @RequestParam(name = "refresh_token", required = false) String refreshToken, HttpServletResponse response)
            throws IOException {
        OAuthTokenRequest tokenRequest = OAuthTokenRequest.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .grantType(grantType)
                .code(code)
                .refreshToken(refreshToken)
                .build();

        if ("authorization_code".equals(tokenRequest.getGrantType())) {
            handleAuthorizationCodeGrant(tokenRequest, response);
        } else if ("refresh_token".equals(tokenRequest.getGrantType())) {
            handleRefreshTokenGrant(tokenRequest, response);
        } else {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                    tokenRequest.getClientId(), appProtocolGuard.resolveAppRefIdOrNull(tokenRequest.getClientId()),
                    null, null, "grant_type 不支持", null);
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("unsupported_grant_type"));
        }
    }

    /**
     * OAuth2 用户信息查询：解析 {@code Authorization: Bearer <access_token>} 请求头，
     * 校验通过后返回该令牌绑定用户的基本身份信息。除固定的 {@code sub}（取用户 id，不受
     * 字段映射配置影响，见 add-sso-userinfo-field-mapping change design.md Decision 3）外，
     * 其余字段按该应用配置的用户信息字段映射动态生成；写入顺序上先写入映射字段、再写入
     * {@code sub}，即使某行映射的应用侧字段编码恰好配置成 {@code sub}，最终仍以固定值为准。
     *
     * @param authorization {@code Authorization} 请求头原始值
     * @param response      当前响应
     * @throws IOException 写响应失败
     */
    @Operation(summary = "OAuth2 用户信息查询", description = "校验 Authorization: Bearer <access_token>，返回绑定用户的基本身份信息；"
            + "除固定的 sub 外，其余字段按应用配置的用户信息字段映射动态生成")
    @GetMapping("/api/authn/oauth/userinfo")
    public void userinfo(@RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response) throws IOException {
        String token = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim()
                : null;
        if (!StringUtils.hasText(token)) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.USERINFO, null, null,
                    null, null, "access_token 无效或缺失", null);
            writeUnauthorized(response);
            return;
        }

        Optional<OAuthTokenPayload> payloadOpt = oAuthTokenService.verifyAccessToken(token);
        if (payloadOpt.isEmpty()) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.USERINFO, null, null,
                    null, null, "access_token 无效或缺失", null);
            writeUnauthorized(response);
            return;
        }

        OAuthTokenPayload payload = payloadOpt.get();
        Long userId = payload.userId();
        String sessionId = SsoSessionIdHasher.hash(payload.sessionToken());
        UserEntity user = userMapper.selectById(userId);
        Long appRefId = appProtocolGuard.resolveAppRefIdOrNull(payload.clientId());
        Map<String, Object> body = new LinkedHashMap<>();
        if (user != null && appRefId != null) {
            body.putAll(ssoUserinfoAttributesResolver.resolve(appRefId, user));
        }
        body.put("sub", String.valueOf(userId));
        ssoProtocolLogRecorder.recordSuccess(AuthProtocol.OAUTH2, SsoProtocolLogEventType.USERINFO, payload.clientId(),
                appRefId, userId, sessionId);
        ProtocolResponseWriter.json(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * 处理 {@code grant_type=authorization_code} 分支。
     *
     * @param tokenRequest 令牌请求参数
     * @param response     当前响应
     * @throws IOException 写响应失败
     */
    private void handleAuthorizationCodeGrant(OAuthTokenRequest tokenRequest, HttpServletResponse response)
            throws IOException {
        if (!StringUtils.hasText(tokenRequest.getClientId()) || !StringUtils.hasText(tokenRequest.getClientSecret())
                || !StringUtils.hasText(tokenRequest.getRedirectUri()) || !StringUtils.hasText(tokenRequest.getCode())) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                    tokenRequest.getClientId(), appProtocolGuard.resolveAppRefIdOrNull(tokenRequest.getClientId()),
                    null, null, "invalid_request：缺少必要参数", null);
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_request"));
            return;
        }

        AppConfigEntity appConfig;
        try {
            appConfig = appProtocolGuard.resolveOAuthClientConfig(tokenRequest.getClientId());
        } catch (SsoProtocolException e) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                    tokenRequest.getClientId(), appProtocolGuard.resolveAppRefIdOrNull(tokenRequest.getClientId()),
                    null, null, "invalid_client：client_id 不存在或未开启 OAuth2.0 单点登录协议", null);
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_UNAUTHORIZED, errorBody("invalid_client"));
            return;
        }

        String plainSecretKey = Sm4JdkUtils.decrypt(appConfig.getSecretKey(), appSecretProperties.getSm4Key());
        boolean secretMatches = MessageDigest.isEqual(tokenRequest.getClientSecret().getBytes(StandardCharsets.UTF_8),
                plainSecretKey.getBytes(StandardCharsets.UTF_8));
        if (!secretMatches) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                    tokenRequest.getClientId(), appConfig.getAppRefId(), null, null,
                    "invalid_client：client_secret 不匹配", null);
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_UNAUTHORIZED, errorBody("invalid_client"));
            return;
        }

        Optional<OAuthCodePayload> payloadOpt = oAuthTokenService.consumeCode(tokenRequest.getCode());
        if (payloadOpt.isEmpty() || !Objects.equals(payloadOpt.get().clientId(), tokenRequest.getClientId())
                || !Objects.equals(payloadOpt.get().redirectUri(), tokenRequest.getRedirectUri())) {
            Long userId = payloadOpt.map(OAuthCodePayload::userId).orElse(null);
            String sessionId = payloadOpt.map(OAuthCodePayload::ssoSessionToken).map(SsoSessionIdHasher::hash)
                    .orElse(null);
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                    tokenRequest.getClientId(), appConfig.getAppRefId(), userId, sessionId,
                    "invalid_grant：授权码不存在、已过期或与请求参数不一致", null);
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_grant"));
            return;
        }

        OAuthCodePayload payload = payloadOpt.get();
        String sessionId = SsoSessionIdHasher.hash(payload.ssoSessionToken());
        IssuedToken issuedToken = oAuthTokenService.issueAccessTokenWithRefresh(tokenRequest.getClientId(),
                payload.userId(), payload.scope(), payload.ssoSessionToken());
        ssoProtocolLogRecorder.recordSuccess(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                tokenRequest.getClientId(), appConfig.getAppRefId(), payload.userId(), sessionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", issuedToken.accessToken());
        body.put("token_type", OAuthTokenService.TOKEN_TYPE_BEARER);
        body.put("expires_in", issuedToken.expiresIn());
        body.put("refresh_token", issuedToken.refreshToken());
        ProtocolResponseWriter.json(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * 处理 {@code grant_type=refresh_token} 分支：只要求 {@code refresh_token}/
     * {@code grant_type} 两个参数，不校验 {@code client_id}/{@code client_secret}
     * （design.md Decision 6/Risks 已说明这是按用户明确给出的参数列表实现的结果）。校验通过
     * 后按"轮转"模式刷新：旧 {@code refresh_token} 立即一次性消费失效，签发新的 access token
     * 与新的 {@code refresh_token}（拥有完整的配置有效期），响应体新增返回
     * {@code refresh_token} 字段，调用方 SHALL 用该新值替换本地保存的旧值（**BREAKING**，
     * add-sso-single-logout change design.md Decision 6）。
     *
     * @param tokenRequest 令牌请求参数
     * @param response     当前响应
     * @throws IOException 写响应失败
     */
    private void handleRefreshTokenGrant(OAuthTokenRequest tokenRequest, HttpServletResponse response)
            throws IOException {
        if (!StringUtils.hasText(tokenRequest.getRefreshToken())) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                    tokenRequest.getClientId(), appProtocolGuard.resolveAppRefIdOrNull(tokenRequest.getClientId()),
                    null, null, "invalid_request：缺少必要参数", null);
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_request"));
            return;
        }

        Optional<OAuthRefreshPayload> payloadOpt = oAuthTokenService.verifyRefreshToken(tokenRequest.getRefreshToken());
        if (payloadOpt.isEmpty()) {
            ssoProtocolLogRecorder.recordFailure(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN,
                    tokenRequest.getClientId(), appProtocolGuard.resolveAppRefIdOrNull(tokenRequest.getClientId()),
                    null, null, "invalid_grant：refresh_token 不存在或已过期", null);
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_grant"));
            return;
        }

        OAuthRefreshPayload payload = payloadOpt.get();
        String sessionId = SsoSessionIdHasher.hash(payload.sessionToken());
        IssuedToken issuedToken = oAuthTokenService.rotateAccessAndRefreshToken(tokenRequest.getRefreshToken(),
                payload.clientId(), payload.userId(), payload.scope(), payload.sessionToken());
        ssoProtocolLogRecorder.recordSuccess(AuthProtocol.OAUTH2, SsoProtocolLogEventType.TOKEN, payload.clientId(),
                appProtocolGuard.resolveAppRefIdOrNull(payload.clientId()), payload.userId(), sessionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", issuedToken.accessToken());
        body.put("token_type", OAuthTokenService.TOKEN_TYPE_BEARER);
        body.put("expires_in", issuedToken.expiresIn());
        body.put("refresh_token", issuedToken.refreshToken());
        ProtocolResponseWriter.json(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * userinfo 端点未携带有效 access token 时统一写出的 401 响应，附加
     * {@code WWW-Authenticate: Bearer} 响应头。
     *
     * @param response 当前响应
     * @throws IOException 写响应失败
     */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        ProtocolResponseWriter.json(response, HttpServletResponse.SC_UNAUTHORIZED, errorBody("invalid_token"));
    }

    /**
     * 构造标准 OAuth2 错误响应体。
     *
     * @param error OAuth2 错误码
     * @return 错误响应体
     */
    private Map<String, String> errorBody(String error) {
        return Map.of("error", error);
    }
}
