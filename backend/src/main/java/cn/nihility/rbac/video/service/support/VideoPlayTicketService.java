package cn.nihility.rbac.video.service.support;

import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.video.config.VideoProperties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 视频播放凭证的 Redis 读写组件（add-video-management change design.md Decision 3）：
 * 凭证绑定单个视频 id，按 {@code rbac.video.play-ticket-ttl-seconds} 过期；校验通过时原子地
 * 刷新过期时间（滑动过期），保证持续播放/拖动过程中的后续 Range 请求不因凭证过期失败。
 * 凭证不做一次性消费——一次播放会产生多个 Range 请求。
 */
@Component
@RequiredArgsConstructor
public class VideoPlayTicketService {

    /** 播放凭证 Redis key 前缀，完整 key 为前缀 + 凭证，value 为绑定的视频 id。 */
    private static final String TICKET_KEY_PREFIX = "video:play-ticket:";

    /** 视频模块配置，提供凭证有效期。 */
    private final VideoProperties videoProperties;

    /**
     * 为指定视频生成播放凭证并写入 Redis。
     *
     * @param videoId 视频记录 id
     * @return 32 位随机十六进制凭证
     */
    public String issue(Long videoId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        RedisUtils.set(TICKET_KEY_PREFIX + ticket, String.valueOf(videoId), ttlSeconds(), TimeUnit.SECONDS);
        return ticket;
    }

    /**
     * 校验凭证是否存在且绑定的视频 id 与请求的视频 id 一致，一致时原子地刷新凭证过期时间。
     *
     * @param ticket  播放凭证
     * @param videoId 请求的视频记录 id
     * @return 凭证有效且与视频匹配时返回 {@code true}
     */
    public boolean verifyAndRenew(String ticket, Long videoId) {
        if (!StringUtils.hasText(ticket) || videoId == null) {
            return false;
        }
        return RedisUtils.compareAndExpire(TICKET_KEY_PREFIX + ticket, String.valueOf(videoId), ttlSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * 凭证有效期（秒）。
     *
     * @return 凭证有效期（秒）
     */
    public long ttlSeconds() {
        return videoProperties.getPlayTicketTtlSeconds();
    }
}
