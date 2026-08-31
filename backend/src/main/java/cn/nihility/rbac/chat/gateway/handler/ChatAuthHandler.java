package cn.nihility.rbac.chat.gateway.handler;

import cn.nihility.rbac.auth.service.TokenService;
import cn.nihility.rbac.chat.constant.ChatErrorCode;
import cn.nihility.rbac.chat.gateway.ChatAttributeKeys;
import cn.nihility.rbac.chat.gateway.ChatSessionRegistry;
import cn.nihility.rbac.chat.gateway.config.ChatGatewayProperties;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrame;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrameType;
import cn.nihility.rbac.chat.gateway.protocol.body.ErrorFrameBody;
import cn.nihility.rbac.chat.gateway.protocol.body.LoginAckFrameBody;
import cn.nihility.rbac.chat.gateway.protocol.body.LoginFrameBody;
import cn.nihility.rbac.chat.gateway.support.OfflineMessagePushService;
import cn.nihility.rbac.common.util.JacksonUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 认证握手网关（chat-messaging spec"Netty 网关连接建立与认证绑定"、chat-security spec
 * "认证握手超时强制断开"需求，design.md Decision 4）：连接建立后限时等待 {@code LOGIN} 帧，
 * 校验通过前拦截除 {@code LOGIN}/{@code HEARTBEAT} 外的一切业务帧；认证成功后登记
 * {@link ChatSessionRegistry} 并触发离线消息补偿推送；连接失活/异常时统一清理会话映射与
 * 认证超时定时任务。放置在业务 {@code EventExecutorGroup} 中执行（见
 * {@code ChatChannelInitializer}），{@link TokenService#verifyAccessKey} 是阻塞的 Redis
 * 调用，不占用 IO 线程。无状态，标记为 {@link ChannelHandler.Sharable} 供所有 Channel 共用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class ChatAuthHandler extends SimpleChannelInboundHandler<ChatFrame> {

    /** 会话令牌业务逻辑接口，复用既有登录态校验。 */
    private final TokenService tokenService;

    /** 会话/多端登录映射表。 */
    private final ChatSessionRegistry sessionRegistry;

    /** 聊天网关配置。 */
    private final ChatGatewayProperties properties;

    /** 离线消息补偿推送服务。 */
    private final OfflineMessagePushService offlineMessagePushService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.channel().attr(ChatAttributeKeys.AUTHENTICATED).set(false);
        ScheduledFuture<?> timeoutTask = ctx.channel().eventLoop().schedule(() -> {
            if (!Boolean.TRUE.equals(ctx.channel().attr(ChatAttributeKeys.AUTHENTICATED).get())) {
                log.debug("连接 {} 在 {} 秒内未完成认证，主动关闭", ctx.channel().id(), properties.getAuthTimeoutSeconds());
                ctx.close();
            }
        }, properties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
        ctx.channel().attr(ChatAttributeKeys.AUTH_TIMEOUT_TASK).set(timeoutTask);
        ctx.fireChannelActive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatFrame frame) {
        if (frame.getType() == ChatFrameType.LOGIN) {
            handleLogin(ctx, frame);
            return;
        }
        if (frame.getType() == ChatFrameType.HEARTBEAT) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.HEARTBEAT_ACK, null));
            return;
        }
        boolean authenticated = Boolean.TRUE.equals(ctx.channel().attr(ChatAttributeKeys.AUTHENTICATED).get());
        if (!authenticated) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.ERROR,
                    ErrorFrameBody.of(ChatErrorCode.UNAUTHENTICATED, "尚未完成认证，无法处理业务消息", null)));
            return;
        }
        ctx.fireChannelRead(frame);
    }

    /**
     * 处理认证帧：校验 accessKey，成功则绑定 userId 并登记会话映射、触发离线消息补偿推送；
     * 失败则回复失败帧并关闭连接。
     *
     * @param ctx   Channel 上下文
     * @param frame 认证帧
     */
    private void handleLogin(ChannelHandlerContext ctx, ChatFrame frame) {
        LoginFrameBody body;
        try {
            body = JacksonUtils.toObj(frame.getBody(), LoginFrameBody.class);
        } catch (Exception e) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.LOGIN_ACK, LoginAckFrameBody.failure("认证帧格式错误")));
            ctx.close();
            return;
        }

        Optional<Long> userIdOpt = StringUtils.hasText(body.getAccessKey())
                ? tokenService.verifyAccessKey(body.getAccessKey())
                : Optional.empty();
        if (userIdOpt.isEmpty()) {
            ctx.writeAndFlush(ChatFrame.of(ChatFrameType.LOGIN_ACK, LoginAckFrameBody.failure("accessKey 无效或已过期")));
            ctx.close();
            return;
        }

        Long userId = userIdOpt.get();
        cancelAuthTimeout(ctx);
        ctx.channel().attr(ChatAttributeKeys.AUTHENTICATED).set(true);
        ctx.channel().attr(ChatAttributeKeys.USER_ID).set(userId);
        sessionRegistry.register(userId, ctx.channel());
        ctx.writeAndFlush(ChatFrame.of(ChatFrameType.LOGIN_ACK, LoginAckFrameBody.success(userId)));

        offlineMessagePushService.pushPendingMessages(userId, ctx.channel());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cleanup(ctx);
        ctx.fireChannelInactive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("连接 {} 发生异常：{}", ctx.channel().id(), cause.getMessage());
        cleanup(ctx);
        ctx.close();
    }

    /**
     * 清理认证超时定时任务与会话映射。
     *
     * @param ctx Channel 上下文
     */
    private void cleanup(ChannelHandlerContext ctx) {
        cancelAuthTimeout(ctx);
        Long userId = ctx.channel().attr(ChatAttributeKeys.USER_ID).get();
        if (userId != null) {
            sessionRegistry.remove(userId, ctx.channel());
        }
    }

    /**
     * 取消认证超时定时任务（如已完成认证或连接已关闭）。
     *
     * @param ctx Channel 上下文
     */
    private void cancelAuthTimeout(ChannelHandlerContext ctx) {
        ScheduledFuture<?> task = ctx.channel().attr(ChatAttributeKeys.AUTH_TIMEOUT_TASK).get();
        if (task != null) {
            task.cancel(false);
        }
    }
}
