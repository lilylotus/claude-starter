package cn.nihility.rbac.chat.gateway.config;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天网关业务处理线程池配置：{@code ChatChannelInitializer} 把认证/业务 Handler 加入
 * 该线程池对应的 pipeline 分组，与 boss/worker 的 IO 线程完全隔离，DB/敏感词过滤/限流等
 * 阻塞操作不会占用 IO 线程（design.md tasks.md 4.7）。{@code destroyMethod} 交由 Spring
 * 容器在应用关闭时调用，优雅释放线程池资源。
 */
@Configuration
@RequiredArgsConstructor
public class ChatBusinessExecutorConfig {

    /** 聊天网关配置。 */
    private final ChatGatewayProperties properties;

    /**
     * 聊天网关业务处理线程池 Bean。
     *
     * @return 业务处理线程池
     */
    @Bean(destroyMethod = "shutdownGracefully")
    public EventExecutorGroup chatBusinessExecutorGroup() {
        return new DefaultEventExecutorGroup(properties.getBusinessThreads());
    }
}
