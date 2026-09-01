package cn.nihility.rbac.chat.gateway.support;

import cn.nihility.rbac.chat.gateway.config.ChatGatewayProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * msgId 去重短路缓存：命中时直接从缓存重建 ACK 帧返回，不打库；未命中再走
 * {@code ChatMessageService} 的数据库层幂等处理（{@code tab_chat_message.msg_id} 唯一索引
 * 兜底，design.md Decision 6）。缓存跨重启失效属于预期行为，正确性由数据库唯一索引保证，
 * 仅在重启窗口期内去重命中率短暂下降。
 */
@Component
public class ChatMessageDedupCache {

    /** 缓存实例。 */
    private final Cache<String, ChatAckCacheEntry> cache;

    public ChatMessageDedupCache(ChatGatewayProperties properties) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getDedup().getCacheMaxSize())
                .expireAfterWrite(Duration.ofSeconds(properties.getDedup().getCacheTtlSeconds()))
                .build();
    }

    /**
     * 查询缓存中是否已有指定 msgId 的处理结果。
     *
     * @param msgId 客户端生成的消息幂等 id
     * @return 缓存的 ACK 信息，未命中时返回 {@code null}
     */
    public ChatAckCacheEntry getIfPresent(String msgId) {
        return cache.getIfPresent(msgId);
    }

    /**
     * 写入/覆盖指定 msgId 的处理结果缓存。
     *
     * @param msgId 客户端生成的消息幂等 id
     * @param entry ACK 信息
     */
    public void put(String msgId, ChatAckCacheEntry entry) {
        cache.put(msgId, entry);
    }
}
