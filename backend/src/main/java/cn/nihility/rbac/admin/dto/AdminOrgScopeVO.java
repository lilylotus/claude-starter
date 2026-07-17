package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理员管辖组织范围视图对象，随管理员详情一并返回。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员管辖组织范围")
public class AdminOrgScopeVO {

    /** 组织 id。 */
    @Schema(description = "组织 id")
    private Long orgId;

    /** 组织名称。 */
    @Schema(description = "组织名称")
    private String orgName;

    /** 是否包含递归子组织。 */
    @Schema(description = "是否包含递归子组织")
    private Boolean includeChildren;
}
