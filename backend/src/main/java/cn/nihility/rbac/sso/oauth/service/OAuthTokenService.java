package cn.nihility.rbac.sso.oauth.service;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.oauth.dto.IssuedToken;
import cn.nihility.rbac.sso.oauth.dto.OAuthCodePayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthRefreshPayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthTokenPayload;
import cn.nihility.rbac.sso.session.SsoSessionService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OAuth2 授权码/access token/refresh token 业务逻辑（app-sso-protocol-runtime change
 * design.md Decision 6）：均以 32 位随机十六进制作为凭证取值，落库为 Redis JSON 载荷。
 * 授权码一次性消费；{@code grant_type=refresh_token} 刷新时 refresh token 轮转——旧值立即
 * 一次性消费失效，签发新的 access token 与新的 refresh token（add-sso-single-logout change
 * design.md Decision 6，{@link #rotateAccessAndRefreshToken}）。
 */
@Service
@RequiredArgsConstructor
public class OAuthTokenService {

    /** 授权码 Redis key 前缀。 */
    private static final String CODE_KEY_PREFIX = "oauth:code:";

    /** access token Redis key 前缀。 */
    private static final String TOKEN_KEY_PREFIX = "oauth:token:";

    /** refresh token Redis key 前缀。 */
    private static final String REFRESH_KEY_PREFIX = "oauth:refresh:";

    /** 令牌类型：固定为 {@code Bearer}。 */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /** SSO 相关配置：各类凭证有效期。 */
    private final RbacSsoProperties ssoProperties;

    /** SSO 浏览器会话业务逻辑接口，登记本次会话-应用凭证映射。 */
    private final SsoSessionService ssoSessionService;

    /**
     * 签发一枚授权码。
     *
     * @param clientId        OAuth2 client_id
     * @param redirectUri     校验通过的 redirect_uri
     * @param userId          绑定的用户 id
     * @param scope           授权范围
     * @param ssoSessionToken 签发时当前浏览器持有的 SSO 会话令牌，随授权码一并落库
     *                        （add-sso-single-logout change design.md Decision 1）
     * @return 授权码（32 位随机十六进制）
     */
    public String issueCode(String clientId, String redirectUri, Long userId, String scope, String ssoSessionToken) {
        String code = newHex();
        OAuthCodePayload payload = new OAuthCodePayload(clientId, redirectUri, userId, scope, ssoSessionToken);
        RedisUtils.setObject(CODE_KEY_PREFIX + code, payload, ssoProperties.getOauthCodeExpireSeconds(),
                TimeUnit.SECONDS);
        return code;
    }

    /**
     * 消费一枚授权码：读取成功与否均立即删除（一次性消费）。
     *
     * @param code 授权码
     * @return 授权码存在（未过期、未被消费过）时返回其签发载荷，否则返回空
     */
    public Optional<OAuthCodePayload> consumeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String redisKey = CODE_KEY_PREFIX + code;
        Optional<OAuthCodePayload> payload = RedisUtils.getObject(redisKey, OAuthCodePayload.class);
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        RedisUtils.delete(redisKey);
        return payload;
    }

    /**
     * {@code grant_type=authorization_code} 场景：同时签发 access token 与 refresh token。
     * {@code ssoSessionToken} 非空时（授权码本身关联了 SSO 会话，理论上必然非空，见
     * {@link OAuthCodePayload} 的既有注释），同步登记本次会话在该应用签发的 access token
     * （{@code sso:session:<token>:apps} Hash，add-sso-single-logout change design.md
     * Decision 1）；为空时跳过登记，不影响令牌签发主流程。
     *
     * @param clientId        OAuth2 client_id
     * @param userId          绑定的用户 id
     * @param scope           授权范围
     * @param ssoSessionToken 签发该授权码时关联的 SSO 会话令牌，可能为空
     * @return 签发的 access token / refresh token / access token 有效期
     */
    public IssuedToken issueAccessTokenWithRefresh(String clientId, Long userId, String scope, String ssoSessionToken) {
        String accessToken = writeAccessToken(clientId, userId, scope);
        if (StringUtils.hasText(ssoSessionToken)) {
            ssoSessionService.recordAppCredential(ssoSessionToken, clientId, AuthProtocol.OAUTH2, accessToken);
        }
        String refreshToken = newHex();
        OAuthRefreshPayload refreshPayload = new OAuthRefreshPayload(clientId, userId, scope);
        RedisUtils.setObject(REFRESH_KEY_PREFIX + refreshToken, refreshPayload,
                ssoProperties.getOauthRefreshTokenExpireSeconds(), TimeUnit.SECONDS);
        return new IssuedToken(accessToken, refreshToken, ssoProperties.getOauthTokenExpireSeconds());
    }

    /**
     * {@code grant_type=refresh_token} 场景：轮转签发——旧 {@code refresh_token} 立即删除
     * （一次性消费，不可再被用于任何后续刷新请求），签发一个新的 access token 与一个新的
     * {@code refresh_token}（拥有完整的配置有效期），供调用方替换本地保存的旧值
     * （add-sso-single-logout change design.md Decision 6）。
     *
     * @param oldRefreshToken 本次刷新使用的旧 refresh token（已校验存在且未过期）
     * @param clientId        OAuth2 client_id
     * @param userId          绑定的用户 id
     * @param scope           授权范围
     * @return 新签发的 access token / refresh token / access token 有效期
     */
    public IssuedToken rotateAccessAndRefreshToken(String oldRefreshToken, String clientId, Long userId, String scope) {
        RedisUtils.delete(REFRESH_KEY_PREFIX + oldRefreshToken);
        String accessToken = writeAccessToken(clientId, userId, scope);
        String newRefreshToken = newHex();
        OAuthRefreshPayload refreshPayload = new OAuthRefreshPayload(clientId, userId, scope);
        RedisUtils.setObject(REFRESH_KEY_PREFIX + newRefreshToken, refreshPayload,
                ssoProperties.getOauthRefreshTokenExpireSeconds(), TimeUnit.SECONDS);
        return new IssuedToken(accessToken, newRefreshToken, ssoProperties.getOauthTokenExpireSeconds());
    }

    /**
     * 校验一个 access token 是否有效。
     *
     * @param token access token
     * @return 有效时返回其签发载荷，否则返回空
     */
    public Optional<OAuthTokenPayload> verifyAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return RedisUtils.getObject(TOKEN_KEY_PREFIX + token, OAuthTokenPayload.class);
    }

    /**
     * 校验一个 refresh token 是否有效，本方法只读不删（一次性消费的删除动作在
     * {@link #rotateAccessAndRefreshToken} 内完成，校验通过后才会执行）。
     *
     * @param refreshToken refresh token
     * @return 有效时返回其签发载荷，否则返回空
     */
    public Optional<OAuthRefreshPayload> verifyRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }
        return RedisUtils.getObject(REFRESH_KEY_PREFIX + refreshToken, OAuthRefreshPayload.class);
    }

    /**
     * 签发一枚 access token 并写入 Redis，供 {@link #issueAccessTokenWithRefresh}/
     * {@link #rotateAccessAndRefreshToken} 共用。
     *
     * @param clientId OAuth2 client_id
     * @param userId   绑定的用户 id
     * @param scope    授权范围
     * @return 新签发的 access token
     */
    private String writeAccessToken(String clientId, Long userId, String scope) {
        String accessToken = newHex();
        OAuthTokenPayload payload = new OAuthTokenPayload(clientId, userId, scope);
        RedisUtils.setObject(TOKEN_KEY_PREFIX + accessToken, payload, ssoProperties.getOauthTokenExpireSeconds(),
                TimeUnit.SECONDS);
        return accessToken;
    }

    /**
     * 生成一个不含横线的 UUID 字符串（32 位十六进制）。
     *
     * @return 32 位十六进制字符串
     */
    private String newHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
