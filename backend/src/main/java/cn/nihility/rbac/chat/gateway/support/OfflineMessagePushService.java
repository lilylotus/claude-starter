package cn.nihility.rbac.chat.gateway.support;

import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import cn.nihility.rbac.chat.entity.ChatMessageOfflineEntity;
import cn.nihility.rbac.chat.gateway.config.ChatGatewayProperties;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrame;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrameType;
import cn.nihility.rbac.chat.gateway.protocol.body.MessagePushFrameBody;
import cn.nihility.rbac.chat.mapper.ChatMessageMapper;
import cn.nihility.rbac.chat.mapper.ChatMessageOfflineMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.netty.channel.Channel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 离线消息补偿推送：用户重新建立连接并完成认证后，按序把此前未送达的离线消息推送给
 * 该用户（chat-messaging spec"离线消息补偿推送"需求）。每条离线记录独立更新
 * {@code delivered} 标记（不整体包一个长事务），单条更新失败只影响该条记录的下一次
 * 重试，不影响本批次其余记录，且不会因为持有长事务而阻塞其他会话的行锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineMessagePushService {

    /** 离线消息队列数据访问接口。 */
    private final ChatMessageOfflineMapper chatMessageOfflineMapper;

    /** 消息数据访问接口。 */
    private final ChatMessageMapper chatMessageMapper;

    /** 聊天网关配置。 */
    private final ChatGatewayProperties properties;

    /**
     * 按序批量推送指定用户的全部未送达离线消息到给定连接。
     *
     * @param userId  用户 id
     * @param channel 目标连接（通常是刚认证成功的连接）
     */
    public void pushPendingMessages(Long userId, Channel channel) {
        int batchSize = properties.getOffline().getMaxPushBatchSize();
        int pushedTotal = 0;
        List<ChatMessageOfflineEntity> pending;
        do {
            pending = chatMessageOfflineMapper.selectList(new LambdaQueryWrapper<ChatMessageOfflineEntity>()
                    .eq(ChatMessageOfflineEntity::getReceiverId, userId)
                    .eq(ChatMessageOfflineEntity::getDelivered, false)
                    .orderByAsc(ChatMessageOfflineEntity::getId)
                    .last("LIMIT " + batchSize));
            if (pending.isEmpty()) {
                break;
            }

            List<Long> messageIds = pending.stream().map(ChatMessageOfflineEntity::getMessageId).toList();
            Map<Long, ChatMessageEntity> messages = chatMessageMapper.selectByIds(messageIds).stream()
                    .collect(Collectors.toMap(ChatMessageEntity::getId, message -> message));

            for (ChatMessageOfflineEntity offline : pending) {
                ChatMessageEntity message = messages.get(offline.getMessageId());
                if (message == null) {
                    // 关联的消息记录已不存在（理论上不应发生，容错跳过避免整批中断）。
                    markDelivered(offline);
                    continue;
                }
                if (channel.isActive()) {
                    channel.writeAndFlush(ChatFrame.of(ChatFrameType.MESSAGE_PUSH,
                            MessagePushFrameBody.from(message, true)));
                }
                markDelivered(offline);
            }
            pushedTotal += pending.size();
        } while (pending.size() == batchSize);

        if (pushedTotal > 0) {
            log.info("用户 {} 上线，已补偿推送 {} 条离线消息", userId, pushedTotal);
        }
    }

    /**
     * 标记一条离线消息记录为已送达。
     *
     * @param offline 离线消息记录
     */
    private void markDelivered(ChatMessageOfflineEntity offline) {
        offline.setDelivered(true);
        offline.setUpdateTime(LocalDateTime.now());
        chatMessageOfflineMapper.updateById(offline);
    }
}
