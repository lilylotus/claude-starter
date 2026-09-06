package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 运维强制终止流程实例请求体（production-approval-lifecycle change design.md 第7节
 * "terminate：独立运维权限和必填原因"，tasks.md 6.8）。
 */
@Getter
@Setter
@Schema(description = "运维终止流程实例请求")
public class TerminateRequest {

    /** 终止原因，必填。 */
    @NotBlank(message = "终止原因不能为空")
    @Size(max = 500, message = "终止原因长度不能超过 500 个字符")
    @Schema(description = "终止原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
