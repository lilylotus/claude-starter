package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.plugin.testsupport.PluginJarTestSupport;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link PluginComponentScanner} 单元测试：用一个示例插件 jar 验证扫描结果只包含标注
 * {@code @Service}/{@code @RestController} 等组件注解的类，排除接口/抽象类/普通 POJO
 * （plugin-jar-upgrade change tasks.md 2.3）。
 */
class PluginComponentScannerTest {

    /** 示例插件 jar 内的包名。 */
    private static final String PKG = "com.example.pluginfixture.componentscantest";

    /** JUnit 管理的临时目录。 */
    @TempDir
    private Path tempDir;

    /**
     * 扫描结果应恰好包含 {@code @Service} 类与 {@code @RestController} 类，
     * 不包含普通类、接口、抽象类。
     */
    @Test
    void scan_shouldReturnOnlyAnnotatedComponentClasses() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(PKG + ".DemoService", "package " + PKG + ";"
                + "import org.springframework.stereotype.Service;"
                + "@Service public class DemoService { public String hi() { return \"service\"; } }");
        sources.put(PKG + ".DemoController", "package " + PKG + ";"
                + "import org.springframework.web.bind.annotation.GetMapping;"
                + "import org.springframework.web.bind.annotation.RestController;"
                + "@RestController public class DemoController {"
                + "@GetMapping(\"/component-scan-test/hi\") public String hi() { return \"controller\"; } }");
        sources.put(PKG + ".PlainPojo", "package " + PKG + "; public class PlainPojo { }");
        sources.put(PKG + ".DemoComponentInterface", "package " + PKG + ";"
                + "import org.springframework.stereotype.Component;"
                + "@Component public interface DemoComponentInterface { }");
        sources.put(PKG + ".AbstractDemoService", "package " + PKG + ";"
                + "import org.springframework.stereotype.Service;"
                + "@Service public abstract class AbstractDemoService { }");

        Path jarPath = PluginJarTestSupport.buildJar(tempDir.resolve("component-scan-fixture.jar"), sources, null,
                Component.class, Service.class, RestController.class, GetMapping.class);

        ClassLoader appClassLoader = PluginComponentScannerTest.class.getClassLoader();
        try (URLClassLoader pluginClassLoader =
                PluginClassLoaderFactory.create(jarPath, "componentScanTest", appClassLoader)) {
            List<Class<?>> candidates = PluginComponentScanner.scan(jarPath, pluginClassLoader);

            assertThat(candidates).extracting(Class::getSimpleName).containsExactlyInAnyOrder("DemoService",
                    "DemoController");
        }
    }
}
