package cn.nihility.rbac.chat.gateway;

import cn.nihility.rbac.chat.gateway.config.ChatGatewayProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 聊天网关 Netty {@code ServerBootstrap} 生命周期管理：作为 {@link SmartLifecycle} Bean
 * 随 Spring 容器启动/优雅停止，与业务 HTTP 端口（48080）分离监听独立端口（默认 48091，
 * design.md Decision 1）。{@code chat.gateway.enabled=false} 时跳过监听，仅打日志说明，
 * 用于问题回滚而不需要回退代码（design.md Migration Plan 3）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatGatewayServer implements SmartLifecycle {

    /** 聊天网关配置。 */
    private final ChatGatewayProperties properties;

    /** Netty pipeline 装配器。 */
    private final ChatChannelInitializer channelInitializer;

    /** 是否正在运行。 */
    private volatile boolean running = false;

    /** 负责接受新连接的事件循环组。 */
    private EventLoopGroup bossGroup;

    /** 负责已建立连接 IO 的事件循环组。 */
    private EventLoopGroup workerGroup;

    /** 监听 Channel。 */
    private Channel serverChannel;

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("聊天网关已通过 chat.gateway.enabled=false 关闭，跳过端口监听");
            return;
        }
        if (!properties.getTls().isEnabled()) {
            log.warn("聊天网关 TLS 已通过 chat.gateway.tls.enabled=false 关闭，"
                    + "客户端只能通过 ws://（非 wss://）连接，该开关仅限本地开发环境使用，"
                    + "生产环境必须开启并配置真实证书");
        }

        bossGroup = new NioEventLoopGroup(properties.getBossThreads());
        workerGroup = properties.getWorkerThreads() > 0
                ? new NioEventLoopGroup(properties.getWorkerThreads())
                : new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(channelInitializer)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        try {
            serverChannel = bootstrap.bind(properties.getPort()).sync().channel();
            running = true;
            log.info("聊天网关已启动，监听端口 {}，WebSocket 路径 {}", properties.getPort(), properties.getWebsocketPath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownEventLoopGroups();
            throw new IllegalStateException("聊天网关启动失败", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close();
        }
        shutdownEventLoopGroups();
        log.info("聊天网关已停止");
    }

    /**
     * 优雅关闭 boss/worker 事件循环组。
     */
    private void shutdownEventLoopGroups() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 使用较大的启动阶段值，确保依赖的其他业务 Bean（{@code TokenService} 等）已完成初始化。
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
