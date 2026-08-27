package cn.nihility.rbac.plugin.support;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * 插件专属类加载器构造工具（plugin-jar-upgrade change design.md Decision 1）：为每个插件
 * jar 创建独立的 {@link URLClassLoader}，parent 设为主程序类加载器。{@link URLClassLoader}
 * 默认的 {@link ClassLoader#loadClass(String)} 实现即为 parent-first 委派——先尝试
 * parent（主程序类加载器）加载，找不到时才在自身 URL 范围内查找，因此 Spring 框架等主程序
 * 已提供的公共类天然只会被加载一次、不会被插件重复定义，插件私有类型（主程序类加载器
 * 加载不到的类）则会正确地在插件自身 jar 范围内被解析。
 */
public final class PluginClassLoaderFactory {

    /**
     * 工具类不允许实例化。
     */
    private PluginClassLoaderFactory() {
    }

    /**
     * 为指定插件 jar 构造专属类加载器。
     *
     * @param jarPath     插件 jar 路径
     * @param pluginName  插件名称，仅用于类加载器命名，便于诊断堆栈定位到具体插件
     * @param parentLoader 主程序类加载器
     * @return 插件专属类加载器
     * @throws MalformedURLException 插件 jar 路径无法转换为合法 URL 时抛出
     */
    public static URLClassLoader create(Path jarPath, String pluginName, ClassLoader parentLoader)
            throws MalformedURLException {
        URL jarUrl = jarPath.toUri().toURL();
        return new URLClassLoader("plugin-" + pluginName, new URL[] {jarUrl}, parentLoader);
    }
}
