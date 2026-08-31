package cn.nihility.rbac.chat.gateway.handler;

import cn.nihility.rbac.chat.constant.ChatErrorCode;
import cn.nihility.rbac.chat.dto.MessageRecipient;
import cn.nihility.rbac.chat.dto.SendMessageResult;
import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import cn.nihility.rbac.chat.gateway.ChatAttributeKeys;
import cn.nihility.rbac.chat.gateway.ChatSessionRegistry;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrame;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrameType;
import cn.nihility.rbac.chat.gateway.protocol.body.AckFrameBody;
import cn.nihility.rbac.chat.gateway.protocol.body.ChatGroupFrameBody;
import cn.nihility.rbac.chat.gateway.protocol.body.ChatSingleFrameBody;
import cn.nihility.rbac.chat.gateway.protocol.body.ErrorFrameBody;
import cn.nihility.rbac.chat.gateway.protocol.body.MessagePushFrameBody;
import cn.nihility.rbac.chat.gateway.support.ChatAckCacheEntry;
import cn.nihility.rbac.chat.gateway.support.ChatMessageDedupCache;
import cn.nihility.rbac.chat.gateway.support.ChatRateLimiterManager;
import cn.nihility.rbac.chat.service.ChatMessageService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 业务消息处理网关：单聊/群聊消息的限流、去重短路、发送处理、ACK 回执与在线投递
 * （chat-messaging spec"单聊消息发送与路由"/"群聊消息发送与路由"/"消息 ACK 确认与幂等
 * 重发"需求，chat-security spec"按用户与按连接来源限流"/"消息去重防重放"需求）。仅接收
 * {@link ChatAuthHandler} 放行的已认证连接的业务帧。放置在业务 {@code EventExecutorGroup}
 * 中执行，DB/敏感词过滤等阻塞操作不占用 IO 线程。无状态，标记为
 * {@link ChannelHandler.Sharable} 供所有 Channel 共用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class ChatBusinessHandler extends SimpleChannelInboundHandler<ChatFrame> {

    /** 消息发送与历史查询业务逻辑接口。 */
    private final ChatMessageService chatMessageService;

    /** 按用户消息发送令牌桶限流管理器。 */
    private final ChatRateLimiterManager rateLimiterManager;

    /** msgId 去重短路缓存。 */
    private final ChatMessageDedupCache dedupCache;

    /** 会话/多端登录映射表。 */
    private final ChatSessionRegistry sessionRegistry;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatFrame frame) {
        switch (frame.getType()) {
            case CHAT_SINGLE -> handleSingle(ctx, frame);
            case CHAT_GROUP -> handleGroup(ctx, frame);
            default -> ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ERROR,
                    ErrorFrameBody.of(ChatErrorCode.INVALID_FRAME, "不支持的消息类型", null)));
        }
    }

    /**
     * 处理单聊消息发送帧。
     *
     * @param ctx   Channel 上下文
     * @param frame 单聊消息发送帧
     */
    private void handleSingle(ChannelHandlerContext ctx, ChatFrame frame) {
        Long senderId = ctx.channel().attr(ChatAttributeKeys.USER_ID).get();
        ChatSingleFrameBody body = parseBody(ctx, frame, ChatSingleFrameBody.class);
        if (body == null || !validate(ctx, body.getMsgId(), body.getContent()) || respondFromCache(ctx,
                body.getMsgId()) || !checkRateLimit(ctx, senderId, body.getMsgId())) {
            return;
        }
        try {
            SendMessageResult result = chatMessageService.sendSingleMessage(senderId, body.getToUserId(),
                    body.getMsgId(), body.getMsgType(), body.getContent(), sessionRegistry::hasOnlineChannel);
            afterSend(ctx, result);
        } catch (BusinessException e) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ERROR,
                    ErrorFrameBody.of(e.getCode(), e.getMessage(), body.getMsgId())));
        }
    }

    /**
     * 处理群聊消息发送帧。
     *
     * @param ctx   Channel 上下文
     * @param frame 群聊消息发送帧
     */
    private void handleGroup(ChannelHandlerContext ctx, ChatFrame frame) {
        Long senderId = ctx.channel().attr(ChatAttributeKeys.USER_ID).get();
        ChatGroupFrameBody body = parseBody(ctx, frame, ChatGroupFrameBody.class);
        if (body == null || !validate(ctx, body.getMsgId(), body.getContent()) || respondFromCache(ctx,
                body.getMsgId()) || !checkRateLimit(ctx, senderId, body.getMsgId())) {
            return;
        }
        try {
            SendMessageResult result = chatMessageService.sendGroupMessage(senderId, body.getConversationId(),
                    body.getMsgId(), body.getMsgType(), body.getContent(), sessionRegistry::hasOnlineChannel);
            afterSend(ctx, result);
        } catch (BusinessException e) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ERROR,
                    ErrorFrameBody.of(e.getCode(), e.getMessage(), body.getMsgId())));
        }
    }

    /**
     * 发送处理成功后：回复 ACK 帧、写入去重缓存、向在线接收方推送 {@code MESSAGE_PUSH} 帧
     * （重复提交场景 {@code recipients} 为空，跳过推送）。
     *
     * @param ctx    Channel 上下文
     * @param result 发送处理结果
     */
    private void afterSend(ChannelHandlerContext ctx, SendMessageResult result) {
        ChatMessageEntity message = result.getMessage();
        ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ACK,
                AckFrameBody.of(message.getMsgId(), message.getConversationId(), message.getConversationSeq(),
                        message.getSendTime())));
        dedupCache.put(message.getMsgId(), new ChatAckCacheEntry(message.getMsgId(), message.getConversationId(),
                message.getConversationSeq(), message.getSendTime()));

        if (result.isDuplicate()) {
            return;
        }
        ChatFrame pushFrame = ChatFrame.of(ChatFrameType.MESSAGE_PUSH, MessagePushFrameBody.from(message, false));
        for (MessageRecipient recipient : result.getRecipients()) {
            if (!recipient.online()) {
                continue;
            }
            for (Channel channel : sessionRegistry.getChannels(recipient.userId())) {
                if (channel.isActive()) {
                    channel.writeAndFlush(pushFrame);
                }
            }
        }
    }

    /**
     * 把消息体字节解析为目标类型，解析失败回复 {@code ERROR} 帧。
     *
     * @param ctx   Channel 上下文
     * @param frame 协议帧
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 解析结果，失败时返回 {@code null}
     */
    private <T> T parseBody(ChannelHandlerContext ctx, ChatFrame frame, Class<T> clazz) {
        try {
            return JacksonUtils.toObj(frame.getBody(), clazz);
        } catch (Exception e) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ERROR,
                    ErrorFrameBody.of(ChatErrorCode.INVALID_FRAME, "消息体格式错误", null)));
            return null;
        }
    }

    /**
     * 校验 {@code msgId}/{@code content} 均非空。
     *
     * @param ctx     Channel 上下文
     * @param msgId   客户端生成的消息幂等 id
     * @param content 消息内容
     * @return 是否通过校验
     */
    private boolean validate(ChannelHandlerContext ctx, String msgId, String content) {
        if (!StringUtils.hasText(msgId) || !StringUtils.hasText(content)) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ERROR,
                    ErrorFrameBody.of(ChatErrorCode.INVALID_FRAME, "msgId/content 不能为空", msgId)));
            return false;
        }
        return true;
    }

    /**
     * 若去重缓存已有该 msgId 的处理结果，直接重放 ACK 帧，不再进入发送处理流程。
     *
     * @param ctx   Channel 上下文
     * @param msgId 客户端生成的消息幂等 id
     * @return 是否命中缓存并已处理
     */
    private boolean respondFromCache(ChannelHandlerContext ctx, String msgId) {
        ChatAckCacheEntry cached = dedupCache.getIfPresent(msgId);
        if (cached == null) {
            return false;
        }
        ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ACK,
                AckFrameBody.of(cached.msgId(), cached.conversationId(), cached.conversationSeq(),
                        cached.sendTime())));
        return true;
    }

    /**
     * 按用户 id 做消息发送限流校验。
     *
     * @param ctx    Channel 上下文
     * @param userId 用户 id
     * @param msgId  客户端生成的消息幂等 id，用于错误帧关联
     * @return 是否允许本次发送
     */
    private boolean checkRateLimit(ChannelHandlerContext ctx, Long userId, String msgId) {
        if (rateLimiterManager.tryAcquireMessage(userId)) {
            return true;
        }
        ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ERROR,
                ErrorFrameBody.of(ChatErrorCode.RATE_LIMITED, "发送过于频繁，请稍后再试", msgId)));
        return false;
    }
}
