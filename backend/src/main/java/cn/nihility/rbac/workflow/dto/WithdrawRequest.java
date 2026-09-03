package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 撤回请求体。
 */
@Getter
@Setter
@Schema(description = "撤回请求")
public class WithdrawRequest {

    /** 撤回原因，可为空。 */
    @Size(max = 500, message = "撤回原因长度不能超过 500 个字符")
    @Schema(description = "撤回原因")
    private String remark;
}
