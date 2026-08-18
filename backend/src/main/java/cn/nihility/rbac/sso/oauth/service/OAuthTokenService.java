package cn.nihility.rbac.sso.oauth.service;

import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import cn.nihility.rbac.sso.oauth.dto.IssuedToken;
import cn.nihility.rbac.sso.oauth.dto.OAuthCodePayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthRefreshPayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthTokenPayload;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OAuth2 授权码/access token/refresh token 业务逻辑（app-sso-protocol-runtime change
 * design.md Decision 6）：均以 32 位随机十六进制作为凭证取值，落库为 Redis JSON 载荷。
 * 授权码一次性消费；refresh token 不轮转——{@link #verifyRefreshToken(String)} 只读不删。
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

    /** 字符串 Redis 模板。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** SSO 相关配置：各类凭证有效期。 */
    private final RbacSsoProperties ssoProperties;

    /**
     * 签发一枚授权码。
     *
     * @param clientId    OAuth2 client_id
     * @param redirectUri 校验通过的 redirect_uri
     * @param userId      绑定的用户 id
     * @param scope       授权范围
     * @return 授权码（32 位随机十六进制）
     */
    public String issueCode(String clientId, String redirectUri, Long userId, String scope) {
        String code = newHex();
        OAuthCodePayload payload = new OAuthCodePayload(clientId, redirectUri, userId, scope);
        stringRedisTemplate.opsForValue().set(CODE_KEY_PREFIX + code, JacksonUtils.toJson(payload),
                ssoProperties.getOauthCodeExpireSeconds(), TimeUnit.SECONDS);
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
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        stringRedisTemplate.delete(redisKey);
        return Optional.of(JacksonUtils.toObj(json, OAuthCodePayload.class));
    }

    /**
     * {@code grant_type=authorization_code} 场景：同时签发 access token 与 refresh token。
     *
     * @param clientId OAuth2 client_id
     * @param userId   绑定的用户 id
     * @param scope    授权范围
     * @return 签发的 access token / refresh token / access token 有效期
     */
    public IssuedToken issueAccessTokenWithRefresh(String clientId, Long userId, String scope) {
        String accessToken = writeAccessToken(clientId, userId, scope);
        String refreshToken = newHex();
        OAuthRefreshPayload refreshPayload = new OAuthRefreshPayload(clientId, userId, scope);
        stringRedisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + refreshToken, JacksonUtils.toJson(refreshPayload),
                ssoProperties.getOauthRefreshTokenExpireSeconds(), TimeUnit.SECONDS);
        return new IssuedToken(accessToken, refreshToken, ssoProperties.getOauthTokenExpireSeconds());
    }

    /**
     * {@code grant_type=refresh_token} 场景：只签发新的 access token，不动 refresh token 记录。
     *
     * @param clientId OAuth2 client_id
     * @param userId   绑定的用户 id
     * @param scope    授权范围
     * @return 新签发的 access token
     */
    public String issueAccessTokenOnly(String clientId, Long userId, String scope) {
        return writeAccessToken(clientId, userId, scope);
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
        String json = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        return Optional.of(JacksonUtils.toObj(json, OAuthTokenPayload.class));
    }

    /**
     * 校验一个 refresh token 是否有效。refresh token 不轮转，本方法只读不删。
     *
     * @param refreshToken refresh token
     * @return 有效时返回其签发载荷，否则返回空
     */
    public Optional<OAuthRefreshPayload> verifyRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }
        String json = stringRedisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + refreshToken);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        return Optional.of(JacksonUtils.toObj(json, OAuthRefreshPayload.class));
    }

    /**
     * 签发一枚 access token 并写入 Redis，供 {@link #issueAccessTokenWithRefresh}/
     * {@link #issueAccessTokenOnly} 共用。
     *
     * @param clientId OAuth2 client_id
     * @param userId   绑定的用户 id
     * @param scope    授权范围
     * @return 新签发的 access token
     */
    private String writeAccessToken(String clientId, Long userId, String scope) {
        String accessToken = newHex();
        OAuthTokenPayload payload = new OAuthTokenPayload(clientId, userId, scope);
        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + accessToken, JacksonUtils.toJson(payload),
                ssoProperties.getOauthTokenExpireSeconds(), TimeUnit.SECONDS);
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
