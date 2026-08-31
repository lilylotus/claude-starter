package cn.nihility.rbac.chat.gateway.handler;

import cn.nihility.rbac.chat.constant.ChatErrorCode;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrame;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrameCodec;
import cn.nihility.rbac.chat.gateway.protocol.ChatFrameType;
import cn.nihility.rbac.chat.gateway.protocol.body.ErrorFrameBody;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 业务协议帧（{@link ChatFrame}）与 WebSocket 二进制帧（{@link BinaryWebSocketFrame}）之间
 * 的双向转换：出站方向按 {@link ChatFrameCodec} 编码为字节后包装进
 * {@link BinaryWebSocketFrame}；入站方向从 {@link BinaryWebSocketFrame} 取出内容字节按
 * {@link ChatFrameCodec} 解析为 {@link ChatFrame}（design.md Decision 2）。协议帧结构
 * 损坏（帧头/长度域非法）视为客户端异常，回复 {@code ERROR} 帧后关闭连接，不把损坏数据
 * 继续往下游传递。无状态，标记为 {@link ChannelHandler.Sharable} 供所有 Channel 共用。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class ChatWebSocketFrameCodecHandler extends MessageToMessageCodec<BinaryWebSocketFrame, ChatFrame> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ChatFrame frame, List<Object> out) {
        ByteBuf encoded = ChatFrameCodec.encode(frame, ctx.alloc());
        out.add(new BinaryWebSocketFrame(encoded));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame frame, List<Object> out) {
        try {
            out.add(ChatFrameCodec.decode(frame.content()));
        } catch (IllegalArgumentException e) {
            log.debug("连接 {} 协议帧解析失败：{}，主动关闭连接", ctx.channel().id(), e.getMessage());
            ChatFrame errorFrame = ChatFrame.of(ChatFrameType.ERROR,
                    ErrorFrameBody.of(ChatErrorCode.INVALID_FRAME, "协议帧格式非法：" + e.getMessage(), null));
            ctx.writeAndFlush(new BinaryWebSocketFrame(ChatFrameCodec.encode(errorFrame, ctx.alloc())))
                    .addListener(future -> ctx.close());
        }
    }
}
