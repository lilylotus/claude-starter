package cn.nihility.rbac.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建角色的请求参数。
 */
@Getter
@Setter
@Schema(description = "创建角色请求参数")
public class RoleCreateRequest {

    /** 角色名称，必填。 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
    @Schema(description = "角色名称")
    private String name;

    /** 角色编码，必填，需在未删除的角色中保持唯一。 */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64 个字符")
    @Schema(description = "角色编码")
    private String code;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;

    /** 待分配的权限点 id 列表，可选，不传或空数组视为不授予任何权限点。 */
    @Schema(description = "待分配的权限点 id 列表，不传或空数组视为不授予任何权限点")
    private List<Long> permissionIds;
}
