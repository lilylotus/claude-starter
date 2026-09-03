package cn.nihility.rbac.workflow.designer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 创建流程模型请求。 */
@Getter
@Setter
public class CreateProcessModelRequest {

    /** 流程业务编码。 */
    @Schema(description = "流程编码", example = "USER_CHANGE")
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码长度不能超过64个字符")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "流程编码必须为大写字母开头的大写字母、数字或下划线")
    private String processCode;

    /** 流程名称。 */
    @Schema(description = "流程名称", example = "人员变更审批")
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 128, message = "流程名称长度不能超过128个字符")
    private String processName;
}
