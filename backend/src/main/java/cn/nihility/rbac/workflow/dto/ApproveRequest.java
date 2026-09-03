package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批通过请求体。
 */
@Getter
@Setter
@Schema(description = "审批通过请求")
public class ApproveRequest {

    /** 处理意见，可为空。 */
    @Size(max = 500, message = "处理意见长度不能超过 500 个字符")
    @Schema(description = "处理意见")
    private String remark;
}
