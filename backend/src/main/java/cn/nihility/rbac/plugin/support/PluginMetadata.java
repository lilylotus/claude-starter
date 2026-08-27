package cn.nihility.rbac.plugin.support;

/**
 * 插件元信息，解析自插件 jar 内 {@code META-INF/plugin.properties}（plugin-jar-upgrade
 * change design.md Decision 6）：{@code name}/{@code version}/{@code priority} 均可选，
 * 缺省时由 {@link PluginMetadataReader} 用来源文件名等信息兜底。
 *
 * @param name     插件名称，用于非覆盖 Bean 的命名空间前缀（{@code plugin.<name>.<SimpleClassName>}）
 * @param version  插件版本号，仅用于展示，不参与任何业务判断
 * @param priority 处理优先级，数值越大越晚处理（同优先级按文件名字典序），影响多插件覆盖
 *                 同一目标时的最终生效者（design.md Decision 4）
 */
public record PluginMetadata(String name, String version, int priority) {

    /** 缺省版本号占位文案。 */
    public static final String UNKNOWN_VERSION = "unknown";

    /** 缺省处理优先级。 */
    public static final int DEFAULT_PRIORITY = 0;
}
