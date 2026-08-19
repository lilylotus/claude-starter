package cn.nihility.rbac.sso.session;

import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * SSO 浏览器会话业务逻辑（app-sso-protocol-runtime change design.md Decision 2）：以
 * {@code sso:session:<token>} 为 Redis key 存储 userId，固定过期、不做滑动续期，延续
 * {@code TokenServiceImpl} "UUID 去横线做 opaque token" 的既有模式。同时维护
 * {@code sso:session:<token>:apps} Hash，记录本次会话在每个应用最后一次签发的协议凭证
 * （add-sso-single-logout change design.md Decision 1），供登出时构造回调通知使用。
 */
@Service
@RequiredArgsConstructor
public class SsoSessionService {

    /** Redis key 前缀，完整 key 为该前缀 + 会话令牌。 */
    private static final String SESSION_KEY_PREFIX = "sso:session:";

    /** 会话-应用凭证映射 Hash key 后缀，完整 key 为 {@link #SESSION_KEY_PREFIX} + 会话令牌 + 该后缀。 */
    private static final String SESSION_APPS_KEY_SUFFIX = ":apps";

    /** SSO 相关配置：会话有效期。 */
    private final RbacSsoProperties ssoProperties;

    /**
     * 签发一个新的 SSO 会话。
     *
     * @param userId 用户 id
     * @return 会话令牌
     */
    public String issue(Long userId) {
        String token = newTokenValue();
        RedisUtils.set(SESSION_KEY_PREFIX + token, String.valueOf(userId), ssoProperties.getSessionExpireSeconds(),
                TimeUnit.SECONDS);
        return token;
    }

    /**
     * 校验一个 SSO 会话令牌是否有效。
     *
     * @param token 会话令牌，可能为空
     * @return 有效时返回对应的用户 id，否则返回空
     */
    public Optional<Long> verify(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return RedisUtils.get(SESSION_KEY_PREFIX + token).map(Long::valueOf);
    }

    /**
     * 清除一个 SSO 会话（登出），幂等——令牌不存在时也视为成功。
     *
     * @param token 会话令牌，可能为空
     */
    public void revoke(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        RedisUtils.delete(SESSION_KEY_PREFIX + token);
    }

    /**
     * 记录本次会话在某个应用最后一次签发的协议凭证（CAS 服务票据或 OAuth2 access token），
     * 写入 {@code sso:session:<token>:apps} Hash 的 {@code appId} 字段，同一 {@code appId}
     * 重复写入时后写覆盖前写；每次写入都刷新该 Hash 的过期时间，使其与 {@code sso_session}
     * 主 key 的有效期保持对齐（add-sso-single-logout change design.md Decision 1）。
     * {@code token}/{@code appId} 为空时直接跳过，不影响调用方主流程。
     *
     * @param token      会话令牌
     * @param appId      应用对外标识
     * @param protocol   签发该凭证使用的单点登录协议：{@code CAS} 或 {@code OAUTH2}
     * @param credential 凭证取值：CAS 场景为服务票据，OAuth2 场景为 access token
     */
    public void recordAppCredential(String token, String appId, String protocol, String credential) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(appId)) {
            return;
        }
        String key = appsKey(token);
        RedisUtils.putHashObject(key, appId, new SsoSessionAppCredential(protocol, credential));
        RedisUtils.expire(key, ssoProperties.getSessionExpireSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 读取本次会话已记录的"应用 -&gt; 最后一次签发凭证"映射。
     *
     * @param token 会话令牌，可能为空
     * @return 应用对外标识到其最后一次签发凭证的映射，未记录过任何应用时返回空 Map
     */
    public Map<String, SsoSessionAppCredential> listAppCredentials(String token) {
        if (!StringUtils.hasText(token)) {
            return Map.of();
        }
        Map<Object, Object> raw = RedisUtils.hashEntries(appsKey(token));
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, SsoSessionAppCredential> result = new LinkedHashMap<>();
        raw.forEach((field, value) -> result.put(String.valueOf(field), toCredential(value)));
        return result;
    }

    /**
     * 清除本次会话的"应用 -&gt; 最后一次签发凭证"映射，供 {@code SsoLogoutNotifyService}
     * 在读取完映射、提交完通知任务后清理，避免残留（add-sso-single-logout change design.md
     * Decision 1"登出时一并清理"）。幂等——Hash 不存在时也视为成功。
     *
     * @param token 会话令牌，可能为空
     */
    public void clearAppCredentials(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        RedisUtils.delete(appsKey(token));
    }

    /**
     * 把 Redis Hash 字段的原始值反序列化为 {@link SsoSessionAppCredential}。
     *
     * @param rawValue Hash 字段原始值（JSON 字符串）
     * @return 反序列化后的凭证
     */
    private SsoSessionAppCredential toCredential(Object rawValue) {
        return JacksonUtils.toObj(String.valueOf(rawValue), SsoSessionAppCredential.class);
    }

    /**
     * 拼接会话-应用凭证映射 Hash 的完整 Redis key。
     *
     * @param token 会话令牌
     * @return 完整 Redis key
     */
    private String appsKey(String token) {
        return SESSION_KEY_PREFIX + token + SESSION_APPS_KEY_SUFFIX;
    }

    /**
     * 生成一个不含横线的 UUID 字符串，作为会话令牌的取值。
     *
     * @return 令牌字符串
     */
    private String newTokenValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
