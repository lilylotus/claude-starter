package cn.nihility.rbac.plugin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.common.advice.GlobalResponseAdvice;
import cn.nihility.rbac.common.exception.GlobalExceptionHandler;
import cn.nihility.rbac.plugin.service.impl.PluginQueryServiceImpl;
import cn.nihility.rbac.plugin.support.PluginBeanOverride;
import cn.nihility.rbac.plugin.support.PluginInfo;
import cn.nihility.rbac.plugin.support.PluginOverrideConflict;
import cn.nihility.rbac.plugin.support.PluginRegistry;
import cn.nihility.rbac.plugin.support.PluginSkippedClass;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link PluginController} 集成测试（plugin-jar-upgrade change tasks.md 5.1）：验证
 * {@code GET /api/v1/plugins} 的响应结构（统一 {@code Result} 包装 + 插件状态/覆盖关系/
 * 冲突记录字段）。用 {@link WebApplicationContextRunner} 手工装配一个只含插件管理相关 Bean
 * 的最小 MVC 容器，不依赖真实数据库/Redis。
 */
class PluginControllerTest {

    /**
     * 响应应为统一 {@code Result} 包装结构，且完整反映预置的插件状态、覆盖记录、跳过记录、
     * 覆盖冲突记录。
     */
    @Test
    void list_shouldReturnWrappedPluginStatusResponse() throws Exception {
        PluginInfo demoPlugin = new PluginInfo("demoPlugin", "demo.jar", "1.0.0", 0);
        demoPlugin.addOverride(new PluginBeanOverride("com.example.PluginServiceImpl", "cn.nihility.rbac.example.MainService",
                "mainService"));
        demoPlugin.addSkippedClass(new PluginSkippedClass("com.example.ConflictingController", "请求路径 [/api/x] 已被 [main] 占用"));
        demoPlugin.addRegisteredBeanName("plugin.demoPlugin.DemoComponent");

        PluginInfo failedPlugin = new PluginInfo("brokenPlugin", "broken.jar", "unknown", 0);
        failedPlugin.markFailed("插件加载失败：java.io.IOException: 非法 jar 格式");

        PluginRegistry registry = new PluginRegistry();
        registry.register(demoPlugin);
        registry.register(failedPlugin);
        registry.recordConflict(new PluginOverrideConflict("cn.nihility.rbac.example.MainService", "otherPlugin", "demoPlugin",
                "mainService"));

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class))
                .withBean(PluginRegistry.class, () -> registry)
                .withBean(PluginQueryServiceImpl.class)
                .withBean(PluginController.class)
                .withBean(GlobalResponseAdvice.class)
                .withBean(GlobalExceptionHandler.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

                    mockMvc.perform(get("/api/v1/plugins"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.code").value(0))
                            .andExpect(jsonPath("$.data.plugins.length()").value(2))
                            .andExpect(jsonPath("$.data.plugins[0].name").value("demoPlugin"))
                            .andExpect(jsonPath("$.data.plugins[0].status").value("REGISTERED"))
                            .andExpect(jsonPath("$.data.plugins[0].overrides[0].beanName").value("mainService"))
                            .andExpect(jsonPath("$.data.plugins[0].skippedClasses[0].reason").value("请求路径 [/api/x] 已被 [main] 占用"))
                            .andExpect(jsonPath("$.data.plugins[0].registeredBeanNames[0]").value("plugin.demoPlugin.DemoComponent"))
                            .andExpect(jsonPath("$.data.plugins[1].name").value("brokenPlugin"))
                            .andExpect(jsonPath("$.data.plugins[1].status").value("FAILED"))
                            .andExpect(jsonPath("$.data.plugins[1].failureReason").value("插件加载失败：java.io.IOException: 非法 jar 格式"))
                            .andExpect(jsonPath("$.data.overrideConflicts.length()").value(1))
                            .andExpect(jsonPath("$.data.overrideConflicts[0].winningPluginName").value("demoPlugin"));
                });
    }
}
