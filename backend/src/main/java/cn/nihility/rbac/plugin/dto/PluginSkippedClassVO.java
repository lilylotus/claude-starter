package cn.nihility.rbac.plugin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 插件内未能注册 Bean 定义的候选类视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "插件内未能注册的候选类")
public class PluginSkippedClassVO {

    /** 候选类全限定名。 */
    @Schema(description = "候选类全限定名")
    private String className;

    /** 未能注册的原因。 */
    @Schema(description = "未能注册的原因")
    private String reason;
}
