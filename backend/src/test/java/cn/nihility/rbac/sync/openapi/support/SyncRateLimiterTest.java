package cn.nihility.rbac.sync.openapi.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.config.SyncRateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link SyncRateLimiter} 的配额隔离和请求规模校验测试。 */
class SyncRateLimiterTest {

    @AfterEach
    void tearDown() {
        OpenApiCallerContext.clear();
    }

    /** 同一应用的不同接口使用独立令牌桶。 */
    @Test
    void validateAndAcquire_shouldIsolateEndpoints() {
        SyncRateLimiter limiter = limiter();
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());

        limiter.validateAndAcquire("pull", null, 0);
        assertThatThrownBy(() -> limiter.validateAndAcquire("pull", null, 0))
                .isInstanceOf(SyncRateLimiter.RateLimitedException.class);
        limiter.validateAndAcquire("changes", null, 0);
        limiter.validateAndAcquire("digest", null, 0);
    }

    /** 不同应用的同一接口使用独立令牌桶。 */
    @Test
    void validateAndAcquire_shouldIsolateApplications() {
        SyncRateLimiter limiter = limiter();
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());
        limiter.validateAndAcquire("pull", null, 0);
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(2L).build());
        limiter.validateAndAcquire("pull", null, 0);
    }

    /** 请求规模超限应在消费令牌前直接拒绝。 */
    @Test
    void validateAndAcquire_shouldRejectAmplifiedRequest() {
        SyncRateLimiter limiter = limiter();
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());

        assertThatThrownBy(() -> limiter.validateAndAcquire("pull", 501, 0))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pageSize");
        assertThatThrownBy(() -> limiter.validateAndAcquire("pull", 20, 201))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ids");
        // 两次非法请求均不得消费唯一的突发令牌。
        limiter.validateAndAcquire("pull", 20, 1);
        assertThatThrownBy(() -> limiter.validateAndAcquire("pull", 20, 1))
                .isInstanceOf(SyncRateLimiter.RateLimitedException.class);
    }

    /** 未知接口标识应被拒绝，不能静默复用 pull 配额。 */
    @Test
    void validateAndAcquire_shouldRejectUnknownEndpoint() {
        SyncRateLimiter limiter = limiter();
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());

        assertThatThrownBy(() -> limiter.validateAndAcquire("unknown", null, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的同步接口限流标识");
        limiter.validateAndAcquire("pull", null, 0);
    }

    private SyncRateLimiter limiter() {
        SyncRateLimitProperties properties = new SyncRateLimitProperties();
        properties.setPullTokensPerSecond(0.01D);
        properties.setPullBurstCapacity(1);
        properties.setDigestTokensPerSecond(0.01D);
        properties.setDigestBurstCapacity(1);
        return new SyncRateLimiter(properties);
    }
}
