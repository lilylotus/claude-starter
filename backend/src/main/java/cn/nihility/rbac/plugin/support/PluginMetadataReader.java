package cn.nihility.rbac.plugin.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析插件 jar 内 {@code META-INF/plugin.properties} 元信息（plugin-jar-upgrade change
 * design.md Decision 6）：{@code name}/{@code version}/{@code priority} 均可选，
 * properties 文件本身也可整体缺失，缺省时用来源文件名等信息兜底，不影响插件正常加载。
 */
@Slf4j
public final class PluginMetadataReader {

    /** 元信息文件在 jar 内的固定路径。 */
    private static final String METADATA_ENTRY = "META-INF/plugin.properties";

    /** 名称属性键。 */
    private static final String KEY_NAME = "name";

    /** 版本属性键。 */
    private static final String KEY_VERSION = "version";

    /** 优先级属性键。 */
    private static final String KEY_PRIORITY = "priority";

    /**
     * 工具类不允许实例化。
     */
    private PluginMetadataReader() {
    }

    /**
     * 从插件 jar 中读取元信息，{@code META-INF/plugin.properties} 缺失或其中某个属性缺失/
     * 格式非法时，用来源文件名（去掉 {@code .jar} 后缀）兜底名称、{@link
     * PluginMetadata#UNKNOWN_VERSION} 兜底版本、{@link PluginMetadata#DEFAULT_PRIORITY}
     * 兜底优先级，不抛出异常（元信息缺失不属于插件加载失败）。
     *
     * @param jarFile 已打开的插件 jar 文件
     * @param jarPath 插件 jar 路径，用于推导兜底名称
     * @return 插件元信息，不会为 {@code null}
     */
    public static PluginMetadata read(JarFile jarFile, Path jarPath) {
        String fallbackName = fallbackName(jarPath);
        JarEntry entry = jarFile.getJarEntry(METADATA_ENTRY);
        if (entry == null) {
            return new PluginMetadata(fallbackName, PluginMetadata.UNKNOWN_VERSION, PluginMetadata.DEFAULT_PRIORITY);
        }
        Properties properties = new Properties();
        try (InputStream in = jarFile.getInputStream(entry)) {
            properties.load(in);
        } catch (IOException ex) {
            log.warn("插件 [{}] 的 {} 读取失败，使用缺省元信息：{}", jarPath.getFileName(), METADATA_ENTRY, ex.getMessage());
            return new PluginMetadata(fallbackName, PluginMetadata.UNKNOWN_VERSION, PluginMetadata.DEFAULT_PRIORITY);
        }
        String name = properties.getProperty(KEY_NAME);
        if (name == null || name.isBlank()) {
            name = fallbackName;
        }
        String version = properties.getProperty(KEY_VERSION);
        if (version == null || version.isBlank()) {
            version = PluginMetadata.UNKNOWN_VERSION;
        }
        int priority = parsePriority(properties.getProperty(KEY_PRIORITY), jarPath);
        return new PluginMetadata(name, version, priority);
    }

    /**
     * 解析优先级属性，非法格式（非数字）时记录警告并回退为缺省优先级。
     *
     * @param rawValue 原始属性值，可能为 {@code null}
     * @param jarPath  插件 jar 路径，用于日志定位
     * @return 解析后的优先级
     */
    private static int parsePriority(String rawValue, Path jarPath) {
        if (rawValue == null || rawValue.isBlank()) {
            return PluginMetadata.DEFAULT_PRIORITY;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            log.warn("插件 [{}] 的 priority 属性值 [{}] 不是合法整数，使用缺省优先级 {}", jarPath.getFileName(), rawValue,
                    PluginMetadata.DEFAULT_PRIORITY);
            return PluginMetadata.DEFAULT_PRIORITY;
        }
    }

    /**
     * 用来源文件名（去掉 {@code .jar} 后缀）推导兜底插件名称。
     *
     * @param jarPath 插件 jar 路径
     * @return 兜底名称
     */
    private static String fallbackName(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        return fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }
}
