package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 反对（阈值制会签节点专用反对票）请求体。
 */
@Getter
@Setter
@Schema(description = "审批反对（阈值制会签节点专用反对票）请求")
public class DisagreeRequest {

    /** 处理意见，可为空。 */
    @Size(max = 500, message = "处理意见长度不能超过 500 个字符")
    @Schema(description = "处理意见")
    private String remark;
}
