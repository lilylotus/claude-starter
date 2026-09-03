package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批拒绝（驳回）请求体。
 */
@Getter
@Setter
@Schema(description = "审批拒绝请求")
public class RejectRequest {

    /** 拒绝原因，必填。 */
    @NotBlank(message = "拒绝原因不能为空")
    @Size(max = 500, message = "拒绝原因长度不能超过 500 个字符")
    @Schema(description = "拒绝原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String remark;
}
