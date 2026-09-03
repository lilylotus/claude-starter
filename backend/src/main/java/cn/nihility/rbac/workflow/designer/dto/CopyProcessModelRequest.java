package cn.nihility.rbac.workflow.designer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 复制流程模型的目标编码和名称。 */
@Getter
@Setter
public class CopyProcessModelRequest {

    /** 新模型流程编码。 */
    @NotBlank(message = "新流程编码不能为空")
    @Size(max = 64, message = "新流程编码长度不能超过64个字符")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "新流程编码必须为大写字母开头的大写字母、数字或下划线")
    private String processCode;

    /** 新模型流程名称。 */
    @NotBlank(message = "新流程名称不能为空")
    @Size(max = 128, message = "新流程名称长度不能超过128个字符")
    private String processName;
}
