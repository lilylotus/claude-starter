package cn.nihility.rbac.plugin.support;

/**
 * 一次成功生效的插件覆盖记录（plugin-bean-override capability spec "覆盖生效范围与优先级"）。
 *
 * @param pluginClassName 插件中声明 {@code @PluginOverride} 的类全限定名
 * @param targetClassName 覆盖目标类全限定名（{@code @PluginOverride#target()}）
 * @param beanName        最终注册使用的 bean name（与主程序原有 bean name 相同）
 */
public record PluginBeanOverride(String pluginClassName, String targetClassName, String beanName) {
}
