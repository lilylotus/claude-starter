package cn.nihility.rbac.auth.service.impl;

import cn.nihility.rbac.auth.config.RbacLoginProperties;
import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.dto.TokenPair;
import cn.nihility.rbac.auth.service.TokenService;
import cn.nihility.rbac.common.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Redis 会话令牌业务逻辑实现（password-login-auth change design.md Decision 3）：以
 * {@code user:} 为前缀维护三类记录——{@code user:identity-token:<userId>} 会话 Hash、
 * {@code user:access-token:<accessKey>}/{@code user:refresh-token:<refreshKey>} 两个
 * 反查记录，均使用 {@link StringRedisTemplate}（值均为字符串，无需自定义序列化器）。
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    /** 会话 Hash key 前缀，完整 key 为该前缀 + userId。 */
    private static final String IDENTITY_TOKEN_PREFIX = "user:identity-token:";

    /** access-key 反查记录 key 前缀，完整 key 为该前缀 + accessKey，值为 userId。 */
    private static final String ACCESS_TOKEN_PREFIX = "user:access-token:";

    /** refresh-key 反查记录 key 前缀，完整 key 为该前缀 + refreshKey，值为 userId。 */
    private static final String REFRESH_TOKEN_PREFIX = "user:refresh-token:";

    /** 会话 Hash 中 access-key 字段名。 */
    private static final String FIELD_ACCESS_KEY = "accessKey";

    /** 会话 Hash 中 access-key 过期时间（epoch millis）字段名。 */
    private static final String FIELD_ACCESS_EXPIRE_AT = "accessExpireAt";

    /** 会话 Hash 中 refresh-key 字段名。 */
    private static final String FIELD_REFRESH_KEY = "refreshKey";

    /** 会话 Hash 中 refresh-key 过期时间（epoch millis）字段名。 */
    private static final String FIELD_REFRESH_EXPIRE_AT = "refreshExpireAt";

    /** 字符串 Redis 模板，Spring Boot 已自动装配，无需自定义序列化器。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 登录相关配置：access/refresh 令牌有效期。 */
    private final RbacLoginProperties loginProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public TokenPair issue(Long userId) {
        String accessKey = newTokenValue();
        String refreshKey = newTokenValue();
        long now = System.currentTimeMillis();
        long accessExpireAtMillis = now + loginProperties.getAccessTokenExpireSeconds() * 1000;
        long refreshExpireAtMillis = now + loginProperties.getRefreshTokenExpireSeconds() * 1000;

        Map<String, String> hash = new HashMap<>();
        hash.put(FIELD_ACCESS_KEY, accessKey);
        hash.put(FIELD_ACCESS_EXPIRE_AT, String.valueOf(accessExpireAtMillis));
        hash.put(FIELD_REFRESH_KEY, refreshKey);
        hash.put(FIELD_REFRESH_EXPIRE_AT, String.valueOf(refreshExpireAtMillis));

        String identityKey = IDENTITY_TOKEN_PREFIX + userId;
        stringRedisTemplate.opsForHash().putAll(identityKey, hash);
        stringRedisTemplate.expire(identityKey, loginProperties.getRefreshTokenExpireSeconds(), TimeUnit.SECONDS);

        stringRedisTemplate.opsForValue().set(ACCESS_TOKEN_PREFIX + accessKey, String.valueOf(userId),
                loginProperties.getAccessTokenExpireSeconds(), TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshKey, String.valueOf(userId),
                loginProperties.getRefreshTokenExpireSeconds(), TimeUnit.SECONDS);

        return TokenPair.builder()
                .accessKey(accessKey)
                .accessExpireAt(toLocalDateTime(accessExpireAtMillis))
                .refreshKey(refreshKey)
                .refreshExpireAt(toLocalDateTime(refreshExpireAtMillis))
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Long> verifyAccessKey(String accessKey) {
        String userIdStr = stringRedisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + accessKey);
        if (!StringUtils.hasText(userIdStr)) {
            return Optional.empty();
        }
        Long userId = Long.valueOf(userIdStr);

        Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(IDENTITY_TOKEN_PREFIX + userId);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        Object storedAccessKey = hash.get(FIELD_ACCESS_KEY);
        if (storedAccessKey == null || !accessKey.equals(storedAccessKey.toString())) {
            // 刷新接口签发新 access-key 后只覆盖 Hash 里的 accessKey，不会主动删除旧的
            // user:access-token:<oldKey> 反查记录，这里回读 Hash 校验一致性，
            // 保证刷新后旧 access-key 立即失效（design.md Decision 3）。
            return Optional.empty();
        }
        Object accessExpireAtValue = hash.get(FIELD_ACCESS_EXPIRE_AT);
        long accessExpireAtMillis = accessExpireAtValue != null ? Long.parseLong(accessExpireAtValue.toString()) : 0L;
        if (System.currentTimeMillis() > accessExpireAtMillis) {
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TokenPair refresh(String refreshKey) {
        String userIdStr = stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshKey);
        if (!StringUtils.hasText(userIdStr)) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期，请重新登录");
        }
        Long userId = Long.valueOf(userIdStr);

        String identityKey = IDENTITY_TOKEN_PREFIX + userId;
        Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(identityKey);
        Object storedRefreshKey = hash.get(FIELD_REFRESH_KEY);
        if (storedRefreshKey == null || !refreshKey.equals(storedRefreshKey.toString())) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期，请重新登录");
        }
        Object refreshExpireAtValue = hash.get(FIELD_REFRESH_EXPIRE_AT);
        long refreshExpireAtMillis = refreshExpireAtValue != null ? Long.parseLong(refreshExpireAtValue.toString()) : 0L;
        if (System.currentTimeMillis() > refreshExpireAtMillis) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期，请重新登录");
        }

        String newAccessKey = newTokenValue();
        long now = System.currentTimeMillis();
        long newAccessExpireAtMillis = now + loginProperties.getAccessTokenExpireSeconds() * 1000;

        stringRedisTemplate.opsForHash().put(identityKey, FIELD_ACCESS_KEY, newAccessKey);
        stringRedisTemplate.opsForHash().put(identityKey, FIELD_ACCESS_EXPIRE_AT, String.valueOf(newAccessExpireAtMillis));
        stringRedisTemplate.opsForValue().set(ACCESS_TOKEN_PREFIX + newAccessKey, String.valueOf(userId),
                loginProperties.getAccessTokenExpireSeconds(), TimeUnit.SECONDS);

        return TokenPair.builder()
                .accessKey(newAccessKey)
                .accessExpireAt(toLocalDateTime(newAccessExpireAtMillis))
                .refreshKey(refreshKey)
                .refreshExpireAt(toLocalDateTime(refreshExpireAtMillis))
                .build();
    }

    /**
     * 生成一个不含横线的 UUID 字符串，作为 access-key/refresh-key 的取值。
     *
     * @return 令牌字符串
     */
    private String newTokenValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 把 epoch 毫秒时间戳转换为系统默认时区下的 {@link LocalDateTime}。
     *
     * @param epochMillis epoch 毫秒时间戳
     * @return 对应的 {@link LocalDateTime}
     */
    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
