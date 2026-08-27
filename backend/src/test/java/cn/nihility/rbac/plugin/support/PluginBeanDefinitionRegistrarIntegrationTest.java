package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.plugin.annotation.PluginOverride;
import cn.nihility.rbac.plugin.support.fixture.FakeDeniedService;
import cn.nihility.rbac.plugin.support.fixture.FakeDeniedServiceImpl;
import cn.nihility.rbac.plugin.support.fixture.FakeMainConsumer;
import cn.nihility.rbac.plugin.support.fixture.FakeMainService;
import cn.nihility.rbac.plugin.support.fixture.FakeMainServiceImpl;
import cn.nihility.rbac.plugin.testsupport.PluginJarTestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/**
 * {@code PluginBeanDefinitionRegistrar} 集成测试（不含 MVC 路由部分，路由相关场景见
 * {@link PluginControllerRoutingIntegrationTest}）：用 {@link ApplicationContextRunner}
 * 搭建隔离的、不依赖真实数据库/Redis 的最小 Spring 容器，验证 Bean 定义注册阶段的失败隔离
 * （tasks.md 3.2/3.3）与覆盖机制（tasks.md 4.2/4.3/4.6/4.7）。
 */
class PluginBeanDefinitionRegistrarIntegrationTest {

    /**
     * JUnit 管理的临时目录，每个用例各自建立独立的 {@code plugins/} 子目录。测试内成功加载的
     * 插件其 {@link java.net.URLClassLoader} 按生产设计不会被关闭（类需要在整个应用生命周期
     * 内保持可加载），Windows 下会一直持有 jar 文件句柄，导致 JUnit 默认的临时目录清理在
     * 用例结束后因"文件被其他程序占用"而失败；改用 {@code CleanupMode.NEVER} 交由操作系统
     * 临时目录的常规清理机制处理，避免这一与被测逻辑本身无关的平台差异导致用例误报失败。
     */
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER)
    private Path tempDir;

    /**
     * {@link ApplicationContextRunner}/{@link org.springframework.boot.test.context.runner.WebApplicationContextRunner}
     * 不经过 {@code SpringApplication.prepareContext}，不会自动应用
     * {@code spring.main.allow-bean-definition-overriding}，这里用初始化器手动在真实生产环境
     * 同样生效的时机（{@code refresh()} 之前）显式开启，以还原生产配置。
     */
    static final ApplicationContextInitializer<ConfigurableApplicationContext> ALLOW_OVERRIDING = context -> {
        if (context.getBeanFactory() instanceof DefaultListableBeanFactory listableBeanFactory) {
            listableBeanFactory.setAllowBeanDefinitionOverriding(true);
        }
    };

    /**
     * 插件 jar 本身损坏（非法 jar 格式）时，该插件标记 FAILED，主程序及其余正常插件不受影响
     * （plugin-jar-management capability spec "插件 jar 损坏"，tasks.md 3.2）。
     */
    @Test
    void corruptedPlugin_shouldFail_butOtherPluginsAndMainProgramUnaffected() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        PluginJarTestSupport.writeCorruptJar(pluginsDir.resolve("a-corrupt.jar"));
        buildHealthyServicePlugin(pluginsDir.resolve("b-healthy.jar"), "healthyPlugin");

        newRunner(pluginsDir).run(context -> {
            assertThat(context).hasNotFailed();
            PluginRegistry registry = context.getBean(PluginRegistry.class);
            List<PluginInfo> plugins = registry.getPlugins();
            assertThat(plugins).hasSize(2);

            PluginInfo corrupted = findByFileName(plugins, "a-corrupt.jar");
            assertThat(corrupted.getStatus()).isEqualTo(PluginStatus.FAILED);
            assertThat(corrupted.getFailureReason()).isNotBlank();

            PluginInfo healthy = findByFileName(plugins, "b-healthy.jar");
            assertThat(healthy.getStatus()).isEqualTo(PluginStatus.REGISTERED);
            assertThat(healthy.getRegisteredBeanNames()).containsExactly("plugin.healthyPlugin.HealthyDemoService");
            assertThat(context.containsBean("plugin.healthyPlugin.HealthyDemoService")).isTrue();
        });
    }

    /**
     * 插件 Bean 定义成功注册后，若其在实例化阶段（构造器）抛出异常，主程序本次启动应整体
     * 失败——这是既定行为而非需要隔离恢复的缺陷（plugin-jar-management capability spec
     * "插件 Bean 实例化阶段异常"，tasks.md 3.3）。
     */
    @Test
    void pluginBeanInstantiationFailure_shouldFailWholeContextStartup() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        String className = "com.example.pluginfixture.instantiationfailure.ExplodingService";
        Properties properties = new Properties();
        properties.setProperty("name", "explodingPlugin");
        PluginJarTestSupport.buildJar(pluginsDir.resolve("exploding.jar"),
                java.util.Map.of(className, "package com.example.pluginfixture.instantiationfailure;"
                        + "import org.springframework.stereotype.Service;"
                        + "@Service public class ExplodingService {"
                        + "public ExplodingService() { throw new IllegalStateException(\"boom-from-plugin\"); } }"),
                properties, Service.class);

        newRunner(pluginsDir).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause().hasMessageContaining("boom-from-plugin");
        });
    }

    /**
     * 插件覆盖目标解析成功后，不论构造器注入、字段注入还是运行期 {@code getBean} 查找，
     * 拿到的都应是插件实现（plugin-bean-override capability spec "覆盖后的方法调用生效"，
     * tasks.md 4.2/4.3）。
     */
    @Test
    void pluginOverride_shouldApplyToConstructorInjection_fieldInjection_andGetBeanLookup() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        buildOverridePlugin(pluginsDir.resolve("override-plugin.jar"), "overridePlugin", "plugin-override");

        newRunner(pluginsDir).withBean(FakeMainServiceImpl.class).withBean(FakeMainConsumer.class).run(context -> {
            assertThat(context).hasNotFailed();

            FakeMainService service = context.getBean(FakeMainService.class);
            assertThat(service.identify()).isEqualTo("plugin-override");

            FakeMainConsumer consumer = context.getBean(FakeMainConsumer.class);
            assertThat(consumer.delegateByConstructor()).isEqualTo("plugin-override");
            assertThat(consumer.delegateByField()).isEqualTo("plugin-override");

            PluginRegistry registry = context.getBean(PluginRegistry.class);
            PluginInfo pluginInfo = registry.getPlugins().get(0);
            assertThat(pluginInfo.getOverrides()).hasSize(1);
            assertThat(pluginInfo.getOverrides().get(0).targetClassName()).isEqualTo(FakeMainService.class.getName());
        });
    }

    /**
     * 覆盖目标命中覆盖黑名单时应拒绝生效，主程序原有实现保持生效
     * （plugin-bean-override capability spec "覆盖范围限制"，tasks.md 4.6）。
     */
    @Test
    void pluginOverride_shouldBeRejected_whenTargetInDenyList() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        String className = "com.example.pluginfixture.denylist.DeniedOverrideService";
        Properties properties = new Properties();
        properties.setProperty("name", "deniedPlugin");
        PluginJarTestSupport.buildJar(pluginsDir.resolve("denied-override.jar"),
                java.util.Map.of(className, "package com.example.pluginfixture.denylist;"
                        + "import cn.nihility.rbac.plugin.annotation.PluginOverride;"
                        + "import cn.nihility.rbac.plugin.support.fixture.FakeDeniedService;"
                        + "import org.springframework.stereotype.Service;"
                        + "@Service @PluginOverride(target = FakeDeniedService.class)"
                        + "public class DeniedOverrideService implements FakeDeniedService {"
                        + "@Override public String identify() { return \"plugin-denied\"; } }"),
                properties, Service.class, PluginOverride.class, FakeDeniedService.class);

        newRunner(pluginsDir)
                .withPropertyValues("rbac.plugin.override.deny-list[0]=" + FakeDeniedService.class.getName())
                .withBean(FakeDeniedServiceImpl.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FakeDeniedService service = context.getBean(FakeDeniedService.class);
                    assertThat(service.identify()).isEqualTo("main-denied");

                    PluginRegistry registry = context.getBean(PluginRegistry.class);
                    PluginInfo pluginInfo = registry.getPlugins().get(0);
                    assertThat(pluginInfo.getStatus()).isEqualTo(PluginStatus.REGISTERED);
                    assertThat(pluginInfo.getSkippedClasses()).hasSize(1);
                    assertThat(pluginInfo.getSkippedClasses().get(0).reason()).contains("覆盖黑名单");
                });
    }

    /**
     * 两个插件覆盖同一目标类时，按处理顺序"后注册覆盖先注册"，处理顺序决定生效顺序，且应
     * 记录冲突（plugin-bean-override capability spec "覆盖冲突处理"，tasks.md 4.7）。
     */
    @Test
    void twoPluginsOverridingSameTarget_shouldRecordConflict_andLastProcessedWins() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        // 文件名 a- 排在 b- 之前，同优先级（缺省 0）按文件名字典序处理，因此插件 b 后处理、后生效。
        buildOverridePlugin(pluginsDir.resolve("a-override.jar"), "pluginA", "plugin-a");
        buildOverridePlugin(pluginsDir.resolve("b-override.jar"), "pluginB", "plugin-b");

        newRunner(pluginsDir).withBean(FakeMainServiceImpl.class).run(context -> {
            assertThat(context).hasNotFailed();

            FakeMainService service = context.getBean(FakeMainService.class);
            assertThat(service.identify()).isEqualTo("plugin-b");

            PluginRegistry registry = context.getBean(PluginRegistry.class);
            List<PluginOverrideConflict> conflicts = registry.getOverrideConflicts();
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).targetClassName()).isEqualTo(FakeMainService.class.getName());
            assertThat(conflicts.get(0).previousPluginName()).isEqualTo("pluginA");
            assertThat(conflicts.get(0).winningPluginName()).isEqualTo("pluginB");
        });
    }

    /**
     * 构造基础 {@link ApplicationContextRunner}：开启 Bean 定义覆盖、注册
     * {@link PluginBeanDefinitionRegistrar}、指向指定插件目录。
     *
     * @param pluginsDir 插件目录
     * @return 已完成基础配置的 runner
     */
    private ApplicationContextRunner newRunner(Path pluginsDir) {
        return new ApplicationContextRunner().withInitializer(ALLOW_OVERRIDING).withBean(PluginBeanDefinitionRegistrar.class)
                .withPropertyValues("rbac.plugin.directory=" + pluginsDir);
    }

    /**
     * 构建一个只含单个 {@code @Service} 类的健康插件 jar（类名固定为 {@code HealthyDemoService}）。
     *
     * @param jarPath    目标 jar 路径
     * @param pluginName 插件名称（写入 {@code plugin.properties}）
     */
    private void buildHealthyServicePlugin(Path jarPath, String pluginName) throws Exception {
        String className = "com.example.pluginfixture.healthy.HealthyDemoService";
        Properties properties = new Properties();
        properties.setProperty("name", pluginName);
        PluginJarTestSupport.buildJar(jarPath,
                java.util.Map.of(className, "package com.example.pluginfixture.healthy;"
                        + "import org.springframework.stereotype.Service;"
                        + "@Service public class HealthyDemoService { public String hi() { return \"healthy\"; } }"),
                properties, Service.class);
    }

    /**
     * 构建一个声明 {@code @PluginOverride(target = FakeMainService.class)} 的插件 jar。
     *
     * @param jarPath    目标 jar 路径
     * @param pluginName 插件名称（写入 {@code plugin.properties}）
     * @param identity   覆盖实现返回的标识文案，用于区分不同插件
     */
    private void buildOverridePlugin(Path jarPath, String pluginName, String identity) throws Exception {
        String className = "com.example.pluginfixture.override." + pluginName + ".OverrideService";
        Properties properties = new Properties();
        properties.setProperty("name", pluginName);
        PluginJarTestSupport.buildJar(jarPath,
                java.util.Map.of(className, "package com.example.pluginfixture.override." + pluginName + ";"
                        + "import cn.nihility.rbac.plugin.annotation.PluginOverride;"
                        + "import cn.nihility.rbac.plugin.support.fixture.FakeMainService;"
                        + "import org.springframework.stereotype.Service;"
                        + "@Service @PluginOverride(target = FakeMainService.class)"
                        + "public class OverrideService implements FakeMainService {"
                        + "@Override public String identify() { return \"" + identity + "\"; } }"),
                properties, Service.class, PluginOverride.class, FakeMainService.class);
    }

    /**
     * 按来源文件名从插件状态列表中查找记录。
     *
     * @param plugins  插件状态列表
     * @param fileName 来源文件名
     * @return 匹配的记录
     */
    private PluginInfo findByFileName(List<PluginInfo> plugins, String fileName) {
        return plugins.stream().filter(info -> info.getFileName().equals(fileName)).findFirst()
                .orElseThrow(() -> new AssertionError("未找到来源文件名为 [" + fileName + "] 的插件记录"));
    }
}
