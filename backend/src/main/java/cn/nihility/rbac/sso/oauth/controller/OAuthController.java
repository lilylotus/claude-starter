package cn.nihility.rbac.sso.oauth.controller;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.oauth.dto.IssuedToken;
import cn.nihility.rbac.sso.oauth.dto.OAuthCodePayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthRefreshPayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthTokenPayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthTokenRequest;
import cn.nihility.rbac.sso.oauth.service.OAuthTokenService;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sso.support.AppProtocolGuard;
import cn.nihility.rbac.sso.support.ProtocolResponseWriter;
import cn.nihility.rbac.sso.support.SsoProtocolException;
import cn.nihility.rbac.sso.support.SsoUserinfoAttributesResolver;
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

    /** OAuth2 令牌业务逻辑接口。 */
    private final OAuthTokenService oAuthTokenService;

    /** SSO 浏览器会话业务逻辑接口。 */
    private final SsoSessionService ssoSessionService;

    /** SSO 相关配置：{@code refresh_token} 授权分支响应体 {@code expires_in} 取值。 */
    private final RbacSsoProperties ssoProperties;

    /** 应用对外接口凭证相关配置，提供 SM4 解密主密钥（校验 client_secret 时复用）。 */
    private final AppSecretProperties appSecretProperties;

    /** 用户数据访问接口，userinfo 端点按用户 id 查询用户标识/姓名。 */
    private final UserMapper userMapper;

    /** 用户信息响应属性运行时解析组件，按应用配置的字段映射生成 userinfo 响应字段。 */
    private final SsoUserinfoAttributesResolver ssoUserinfoAttributesResolver;

    /**
     * OAuth2 授权：{@code redirect_uri} 校验通过后，若 {@code response_type} 非
     * {@code code} 则把错误原样重定向回 {@code redirect_uri}；若当前浏览器持有有效 SSO
     * 会话则签发授权码并重定向回 {@code redirect_uri}，否则重定向到 SSO 登录页。
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
            ProtocolResponseWriter.text(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        if (!"code".equals(responseType)) {
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

        String code = oAuthTokenService.issueCode(clientId, redirectUri, userIdOpt.get(), scope);
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
                    + "grant_type=refresh_token 只签发新的 access token，refresh token 本身不轮转")
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
            writeUnauthorized(response);
            return;
        }

        Optional<OAuthTokenPayload> payloadOpt = oAuthTokenService.verifyAccessToken(token);
        if (payloadOpt.isEmpty()) {
            writeUnauthorized(response);
            return;
        }

        OAuthTokenPayload payload = payloadOpt.get();
        Long userId = payload.userId();
        UserEntity user = userMapper.selectById(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        if (user != null) {
            try {
                Long appRefId = appProtocolGuard.resolveAppRefId(payload.clientId());
                body.putAll(ssoUserinfoAttributesResolver.resolve(appRefId, user));
            } catch (SsoProtocolException e) {
                // 令牌签发后应用被删除等边缘场景：不影响固定的 sub 字段正常返回，映射字段跳过。
            }
        }
        body.put("sub", String.valueOf(userId));
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
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_request"));
            return;
        }

        AppConfigEntity appConfig;
        try {
            appConfig = appProtocolGuard.resolveOAuthClientConfig(tokenRequest.getClientId());
        } catch (SsoProtocolException e) {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_UNAUTHORIZED, errorBody("invalid_client"));
            return;
        }

        String plainSecretKey = Sm4JdkUtils.decrypt(appConfig.getSecretKey(), appSecretProperties.getSm4Key());
        boolean secretMatches = MessageDigest.isEqual(tokenRequest.getClientSecret().getBytes(StandardCharsets.UTF_8),
                plainSecretKey.getBytes(StandardCharsets.UTF_8));
        if (!secretMatches) {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_UNAUTHORIZED, errorBody("invalid_client"));
            return;
        }

        Optional<OAuthCodePayload> payloadOpt = oAuthTokenService.consumeCode(tokenRequest.getCode());
        if (payloadOpt.isEmpty() || !Objects.equals(payloadOpt.get().clientId(), tokenRequest.getClientId())
                || !Objects.equals(payloadOpt.get().redirectUri(), tokenRequest.getRedirectUri())) {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_grant"));
            return;
        }

        OAuthCodePayload payload = payloadOpt.get();
        IssuedToken issuedToken = oAuthTokenService.issueAccessTokenWithRefresh(tokenRequest.getClientId(),
                payload.userId(), payload.scope());
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
     * （design.md Decision 6/Risks 已说明这是按用户明确给出的参数列表实现的结果）。
     *
     * @param tokenRequest 令牌请求参数
     * @param response     当前响应
     * @throws IOException 写响应失败
     */
    private void handleRefreshTokenGrant(OAuthTokenRequest tokenRequest, HttpServletResponse response)
            throws IOException {
        if (!StringUtils.hasText(tokenRequest.getRefreshToken())) {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_request"));
            return;
        }

        Optional<OAuthRefreshPayload> payloadOpt = oAuthTokenService.verifyRefreshToken(tokenRequest.getRefreshToken());
        if (payloadOpt.isEmpty()) {
            ProtocolResponseWriter.json(response, HttpServletResponse.SC_BAD_REQUEST, errorBody("invalid_grant"));
            return;
        }

        OAuthRefreshPayload payload = payloadOpt.get();
        String accessToken = oAuthTokenService.issueAccessTokenOnly(payload.clientId(), payload.userId(), payload.scope());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", accessToken);
        body.put("token_type", OAuthTokenService.TOKEN_TYPE_BEARER);
        body.put("expires_in", ssoProperties.getOauthTokenExpireSeconds());
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
