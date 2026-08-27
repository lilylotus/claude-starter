package cn.nihility.rbac.plugin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 插件列表查询响应视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "插件列表查询响应")
public class PluginListVO {

    /** 已发现插件的状态记录列表，按处理顺序排列。 */
    @Schema(description = "插件状态记录列表，按处理顺序排列")
    private List<PluginVO> plugins;

    /** 多插件覆盖同一目标类的冲突记录列表。 */
    @Schema(description = "覆盖冲突记录列表")
    private List<PluginOverrideConflictVO> overrideConflicts;
}
