package cn.nihility.rbac.chat.gateway.handler;

import cn.nihility.rbac.chat.gateway.support.ChatRateLimiterManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 未认证阶段按来源 IP 的建连限流（chat-security spec"按用户与按连接来源限流"需求），
 * 是 pipeline 中第一个业务 Handler，在 WebSocket 握手之前就拒绝超限来源，防止连接风暴
 * 消耗后续 HTTP/WS 编解码资源。无状态，标记为 {@link ChannelHandler.Sharable} 供所有
 * Channel 共用同一实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ChannelHandler.Sharable
public class ChatConnectionRateLimitHandler extends ChannelInboundHandlerAdapter {

    /** 限流管理器。 */
    private final ChatRateLimiterManager rateLimiterManager;

    /**
     * {@inheritDoc}
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String ip = resolveRemoteIp(ctx.channel());
        if (!rateLimiterManager.tryAcquireConnection(ip)) {
            log.debug("来源 IP {} 建连频率超限，拒绝连接", ip);
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }

    /**
     * 解析连接的来源 IP。
     *
     * @param channel 当前连接
     * @return 来源 IP，无法解析时返回 {@code "unknown"}
     */
    private String resolveRemoteIp(Channel channel) {
        SocketAddress remoteAddress = channel.remoteAddress();
        if (remoteAddress instanceof InetSocketAddress inetSocketAddress) {
            return inetSocketAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
