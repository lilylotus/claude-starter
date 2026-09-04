package cn.nihility.rbac.workflow.designer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 设置流程模型是否接受新发起的请求体（production-approval-lifecycle change tasks.md
 * 4.6"模型级启停"）。
 */
@Getter
@Setter
public class SetModelEnabledRequest {

    /** 目标启用状态：{@code true} 接受新发起，{@code false} 停止接受新发起。 */
    @Schema(description = "是否接受新发起", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
