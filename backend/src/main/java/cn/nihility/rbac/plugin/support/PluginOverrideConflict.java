package cn.nihility.rbac.plugin.support;

/**
 * 多个插件覆盖同一目标类时的冲突记录（plugin-bean-override capability spec "覆盖冲突处理"，
 * design.md Decision 4：按处理顺序"后注册覆盖先注册"，处理顺序即生效顺序）。
 *
 * @param targetClassName    被覆盖的目标类全限定名
 * @param previousPluginName 此前生效的插件名称（被取代者）
 * @param winningPluginName  最终生效的插件名称（后处理者）
 * @param beanName           覆盖使用的 bean name
 */
public record PluginOverrideConflict(String targetClassName, String previousPluginName, String winningPluginName,
        String beanName) {
}
