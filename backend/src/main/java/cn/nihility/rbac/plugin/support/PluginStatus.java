package cn.nihility.rbac.plugin.support;

/**
 * 插件 <b>Bean 定义注册阶段</b>状态（plugin-jar-management capability spec "插件状态查询"）。
 * 仅反映定义注册阶段的结果，不覆盖后续 Bean 实例化阶段——若某个插件的 Bean 在实例化阶段
 * 抛出异常，会导致主程序本次启动整体失败，不存在可查询的中间状态。
 */
public enum PluginStatus {

    /**
     * 该插件的类加载器构建、组件扫描均成功，已进入按类逐个注册 Bean 定义的流程。注意：
     * 该状态不保证插件内每一个候选类都成功注册——个别类可能因覆盖目标解析不到、命中
     * 覆盖黑名单、请求路径冲突等被单独跳过（见 {@link PluginInfo#getSkippedClasses()}），
     * 这属于"该插件内其他不冲突的类不受影响"的既定行为，插件整体仍视为 REGISTERED。
     */
    REGISTERED,

    /**
     * 该插件在"构建类加载器"到"扫描组件"的基础设施步骤中失败（如 jar 损坏、无法解析），
     * 未注册该插件任何 Bean 定义。
     */
    FAILED
}
