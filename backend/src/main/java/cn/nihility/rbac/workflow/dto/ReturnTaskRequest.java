package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 退回历史节点请求体。
 */
@Getter
@Setter
@Schema(description = "退回请求")
public class ReturnTaskRequest {

    /** 退回目标节点 id，必填。 */
    @NotBlank(message = "退回目标节点不能为空")
    @Schema(description = "退回目标节点 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetNodeId;

    /** 退回原因，可为空。 */
    @Size(max = 500, message = "退回原因长度不能超过 500 个字符")
    @Schema(description = "退回原因")
    private String remark;
}
