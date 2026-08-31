package cn.nihility.rbac.chat.gateway.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 聊天网关 TLS 上下文配置（chat-security spec"传输层加密"需求）。{@code chatSslContext} Bean
 * 通过 {@link ConditionalOnProperty} 整体按需注册，而不是"注册一个可能返回 {@code null} 的
 * Bean"——Spring 的 {@code @Bean} 工厂方法返回 {@code null} 时，其他 Bean 通过构造器按类型
 * 注入该依赖会直接抛出 {@code NoSuchBeanDefinitionException}（NullBean 不参与按类型解析），
 * 会导致整个应用上下文启动失败，这是本地手工验证阶段发现的真实问题，不是理论假设。
 * {@code ChatChannelInitializer} 改为通过 {@code ObjectProvider<SslContext>} 消费本 Bean，
 * TLS 关闭（{@code chat.gateway.tls.enabled=false}）时该 Bean 根本不存在，
 * {@code ObjectProvider#getIfAvailable()} 安全返回 {@code null}，跳过 SslHandler。
 * 该开关仅限本地开发环境用于简化调试，生产环境必须开启并配置真实证书路径。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatTlsConfig {

    /** 聊天网关配置。 */
    private final ChatGatewayProperties properties;

    /**
     * 构建聊天网关的 {@link SslContext}：优先使用配置的证书/私钥文件；未配置时自动生成一份
     * 自签名证书兜底（打印明确的"仅限开发环境"警告日志，chat-security spec 要求该开关有
     * 明确的仅限开发环境标注）。仅当 {@code chat.gateway.tls.enabled} 为 {@code true}
     * （或未配置，默认值即 {@code true}）时才会注册本 Bean。
     *
     * @return 构建好的 {@link SslContext}
     * @throws Exception 证书加载/自签名证书生成失败
     */
    @Bean
    @ConditionalOnProperty(prefix = "chat.gateway.tls", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SslContext chatSslContext() throws Exception {
        String certPath = properties.getTls().getCertPath();
        String keyPath = properties.getTls().getKeyPath();
        if (StringUtils.hasText(certPath) && StringUtils.hasText(keyPath)) {
            return SslContextBuilder.forServer(new File(certPath), new File(keyPath)).build();
        }

        log.warn("聊天网关 TLS 证书路径（chat.gateway.tls.cert-path/key-path）未配置，"
                + "自动生成自签名证书兜底，该证书仅限本地开发环境使用，"
                + "生产环境务必显式配置真实证书路径");
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        return SslContextBuilder.forServer(selfSignedCertificate.certificate(), selfSignedCertificate.privateKey())
                .build();
    }
}
