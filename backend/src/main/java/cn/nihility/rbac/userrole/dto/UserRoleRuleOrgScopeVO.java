package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户角色规则组织范围条件视图对象，{@code orgName} 关联 {@code tab_org} 实时查询回填。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户角色规则组织范围条件")
public class UserRoleRuleOrgScopeVO {

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
