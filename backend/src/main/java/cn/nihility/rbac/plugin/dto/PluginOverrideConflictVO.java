package cn.nihility.rbac.plugin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 多插件覆盖同一目标类的冲突记录视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "插件覆盖冲突记录")
public class PluginOverrideConflictVO {

    /** 被覆盖的目标类全限定名。 */
    @Schema(description = "被覆盖的目标类全限定名")
    private String targetClassName;

    /** 此前生效的插件名称（被取代者）。 */
    @Schema(description = "此前生效的插件名称")
    private String previousPluginName;

    /** 最终生效的插件名称（后处理者）。 */
    @Schema(description = "最终生效的插件名称")
    private String winningPluginName;

    /** 覆盖使用的 bean name。 */
    @Schema(description = "覆盖使用的 bean name")
    private String beanName;
}
