package cn.nihility.rbac.plugin.support;

/**
 * 插件内某个候选类未能注册 Bean 定义的记录（该插件内其他不冲突的类不受影响，插件整体仍可
 * 处于 {@link PluginStatus#REGISTERED}，见 plugin-jar-management capability spec "插件
 * Bean 定义注册阶段的失败隔离"）。
 *
 * @param className 候选类全限定名
 * @param reason    未能注册的原因（覆盖目标解析不到、命中覆盖黑名单、请求路径冲突、bean name
 *                  冲突等）
 */
public record PluginSkippedClass(String className, String reason) {
}
