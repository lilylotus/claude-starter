package cn.nihility.rbac.chat.gateway.support;

import cn.nihility.rbac.chat.gateway.config.ChatGatewayProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 按用户/按来源 IP 的进程内令牌桶限流管理（chat-security spec"按用户与按连接来源限流"
 * 需求，design.md Decision 6）。令牌桶按需懒创建，用 Caffeine 缓存并设置访问过期时间，
 * 避免长期运行下 {@code Map} 无界增长占用内存（不活跃的 userId/IP 会被自动淘汰，
 * 下次访问时以初始满桶状态重新创建，符合限流语义、不影响正确性）。
 */
@Component
public class ChatRateLimiterManager {

    /** 聊天网关配置。 */
    private final ChatGatewayProperties properties;

    /** 按来源 IP 的建连令牌桶缓存。 */
    private final Cache<String, ChatTokenBucket> ipBuckets;

    /** 按用户 id 的消息发送令牌桶缓存。 */
    private final Cache<Long, ChatTokenBucket> userBuckets;

    public ChatRateLimiterManager(ChatGatewayProperties properties) {
        this.properties = properties;
        this.ipBuckets = Caffeine.newBuilder().maximumSize(50_000).expireAfterAccess(Duration.ofMinutes(10)).build();
        this.userBuckets = Caffeine.newBuilder().maximumSize(50_000).expireAfterAccess(Duration.ofMinutes(10)).build();
    }

    /**
     * 按来源 IP 尝试获取一次建连令牌。
     *
     * @param ip 来源 IP
     * @return 是否允许本次建连
     */
    public boolean tryAcquireConnection(String ip) {
        ChatTokenBucket bucket = ipBuckets.get(ip, key -> new ChatTokenBucket(
                properties.getRateLimit().getIpConnectBurstCapacity(),
                properties.getRateLimit().getIpConnectTokensPerSecond()));
        return bucket.tryAcquire();
    }

    /**
     * 按用户 id 尝试获取一次消息发送令牌。
     *
     * @param userId 用户 id
     * @return 是否允许本次发送
     */
    public boolean tryAcquireMessage(Long userId) {
        ChatTokenBucket bucket = userBuckets.get(userId, key -> new ChatTokenBucket(
                properties.getRateLimit().getUserMessageBurstCapacity(),
                properties.getRateLimit().getUserMessageTokensPerSecond()));
        return bucket.tryAcquire();
    }
}
