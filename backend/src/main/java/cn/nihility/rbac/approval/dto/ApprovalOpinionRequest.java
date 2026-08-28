package cn.nihility.rbac.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批通过请求，可携带审批意见。
 */
@Getter
@Setter
@Schema(description = "审批意见请求")
public class ApprovalOpinionRequest {

    /** 审批意见。 */
    @Size(max = 500, message = "审批意见长度不能超过 500 个字符")
    private String opinion;
}
