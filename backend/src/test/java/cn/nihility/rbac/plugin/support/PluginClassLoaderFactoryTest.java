package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.plugin.testsupport.PluginJarTestSupport;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PluginClassLoaderFactory} 单元测试：验证插件私有类可被加载、主程序公共类不重复加载
 * （parent-first 委派，plugin-jar-upgrade change tasks.md 2.2）。
 */
class PluginClassLoaderFactoryTest {

    /** JUnit 管理的临时目录。 */
    @TempDir
    private Path tempDir;

    /**
     * 插件专属类加载器应能加载仅存在于该插件 jar 内、不在主程序类加载器可见范围内的类；
     * 同时对主程序类加载器已能加载的公共类（如 JDK 内置类）应沿 parent-first 委派复用同一个
     * {@link Class} 对象，不重复定义。
     */
    @Test
    void create_shouldLoadPluginPrivateClass_andReuseParentClassForSharedType() throws Exception {
        String className = "com.example.pluginfixture.classloaderfactorytest.MarkerXyz123";
        Map<String, String> sources = Map.of(className, "package com.example.pluginfixture.classloaderfactorytest;"
                + "public class MarkerXyz123 { public static String hello() { return \"plugin-private\"; } }");
        Path jarPath = PluginJarTestSupport.buildJar(tempDir.resolve("classloader-fixture.jar"), sources, null);

        ClassLoader appClassLoader = PluginClassLoaderFactoryTest.class.getClassLoader();

        // 主程序类加载器本身无法加载该插件私有类，证明它确实"只存在于插件 jar 内"。
        assertThatThrownBy(() -> Class.forName(className, false, appClassLoader)).isInstanceOf(ClassNotFoundException.class);

        try (URLClassLoader pluginClassLoader = PluginClassLoaderFactory.create(jarPath, "classloaderFactoryTest", appClassLoader)) {
            // 插件私有类可通过插件专属类加载器正确加载。
            Class<?> loaded = pluginClassLoader.loadClass(className);
            assertThat(loaded.getMethod("hello").invoke(null)).isEqualTo("plugin-private");

            // 主程序公共类（parent 可加载）沿 parent-first 委派复用同一个 Class 对象，不重复定义。
            Class<?> viaPlugin = pluginClassLoader.loadClass("java.util.ArrayList");
            assertThat(viaPlugin).isSameAs(java.util.ArrayList.class);
        }
    }
}
