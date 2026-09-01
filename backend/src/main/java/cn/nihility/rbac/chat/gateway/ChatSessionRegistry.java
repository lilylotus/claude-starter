package cn.nihility.rbac.chat.gateway;

import io.netty.channel.Channel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 进程内会话/多端登录映射表：{@code userId -> Set<Channel>}（design.md Decision 5）。
 * 认证成功时注册，连接失活/异常时清理（{@code ChatAuthHandler} 的
 * {@code channelInactive}/{@code exceptionCaught} 统一维护），本阶段单节点，仅需进程内
 * 内存结构，不落 Redis（跨节点路由留给 chat-cluster 阶段）。
 */
@Component
public class ChatSessionRegistry {

    /** userId -> 在线 Channel 集合，{@code Set} 使用 {@link ConcurrentHashMap#newKeySet()} 保证并发安全。 */
    private final ConcurrentHashMap<Long, Set<Channel>> userChannels = new ConcurrentHashMap<>();

    /**
     * 注册一条已认证连接。
     *
     * @param userId  用户 id
     * @param channel 已认证的 Channel
     */
    public void register(Long userId, Channel channel) {
        userChannels.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(channel);
    }

    /**
     * 移除一条连接的映射；若该用户已无任何在线连接，同时移除整个 userId 条目，避免
     * 空集合长期占用内存。
     *
     * @param userId  用户 id
     * @param channel 待移除的 Channel
     */
    public void remove(Long userId, Channel channel) {
        Set<Channel> channels = userChannels.get(userId);
        if (channels == null) {
            return;
        }
        channels.remove(channel);
        if (channels.isEmpty()) {
            userChannels.remove(userId, channels);
        }
    }

    /**
     * 查询指定用户当前全部在线连接的快照。
     *
     * @param userId 用户 id
     * @return 在线 Channel 集合快照，无在线连接时返回空集合
     */
    public Set<Channel> getChannels(Long userId) {
        Set<Channel> channels = userChannels.get(userId);
        return channels == null ? Set.of() : Set.copyOf(channels);
    }

    /**
     * 判断指定用户当前是否存在至少一个在线连接。
     *
     * @param userId 用户 id
     * @return 是否在线
     */
    public boolean hasOnlineChannel(Long userId) {
        Set<Channel> channels = userChannels.get(userId);
        return channels != null && !channels.isEmpty();
    }
}
