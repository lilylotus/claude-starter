package cn.nihility.rbac.plugin.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * 单个插件的（Bean 定义注册阶段）运行时状态，由 {@code PluginBeanDefinitionRegistrar} 在
 * 处理该插件的过程中填充，之后只读，供管理接口查询（plugin-jar-management capability spec
 * "插件状态查询"）。
 */
@Getter
public class PluginInfo {

    /** 插件名称（来自 {@code plugin.properties} 的 {@code name}，缺省用来源文件名兜底）。 */
    private final String name;

    /** 来源文件名（含 {@code .jar} 后缀）。 */
    private final String fileName;

    /** 插件版本号，缺省为 {@link PluginMetadata#UNKNOWN_VERSION}。 */
    private final String version;

    /** 处理优先级，数值越大越晚处理。 */
    private final int priority;

    /** Bean 定义注册阶段状态。 */
    private PluginStatus status = PluginStatus.REGISTERED;

    /** 整体失败原因，仅 {@link #status} 为 {@link PluginStatus#FAILED} 时非空。 */
    private String failureReason;

    /** 该插件内成功生效的覆盖记录。 */
    private final List<PluginBeanOverride> overrides = new ArrayList<>();

    /** 该插件内未能注册 Bean 定义的候选类记录。 */
    private final List<PluginSkippedClass> skippedClasses = new ArrayList<>();

    /** 该插件成功注册的非覆盖 Bean 名称列表，供排查/展示使用。 */
    private final List<String> registeredBeanNames = new ArrayList<>();

    /**
     * 构造插件状态记录，初始状态为 {@link PluginStatus#REGISTERED}（乐观初始值，基础设施步骤
     * 失败时由 {@link #markFailed(String)} 改写）。
     *
     * @param name     插件名称
     * @param fileName 来源文件名
     * @param version  插件版本号
     * @param priority 处理优先级
     */
    public PluginInfo(String name, String fileName, String version, int priority) {
        this.name = name;
        this.fileName = fileName;
        this.version = version;
        this.priority = priority;
    }

    /**
     * 标记该插件因基础设施步骤（类加载器构建、组件扫描等）失败，整体不注册任何 Bean 定义。
     *
     * @param reason 失败原因
     */
    public void markFailed(String reason) {
        this.status = PluginStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * 记录一次成功生效的覆盖。
     *
     * @param override 覆盖记录
     */
    public void addOverride(PluginBeanOverride override) {
        this.overrides.add(override);
    }

    /**
     * 记录一个未能注册 Bean 定义的候选类。
     *
     * @param skipped 跳过记录
     */
    public void addSkippedClass(PluginSkippedClass skipped) {
        this.skippedClasses.add(skipped);
    }

    /**
     * 记录一个成功注册的非覆盖 Bean 名称。
     *
     * @param beanName bean name
     */
    public void addRegisteredBeanName(String beanName) {
        this.registeredBeanNames.add(beanName);
    }

    /**
     * 返回覆盖记录的只读视图。
     *
     * @return 覆盖记录列表
     */
    public List<PluginBeanOverride> getOverrides() {
        return Collections.unmodifiableList(overrides);
    }

    /**
     * 返回跳过记录的只读视图。
     *
     * @return 跳过记录列表
     */
    public List<PluginSkippedClass> getSkippedClasses() {
        return Collections.unmodifiableList(skippedClasses);
    }

    /**
     * 返回成功注册的非覆盖 Bean 名称的只读视图。
     *
     * @return bean name 列表
     */
    public List<String> getRegisteredBeanNames() {
        return Collections.unmodifiableList(registeredBeanNames);
    }
}
