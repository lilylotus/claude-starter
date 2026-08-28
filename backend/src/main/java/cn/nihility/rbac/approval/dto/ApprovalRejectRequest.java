package cn.nihility.rbac.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批拒绝请求。
 */
@Getter
@Setter
@Schema(description = "审批拒绝请求")
public class ApprovalRejectRequest {

    /** 拒绝意见。 */
    @NotBlank(message = "拒绝意见不能为空")
    @Size(max = 500, message = "拒绝意见长度不能超过 500 个字符")
    private String opinion;
}
