package cn.nihility.rbac.plugin.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * 插件相关配置，绑定前缀 {@code rbac.plugin}（plugin-jar-upgrade change design.md
 * Decision 1/5）：插件目录路径、覆盖黑名单。
 * <p>
 * 本类同时以 {@link Component} 注册为普通 Spring Bean，供应用启动完成后的常规依赖注入场景
 * （如单元测试校验配置绑定）使用；但 {@code PluginBeanDefinitionRegistrar} 运行在
 * {@code invokeBeanFactoryPostProcessors} 阶段——早于 Spring 的
 * {@code ConfigurationPropertiesBindingPostProcessor} 注册完成，此时通过常规 DI 获取本 Bean
 * 会拿到字段未绑定的"裸对象"，因此该处理器不会注入本 Bean，而是自行用
 * {@code org.springframework.boot.context.properties.bind.Binder} 直接从
 * {@code Environment} 读取同一份配置，两者读取的是同一数据源，不存在不一致问题。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.plugin")
public class PluginProperties {

    /** 插件目录路径（相对路径相对于应用当前工作目录解析），默认 {@code plugins}。 */
    private String directory = "plugins";

    /** 覆盖相关配置。 */
    @NestedConfigurationProperty
    private OverrideProperties override = new OverrideProperties();

    /**
     * 覆盖相关配置。命名为 {@code OverrideProperties} 而非 {@code Override}，避免与
     * {@link java.lang.Override} 注解同名造成阅读混淆。
     */
    @Getter
    @Setter
    public static class OverrideProperties {

        /**
         * 覆盖黑名单：命中该名单的目标类全限定名禁止被插件覆盖（安全关键组件），默认包含
         * 认证过滤器、全局异常处理器、全局响应包装器（plugin-bean-override capability spec
         * "覆盖范围限制"）。部署方可通过配置追加，但不建议移除默认项。
         */
        private List<String> denyList = new ArrayList<>(List.of(
                "cn.nihility.rbac.auth.filter.IdentityAuthFilter",
                "cn.nihility.rbac.common.exception.GlobalExceptionHandler",
                "cn.nihility.rbac.common.advice.GlobalResponseAdvice"));
    }
}
