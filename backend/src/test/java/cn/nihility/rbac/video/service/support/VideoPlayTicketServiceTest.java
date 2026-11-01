package cn.nihility.rbac.video.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.video.config.VideoProperties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link VideoPlayTicketService} 的单元测试：凭证写入 Redis 时绑定视频 id 并设置有效期；
 * 校验时以"值等于视频 id 才续期"的原子操作实现滑动过期，空凭证直接拒绝。
 */
class VideoPlayTicketServiceTest {

    /**
     * 签发凭证：32 位十六进制，key 带前缀，value 为视频 id，有效期取配置。
     */
    @Test
    void issueStoresTicketBoundToVideo() {
        VideoProperties properties = new VideoProperties();
        properties.setPlayTicketTtlSeconds(300);
        VideoPlayTicketService service = new VideoPlayTicketService(properties);

        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            String ticket = service.issue(42L);

            assertThat(ticket).matches("[0-9a-f]{32}");
            redis.verify(() -> RedisUtils.set("video:play-ticket:" + ticket, "42", 300L, TimeUnit.SECONDS));
        }
    }

    /**
     * 校验凭证：委托 compareAndExpire 原子比较视频 id 并续期，结果原样返回。
     */
    @Test
    void verifyAndRenewComparesVideoIdAndSlidesTtl() {
        VideoPlayTicketService service = new VideoPlayTicketService(new VideoProperties());

        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.compareAndExpire("video:play-ticket:abc", "7", 600L, TimeUnit.SECONDS))
                    .thenReturn(true);
            redis.when(() -> RedisUtils.compareAndExpire(eq("video:play-ticket:abc"), eq("8"), anyLong(),
                    eq(TimeUnit.SECONDS))).thenReturn(false);

            assertThat(service.verifyAndRenew("abc", 7L)).isTrue();
            assertThat(service.verifyAndRenew("abc", 8L)).isFalse();
        }
    }

    /**
     * 空凭证不访问 Redis，直接拒绝。
     */
    @Test
    void verifyAndRenewRejectsBlankTicket() {
        VideoPlayTicketService service = new VideoPlayTicketService(new VideoProperties());

        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            assertThat(service.verifyAndRenew(" ", 7L)).isFalse();
            assertThat(service.verifyAndRenew(null, 7L)).isFalse();
            redis.verify(() -> RedisUtils.compareAndExpire(anyString(), anyString(), anyLong(),
                    eq(TimeUnit.SECONDS)), org.mockito.Mockito.never());
        }
    }
}
