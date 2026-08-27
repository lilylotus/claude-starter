package cn.nihility.rbac.plugin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 单个插件（Bean 定义注册阶段）状态视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "插件状态")
public class PluginVO {

    /** 插件名称。 */
    @Schema(description = "插件名称")
    private String name;

    /** 来源文件名（含 .jar 后缀）。 */
    @Schema(description = "来源文件名")
    private String fileName;

    /** 插件版本号。 */
    @Schema(description = "插件版本号")
    private String version;

    /** 处理优先级，数值越大越晚处理。 */
    @Schema(description = "处理优先级，数值越大越晚处理")
    private Integer priority;

    /** Bean 定义注册阶段状态：REGISTERED / FAILED。 */
    @Schema(description = "Bean 定义注册阶段状态：REGISTERED=已注册，FAILED=失败")
    private String status;

    /** 整体失败原因，仅 status 为 FAILED 时非空。 */
    @Schema(description = "整体失败原因，仅 status 为 FAILED 时非空")
    private String failureReason;

    /** 该插件内成功生效的覆盖记录。 */
    @Schema(description = "成功生效的覆盖记录")
    private List<PluginBeanOverrideVO> overrides;

    /** 该插件内未能注册 Bean 定义的候选类记录。 */
    @Schema(description = "未能注册 Bean 定义的候选类记录")
    private List<PluginSkippedClassVO> skippedClasses;

    /** 该插件成功注册的非覆盖 Bean 名称列表。 */
    @Schema(description = "成功注册的非覆盖 Bean 名称列表")
    private List<String> registeredBeanNames;
}
