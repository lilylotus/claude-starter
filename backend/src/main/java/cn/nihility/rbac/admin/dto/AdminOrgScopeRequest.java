package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员管辖组织范围的请求参数，随管理员创建/更新接口整体提交、整体同步（不做按行 diff）。
 */
@Getter
@Setter
@Schema(description = "管理员管辖组织范围请求参数")
public class AdminOrgScopeRequest {

    /** 组织 id，必填，不校验其指向的组织是否存在且启用。 */
    @NotNull(message = "组织不能为空")
    @Schema(description = "组织 id")
    private Long orgId;

    /** 是否包含递归子组织，必填。 */
    @NotNull(message = "是否包含子组织不能为空")
    @Schema(description = "是否包含递归子组织", defaultValue = "false")
    private Boolean includeChildren = Boolean.FALSE;
}
