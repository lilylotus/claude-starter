package cn.nihility.rbac.plugin.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存态插件状态登记表（plugin-jar-upgrade change design.md Decision 6：v1 不做持久化，
 * 重启后以磁盘 jar 重新计算）。由 {@code PluginBeanDefinitionRegistrar} 在
 * {@code invokeBeanFactoryPostProcessors} 阶段写入，之后仅供管理接口只读查询，
 * 写入发生在应用启动这一单线程阶段，读取可能与后续 HTTP 请求线程并发，因此内部集合选用
 * {@link CopyOnWriteArrayList}。
 */
public class PluginRegistry {

    /** 已发现插件的状态记录。 */
    private final List<PluginInfo> plugins = new CopyOnWriteArrayList<>();

    /** 多插件覆盖同一目标类时的冲突记录。 */
    private final List<PluginOverrideConflict> overrideConflicts = new CopyOnWriteArrayList<>();

    /**
     * 登记一个插件的状态记录。
     *
     * @param pluginInfo 插件状态记录
     */
    public void register(PluginInfo pluginInfo) {
        plugins.add(pluginInfo);
    }

    /**
     * 登记一次覆盖冲突。
     *
     * @param conflict 冲突记录
     */
    public void recordConflict(PluginOverrideConflict conflict) {
        overrideConflicts.add(conflict);
    }

    /**
     * 查询全部插件状态记录，按登记（即处理）顺序返回。
     *
     * @return 插件状态记录列表（只读视图）
     */
    public List<PluginInfo> getPlugins() {
        return List.copyOf(plugins);
    }

    /**
     * 查询全部覆盖冲突记录。
     *
     * @return 覆盖冲突记录列表（只读视图）
     */
    public List<PluginOverrideConflict> getOverrideConflicts() {
        return List.copyOf(overrideConflicts);
    }
}
