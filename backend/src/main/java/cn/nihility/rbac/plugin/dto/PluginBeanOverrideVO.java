package cn.nihility.rbac.plugin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 插件覆盖记录视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "插件覆盖记录")
public class PluginBeanOverrideVO {

    /** 插件中声明 {@code @PluginOverride} 的类全限定名。 */
    @Schema(description = "插件覆盖类全限定名")
    private String pluginClassName;

    /** 覆盖目标类全限定名。 */
    @Schema(description = "覆盖目标类全限定名")
    private String targetClassName;

    /** 最终注册使用的 bean name（与主程序原有 bean name 相同）。 */
    @Schema(description = "覆盖生效的 bean name")
    private String beanName;
}
