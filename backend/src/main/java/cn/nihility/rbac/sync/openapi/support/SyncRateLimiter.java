package cn.nihility.rbac.sync.openapi.support;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.config.SyncRateLimitProperties;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 按应用和接口隔离的进程内令牌桶限流器。 */
@Component
public class SyncRateLimiter {

    public static final int RATE_LIMITED_CODE = 42901;
    private static final String ENDPOINT_PULL = "pull";
    private static final String ENDPOINT_CHANGES = "changes";
    private static final String ENDPOINT_DIGEST = "digest";
    private final SyncRateLimitProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public SyncRateLimiter(SyncRateLimitProperties properties) {
        this.properties = properties;
    }

    /** 先校验请求规模，再消费接口独立令牌；返回建议重试秒数。 */
    public long validateAndAcquire(String endpoint, Integer pageSize, int idsCount) {
        if (pageSize != null && pageSize > properties.getMaxPageSize()) {
            throw new BusinessException("pageSize 不能超过 " + properties.getMaxPageSize());
        }
        if (idsCount > properties.getMaxIds()) {
            throw new BusinessException("ids 数量不能超过 " + properties.getMaxIds());
        }
        boolean digest;
        if (ENDPOINT_DIGEST.equals(endpoint)) {
            digest = true;
        } else if (ENDPOINT_PULL.equals(endpoint) || ENDPOINT_CHANGES.equals(endpoint)) {
            digest = false;
        } else {
            throw new BusinessException("非法的同步接口限流标识：" + endpoint);
        }
        Long appRefId = OpenApiCallerContext.getAppRefId();
        double rate = digest ? properties.getDigestTokensPerSecond() : properties.getPullTokensPerSecond();
        int capacity = digest ? properties.getDigestBurstCapacity() : properties.getPullBurstCapacity();
        TokenBucket bucket = buckets.computeIfAbsent(appRefId + ":" + endpoint,
                key -> new TokenBucket(rate, capacity));
        long retryAfter = bucket.tryAcquire();
        if (retryAfter > 0) {
            throw new RateLimitedException(retryAfter);
        }
        return retryAfter;
    }

    /** 携带 Retry-After 秒数的限流业务异常。 */
    public static class RateLimitedException extends BusinessException {
        private final long retryAfterSeconds;

        public RateLimitedException(long retryAfterSeconds) {
            super(RATE_LIMITED_CODE, "请求过于频繁，请稍后重试");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private static final class TokenBucket {
        private final double rate;
        private final int capacity;
        private double tokens;
        private long lastNanos;

        private TokenBucket(double rate, int capacity) {
            this.rate = rate;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastNanos = System.nanoTime();
        }

        private synchronized long tryAcquire() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastNanos) / 1_000_000_000D * rate);
            lastNanos = now;
            if (tokens >= 1D) {
                tokens -= 1D;
                return 0L;
            }
            return Math.max(1L, (long) Math.ceil((1D - tokens) / rate));
        }
    }
}
