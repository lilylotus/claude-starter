package cn.nihility.rbac.plugin.support;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

/**
 * 插件内组件扫描（plugin-jar-management capability spec "插件 jar 动态加载"）：识别插件 jar
 * 内标注 {@code @Controller}/{@code @RestController}/{@code @Service}/{@code @Component}/
 * {@code @Configuration} 的类。
 * <p>
 * design.md 原描述使用 {@code ClassPathScanningCandidateComponentProvider} 做扫描，但该类
 * 基于 {@code classpath*:} 模式解析资源，通过插件专属 {@link java.net.URLClassLoader}
 * （parent-first，parent 为主程序类加载器）取得的 {@code ClassLoader#getResources(String)}
 * 会沿委派链一路向上聚合，实际会把主程序自身及其全部依赖 jar 中的类也一并扫描进来，
 * 而不是只扫描该插件 jar 自身——这与"只识别插件 jar 内的类"的既定行为不符。因此这里改为
 * 直接枚举该插件 jar 自身的 {@link JarEntry} 取得类名清单，再用 Spring
 * {@link AnnotatedElementUtils#hasAnnotation(java.lang.reflect.AnnotatedElement, Class)}
 * （meta-annotation 感知，等价于 {@code useDefaultFilters=true} 时 {@code @Component}
 * 过滤器的判定逻辑，天然覆盖 {@code @Controller}/{@code @RestController}/{@code @Service}/
 * {@code @Configuration} 等元注解了 {@code @Component} 的注解）逐个判定，只依赖该插件
 * 自身类加载器加载的类对象，不产生跨 jar 的扫描污染，属于对 design.md 精神（"扫描插件内
 * 组件"）的工程实现调整，未改变决策 1-6 的核心架构。
 */
@Slf4j
public final class PluginComponentScanner {

    /** {@code .class} 文件后缀。 */
    private static final String CLASS_SUFFIX = ".class";

    /** {@code module-info} 描述符条目名，跳过。 */
    private static final String MODULE_INFO = "module-info.class";

    /**
     * 工具类不允许实例化。
     */
    private PluginComponentScanner() {
    }

    /**
     * 扫描插件 jar 内标注 Spring 组件注解的候选类。
     *
     * @param jarPath           插件 jar 路径
     * @param pluginClassLoader 该插件专属类加载器，用于实际加载候选类
     * @return 候选类列表（已通过组件注解判定），保持 jar 内条目的原始顺序
     * @throws IOException 插件 jar 无法正确解析（如非法 jar 格式）时抛出，由调用方按"插件
     *                      Bean 定义注册阶段失败"整体隔离处理
     */
    public static List<Class<?>> scan(Path jarPath, ClassLoader pluginClassLoader) throws IOException {
        List<Class<?>> candidates = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String className = toClassName(entry);
                if (className == null) {
                    continue;
                }
                Class<?> candidate = loadCandidate(className, pluginClassLoader, jarPath);
                if (candidate != null && isComponentCandidate(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    /**
     * 将 jar 条目名转换为类全限定名，非 {@code .class} 条目、目录、{@code module-info}
     * 返回 {@code null} 表示跳过。
     *
     * @param entry jar 条目
     * @return 类全限定名，不适用时返回 {@code null}
     */
    private static String toClassName(JarEntry entry) {
        String name = entry.getName();
        if (entry.isDirectory() || !name.endsWith(CLASS_SUFFIX) || name.equals(MODULE_INFO)) {
            return null;
        }
        return name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.');
    }

    /**
     * 加载候选类，加载失败（如引用了插件自身未携带的私有依赖）只记录警告并跳过该类，
     * 不影响同一插件内其他类的扫描（区别于 jar 本身无法解析的基础设施级失败）。
     *
     * @param className         类全限定名
     * @param pluginClassLoader 插件专属类加载器
     * @param jarPath           插件 jar 路径，用于日志定位
     * @return 加载成功的类对象，失败时返回 {@code null}
     */
    private static Class<?> loadCandidate(String className, ClassLoader pluginClassLoader, Path jarPath) {
        try {
            return Class.forName(className, false, pluginClassLoader);
        } catch (Throwable ex) {
            log.warn("插件 [{}] 中的类 [{}] 加载失败，跳过该类：{}", jarPath.getFileName(), className, ex.toString());
            return null;
        }
    }

    /**
     * 判定类是否为 Spring 组件候选：非接口/注解/枚举/抽象类/匿名类/本地类/合成类，且标注了
     * {@code @Component} 或其元注解（{@code @Controller}/{@code @RestController}/
     * {@code @Service}/{@code @Configuration} 等）。
     *
     * @param clazz 候选类
     * @return 是否为组件候选
     */
    private static boolean isComponentCandidate(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        if (clazz.isInterface() || clazz.isAnnotation() || clazz.isEnum() || Modifier.isAbstract(modifiers)
                || clazz.isAnonymousClass() || clazz.isLocalClass() || clazz.isSynthetic()) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(clazz, Component.class);
    }
}
