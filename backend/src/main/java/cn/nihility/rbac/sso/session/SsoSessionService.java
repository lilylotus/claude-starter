package cn.nihility.rbac.sso.session;

import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * SSO 浏览器会话业务逻辑（app-sso-protocol-runtime change design.md Decision 2）：以
 * {@code sso:session:<token>} 为 Redis key 存储 userId，固定过期、不做滑动续期，延续
 * {@code TokenServiceImpl} "UUID 去横线做 opaque token" 的既有模式。
 */
@Service
@RequiredArgsConstructor
public class SsoSessionService {

    /** Redis key 前缀，完整 key 为该前缀 + 会话令牌。 */
    private static final String SESSION_KEY_PREFIX = "sso:session:";

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
     * 生成一个不含横线的 UUID 字符串，作为会话令牌的取值。
     *
     * @return 令牌字符串
     */
    private String newTokenValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
