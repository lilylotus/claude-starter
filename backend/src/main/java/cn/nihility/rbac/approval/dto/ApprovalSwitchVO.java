package cn.nihility.rbac.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 审批开关视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "主数据审批开关")
public class ApprovalSwitchVO {

    /** 业务对象类型。 */
    @Schema(description = "业务对象类型", example = "ORG")
    private String bizType;

    /** 是否启用审批。 */
    @Schema(description = "是否启用审批", example = "true")
    private Boolean enabled;
}
