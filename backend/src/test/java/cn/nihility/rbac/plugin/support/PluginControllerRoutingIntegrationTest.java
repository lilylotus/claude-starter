package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.plugin.annotation.PluginOverride;
import cn.nihility.rbac.plugin.support.fixture.FakeMainController;
import cn.nihility.rbac.plugin.testsupport.PluginJarTestSupport;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件 Controller 暴露与覆盖的 MVC 路由集成测试（plugin-jar-upgrade change tasks.md
 * 4.1/4.4/4.5）：用 {@link WebApplicationContextRunner} 搭建含真实
 * {@code DispatcherServlet}/{@code RequestMappingHandlerMapping} 基础设施、但不依赖真实
 * 数据库/Redis、也不监听真实端口的最小 Spring MVC 容器，用 {@link MockMvc} 验证插件
 * Controller 能被正确路由。
 */
class PluginControllerRoutingIntegrationTest {

    /**
     * JUnit 管理的临时目录，每个用例各自建立独立的 {@code plugins/} 子目录；成功加载插件的
     * {@link java.net.URLClassLoader} 按生产设计不关闭，Windows 下会一直持有 jar 文件句柄，
     * 因此用 {@code CleanupMode.NEVER} 避免与被测逻辑无关的平台清理失败误报为用例失败
     * （详见 {@code PluginBeanDefinitionRegistrarIntegrationTest} 同名字段注释）。
     */
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER)
    private Path tempDir;

    /**
     * 加载一个包含 {@code @RestController} 的插件后，其请求路径应可正常访问并返回插件实现的
     * 响应，同时不影响主程序自身已有路由（plugin-jar-management capability spec "加载包含
     * Controller 的插件"，tasks.md 4.1）。
     */
    @Test
    void pluginController_shouldBeRoutable_andMainControllerUnaffected() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        String className = "com.example.pluginfixture.controller.PluginHelloController";
        Properties properties = new Properties();
        properties.setProperty("name", "helloPlugin");
        PluginJarTestSupport.buildJar(pluginsDir.resolve("hello-controller.jar"),
                Map.of(className, "package com.example.pluginfixture.controller;"
                        + "import org.springframework.web.bind.annotation.GetMapping;"
                        + "import org.springframework.web.bind.annotation.RestController;"
                        + "@RestController public class PluginHelloController {"
                        + "@GetMapping(\"/plugin/hello\") public String hello() { return \"plugin-controller\"; } }"),
                properties, RestController.class, GetMapping.class);

        newRunner(pluginsDir).withBean(FakeMainController.class).run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get("/plugin/hello")).andExpect(status().isOk()).andExpect(content().string("plugin-controller"));
            mockMvc.perform(get("/fixture/main/hello")).andExpect(status().isOk())
                    .andExpect(content().string("main-controller"));
        });
    }

    /**
     * 插件声明覆盖某个已存在的 Controller 时，原路径后续请求应由插件实现处理，且启动期不出现
     * "Ambiguous mapping" 异常（plugin-bean-override capability spec "覆盖 Controller 接口"，
     * tasks.md 4.4）。
     */
    @Test
    void pluginControllerOverride_shouldServePluginImplementation_withoutAmbiguousMapping() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        String className = "com.example.pluginfixture.controlleroverride.PluginOverrideController";
        Properties properties = new Properties();
        properties.setProperty("name", "overrideControllerPlugin");
        PluginJarTestSupport.buildJar(pluginsDir.resolve("override-controller.jar"),
                Map.of(className, "package com.example.pluginfixture.controlleroverride;"
                        + "import cn.nihility.rbac.plugin.annotation.PluginOverride;"
                        + "import cn.nihility.rbac.plugin.support.fixture.FakeMainController;"
                        + "import org.springframework.web.bind.annotation.GetMapping;"
                        + "import org.springframework.web.bind.annotation.RequestMapping;"
                        + "import org.springframework.web.bind.annotation.RestController;"
                        + "@RestController @RequestMapping(\"/fixture/main\") @PluginOverride(target = FakeMainController.class)"
                        + "public class PluginOverrideController {"
                        + "@GetMapping(\"/hello\") public String hello() { return \"plugin-controller-override\"; } }"),
                properties, RestController.class, GetMapping.class, RequestMapping.class, PluginOverride.class,
                FakeMainController.class);

        newRunner(pluginsDir).withBean(FakeMainController.class).run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get("/fixture/main/hello")).andExpect(status().isOk())
                    .andExpect(content().string("plugin-controller-override"));
        });
    }

    /**
     * 插件未声明覆盖、但其请求路径与主程序已有路径冲突时，该类的 Bean 定义注册应在定义阶段
     * 被拒绝（不影响主程序原有路由），而不是退化到运行期 {@code RequestMappingHandlerMapping}
     * 初始化才抛出"Ambiguous mapping"导致启动失败（plugin-jar-management capability spec
     * "插件与已知路径/Bean 定义冲突"，tasks.md 4.5）。
     */
    @Test
    void pluginControllerPathConflict_shouldBeSkipped_withoutBreakingMainRoute() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        String className = "com.example.pluginfixture.pathconflict.PluginConflictingController";
        Properties properties = new Properties();
        properties.setProperty("name", "conflictingPlugin");
        PluginJarTestSupport.buildJar(pluginsDir.resolve("path-conflict.jar"),
                Map.of(className, "package com.example.pluginfixture.pathconflict;"
                        + "import org.springframework.web.bind.annotation.GetMapping;"
                        + "import org.springframework.web.bind.annotation.RequestMapping;"
                        + "import org.springframework.web.bind.annotation.RestController;"
                        + "@RestController @RequestMapping(\"/fixture/main\")"
                        + "public class PluginConflictingController {"
                        + "@GetMapping(\"/hello\") public String hello() { return \"plugin-conflict\"; } }"),
                properties, RestController.class, GetMapping.class, RequestMapping.class);

        newRunner(pluginsDir).withBean(FakeMainController.class).run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            // 冲突路径请求仍由主程序原有实现响应，插件版本未生效。
            mockMvc.perform(get("/fixture/main/hello")).andExpect(status().isOk())
                    .andExpect(content().string("main-controller"));

            PluginRegistry registry = context.getBean(PluginRegistry.class);
            PluginInfo pluginInfo = registry.getPlugins().get(0);
            assertThat(pluginInfo.getStatus()).isEqualTo(PluginStatus.REGISTERED);
            assertThat(pluginInfo.getSkippedClasses()).hasSize(1);
            assertThat(pluginInfo.getSkippedClasses().get(0).reason()).contains("/fixture/main/hello");
        });
    }

    /**
     * 构造基础 {@link WebApplicationContextRunner}：开启 Bean 定义覆盖、接入最小 MVC
     * 自动配置集合、注册 {@link PluginBeanDefinitionRegistrar}、指向指定插件目录。
     *
     * @param pluginsDir 插件目录
     * @return 已完成基础配置的 runner
     */
    private WebApplicationContextRunner newRunner(Path pluginsDir) {
        return new WebApplicationContextRunner().withInitializer(PluginBeanDefinitionRegistrarIntegrationTest.ALLOW_OVERRIDING)
                .withConfiguration(AutoConfigurations.of(DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class))
                .withBean(PluginBeanDefinitionRegistrar.class)
                .withPropertyValues("rbac.plugin.directory=" + pluginsDir);
    }
}
