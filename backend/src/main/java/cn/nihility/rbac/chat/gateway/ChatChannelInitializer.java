package cn.nihility.rbac.chat.gateway;

import cn.nihility.rbac.chat.gateway.config.ChatGatewayProperties;
import cn.nihility.rbac.chat.gateway.handler.ChatAuthHandler;
import cn.nihility.rbac.chat.gateway.handler.ChatBusinessHandler;
import cn.nihility.rbac.chat.gateway.handler.ChatConnectionRateLimitHandler;
import cn.nihility.rbac.chat.gateway.handler.ChatIdleTimeoutHandler;
import cn.nihility.rbac.chat.gateway.handler.ChatWebSocketFrameCodecHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 聊天网关 Netty pipeline 装配：TLS（可选） -> HTTP 编解码 -> WebSocket 升级 -> 空闲检测 ->
 * 业务协议帧编解码 -> 认证握手（业务线程池） -> 业务消息处理（业务线程池），对应 design.md
 * Decision 1/2 与 tasks.md 4.2/4.3/4.4。每个新连接调用一次 {@link #initChannel}，
 * 有状态的 Handler（{@link IdleStateHandler}/{@link WebSocketServerProtocolHandler} 等）
 * 各自 {@code new} 一份，无状态的业务 Handler 复用 Spring 单例（标记
 * {@code @ChannelHandler.Sharable}）。
 */
@Component
@RequiredArgsConstructor
public class ChatChannelInitializer extends ChannelInitializer<SocketChannel> {

    /** 聊天网关配置。 */
    private final ChatGatewayProperties properties;

    /**
     * TLS 上下文的 {@link ObjectProvider} 包装：TLS 关闭时 {@code chatSslContext} Bean
     * 根本不存在（见 {@code ChatTlsConfig} 的 {@code @ConditionalOnProperty}），直接按类型
     * 注入 {@link SslContext} 会在应用启动时抛出 {@code NoSuchBeanDefinitionException}，
     * 必须通过 {@link ObjectProvider#getIfAvailable()} 安全获取"可能不存在"的可选依赖。
     */
    private final ObjectProvider<SslContext> chatSslContextProvider;

    /** 未认证阶段按来源 IP 建连限流 Handler。 */
    private final ChatConnectionRateLimitHandler connectionRateLimitHandler;

    /** 业务协议帧 <-> WebSocket 二进制帧编解码 Handler。 */
    private final ChatWebSocketFrameCodecHandler frameCodecHandler;

    /** 读空闲超时处理 Handler。 */
    private final ChatIdleTimeoutHandler idleTimeoutHandler;

    /** 认证握手 Handler。 */
    private final ChatAuthHandler authHandler;

    /** 业务消息处理 Handler。 */
    private final ChatBusinessHandler businessHandler;

    /** 业务处理线程池，隔离 IO 线程与阻塞的业务逻辑。 */
    private final EventExecutorGroup chatBusinessExecutorGroup;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("connectionRateLimit", connectionRateLimitHandler);
        SslContext sslContext = chatSslContextProvider.getIfAvailable();
        if (sslContext != null) {
            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
        }
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(64 * 1024));
        pipeline.addLast("wsProtocol",
                new WebSocketServerProtocolHandler(properties.getWebsocketPath(), null, true));
        pipeline.addLast("wsAggregator", new WebSocketFrameAggregator(properties.getMaxFramePayloadLength()));
        pipeline.addLast("idleState",
                new IdleStateHandler(properties.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS));
        pipeline.addLast("idleTimeout", idleTimeoutHandler);
        pipeline.addLast("frameCodec", frameCodecHandler);
        pipeline.addLast(chatBusinessExecutorGroup, "auth", authHandler);
        pipeline.addLast(chatBusinessExecutorGroup, "business", businessHandler);
    }
}
