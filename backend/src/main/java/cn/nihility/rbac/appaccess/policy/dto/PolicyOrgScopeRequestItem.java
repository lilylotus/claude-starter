package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 策略组织范围条件请求项。
 */
@Getter
@Setter
@Schema(description = "策略组织范围条件")
public class PolicyOrgScopeRequestItem {

    /** 组织 id，必填。 */
    @NotNull(message = "组织不能为空")
    @Schema(description = "组织 id")
    private Long orgId;

    /** 是否包含递归子组织。 */
    @NotNull(message = "是否包含子组织不能为空")
    @Schema(description = "是否包含递归子组织")
    private Boolean includeChildren;
}
