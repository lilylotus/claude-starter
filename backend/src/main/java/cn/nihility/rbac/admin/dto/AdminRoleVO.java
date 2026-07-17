package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理员关联角色视图对象，随管理员详情一并返回。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员关联角色")
public class AdminRoleVO {

    /** 角色 id。 */
    @Schema(description = "角色 id")
    private Long roleId;

    /** 角色名称。 */
    @Schema(description = "角色名称")
    private String roleName;
}
