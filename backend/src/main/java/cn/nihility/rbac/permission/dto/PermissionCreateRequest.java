package cn.nihility.rbac.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建权限点的请求参数。
 */
@Getter
@Setter
@Schema(description = "创建权限点请求参数")
public class PermissionCreateRequest {

    /** 权限名称，必填。 */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64, message = "权限名称长度不能超过 64 个字符")
    @Schema(description = "权限名称")
    private String name;

    /** 权限编码，必填，需在未删除的权限点中保持唯一。 */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 64, message = "权限编码长度不能超过 64 个字符")
    @Schema(description = "权限编码")
    private String code;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;
}
