package cn.nihility.rbac.chat.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天网关配置，绑定前缀 {@code chat.gateway}（chat-gateway-core change design.md
 * Migration Plan 3）。{@link #enabled} 支持整体关闭网关监听用于问题回滚，不需要回退代码。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chat.gateway")
public class ChatGatewayProperties {

    /** 是否启用聊天网关监听，默认开启；置为 {@code false} 可整体关闭网关（不影响其余 REST 接口）。 */
    private boolean enabled = true;

    /** 监听端口，默认 48091，与业务 HTTP 端口（48080）分离。 */
    private int port = 48091;

    /** WebSocket 升级路径。 */
    private String websocketPath = "/ws/chat";

    /** boss 线程数（负责接受新连接）。 */
    private int bossThreads = 1;

    /** worker 线程数（负责已建立连接的 IO），{@code 0} 表示使用 Netty 默认（2 * CPU 核数）。 */
    private int workerThreads = 0;

    /** 业务处理线程池大小，隔离 IO 线程与业务逻辑（DB/敏感词过滤/限流等阻塞操作）。 */
    private int businessThreads = 16;

    /** 单个 WebSocket 帧（含分片聚合后）允许的最大字节数。 */
    private int maxFramePayloadLength = 1024 * 1024;

    /** 连接建立后必须在此时间窗口内完成认证握手，超时强制断开（秒），默认 10 秒。 */
    private long authTimeoutSeconds = 10;

    /** 读空闲超时阈值（秒），超过该时长未收到任何帧（含心跳）则主动断开，默认 60 秒。 */
    private long idleTimeoutSeconds = 60;

    /** TLS 相关配置。 */
    private Tls tls = new Tls();

    /** 限流相关配置。 */
    private RateLimit rateLimit = new RateLimit();

    /** msgId 去重短路缓存相关配置。 */
    private Dedup dedup = new Dedup();

    /** 离线消息补偿推送相关配置。 */
    private Offline offline = new Offline();

    /**
     * TLS 配置：生产环境应强制开启并配置真实证书路径；本地开发环境可通过
     * {@code enabled=false} 关闭以简化调试（仅限开发环境使用，chat-security spec
     * "传输层加密"需求）。开启但未配置证书路径时自动生成自签名证书（同样仅限开发环境，
     * 生产环境务必显式配置 {@link #certPath}/{@link #keyPath}）。
     */
    @Getter
    @Setter
    public static class Tls {

        /** 是否启用 TLS，默认开启。 */
        private boolean enabled = true;

        /** 证书文件路径（PEM 格式），为空时使用自签名证书兜底（仅限开发环境）。 */
        private String certPath;

        /** 私钥文件路径（PEM 格式），为空时使用自签名证书兜底（仅限开发环境）。 */
        private String keyPath;
    }

    /**
     * 限流配置（进程内令牌桶，design.md Decision 6，不引入 Guava/Redis）。
     */
    @Getter
    @Setter
    public static class RateLimit {

        /** 已认证用户每秒可发送消息数（令牌桶恒定速率）。 */
        private double userMessageTokensPerSecond = 5;

        /** 已认证用户消息令牌桶突发容量（允许短时突发的最大令牌数）。 */
        private int userMessageBurstCapacity = 10;

        /** 未认证阶段按来源 IP 每秒可建立的新连接数（令牌桶恒定速率）。 */
        private double ipConnectTokensPerSecond = 5;

        /** 按来源 IP 建连令牌桶突发容量。 */
        private int ipConnectBurstCapacity = 20;
    }

    /**
     * msgId 去重短路缓存配置（Caffeine，design.md Decision 6）。
     */
    @Getter
    @Setter
    public static class Dedup {

        /** 缓存条目存活时间（秒），默认 5 分钟。 */
        private long cacheTtlSeconds = 300;

        /** 缓存最大条目数，超出后按 LRU 淘汰。 */
        private long cacheMaxSize = 100_000;
    }

    /**
     * 离线消息补偿推送配置。
     */
    @Getter
    @Setter
    public static class Offline {

        /** 单次批量查询/推送的最大条数，避免大量积压消息一次性占满内存。 */
        private int maxPushBatchSize = 200;
    }
}
