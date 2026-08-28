package cn.nihility.rbac.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批开关修改请求。
 */
@Getter
@Setter
@Schema(description = "审批开关修改请求")
public class ApprovalSwitchUpdateRequest {

    /** 是否启用审批。 */
    @NotNull(message = "审批开关状态不能为空")
    @Schema(description = "是否启用审批", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
}
