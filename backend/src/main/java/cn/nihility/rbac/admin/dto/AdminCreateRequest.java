package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建管理员的请求参数，可同时提交角色 id 列表与管辖组织范围列表一并创建。
 */
@Getter
@Setter
@Schema(description = "创建管理员请求参数")
public class AdminCreateRequest {

    /** 管理员名称，必填。 */
    @NotBlank(message = "管理员名称不能为空")
    @Size(max = 64, message = "管理员名称长度不能超过 64 个字符")
    @Schema(description = "管理员名称")
    private String name;

    /** 管理员编码，必填，需在未删除的管理员中保持唯一。 */
    @NotBlank(message = "管理员编码不能为空")
    @Size(max = 64, message = "管理员编码长度不能超过 64 个字符")
    @Schema(description = "管理员编码")
    private String code;

    /** 关联用户 id，必填，需在未删除的管理员中保持唯一（同一用户最多关联一个未删除的管理员身份）。 */
    @NotNull(message = "关联用户不能为空")
    @Schema(description = "关联用户 id")
    private Long userId;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;

    /** 关联角色 id 列表，可为空数组，不校验其指向的角色是否存在且启用。 */
    @Schema(description = "关联角色 id 列表，可为空数组")
    private List<Long> roleIds = new ArrayList<>();

    /** 管辖组织范围列表，可为空数组。 */
    @Valid
    @Schema(description = "管辖组织范围列表，可为空数组")
    private List<AdminOrgScopeRequest> orgScopes = new ArrayList<>();
}
