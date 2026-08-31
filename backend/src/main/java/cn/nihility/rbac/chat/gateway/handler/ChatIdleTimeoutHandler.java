package cn.nihility.rbac.chat.gateway.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 读空闲超时处理：与 {@link io.netty.handler.timeout.IdleStateHandler}（每 Channel 独立
 * 实例，负责计时并在超时时触发 {@link IdleStateEvent}）配套使用，本 Handler 本身无状态，
 * 标记为 {@link ChannelHandler.Sharable} 供所有 Channel 共用同一实例（chat-security spec
 * "心跳保活与空闲断连"需求）。连接关闭后触发 {@code channelInactive}，映射清理统一在
 * {@link ChatAuthHandler} 中完成。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class ChatIdleTimeoutHandler extends ChannelInboundHandlerAdapter {

    /**
     * {@inheritDoc}
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent idleStateEvent && idleStateEvent.state() == IdleState.READER_IDLE) {
            log.debug("连接 {} 空闲超时未收到任何帧，主动关闭", ctx.channel().id());
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }
}
