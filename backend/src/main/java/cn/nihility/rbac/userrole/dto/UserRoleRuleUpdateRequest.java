package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑用户角色规则请求参数，目标角色不可通过本请求修改（规则始终归属创建时选定的角色）。
 * {@code orgScopes}、{@code userAttrs} 至少配置一类，均为空时接口 SHALL 拒绝请求；条件子表
 * 按整体替换语义处理（先删后插），保存成功后立即按新条件重新执行一次
 * （add-user-role-batch-assignment change design.md Decision 3）。
 */
@Getter
@Setter
@Schema(description = "编辑用户角色规则请求参数")
public class UserRoleRuleUpdateRequest {

    /** 规则名称，必填。 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128 个字符")
    @Schema(description = "规则名称")
    private String name;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;

    /** 组织范围条件列表，与用户属性条件至少配置一类。 */
    @Valid
    @Schema(description = "组织范围条件列表，与用户属性条件至少配置一类")
    private List<UserRoleOrgScopeCondition> orgScopes = new ArrayList<>();

    /** 用户属性条件列表，与组织范围条件至少配置一类。 */
    @Valid
    @Schema(description = "用户属性条件列表，与组织范围条件至少配置一类")
    private List<UserRoleUserAttrCondition> userAttrs = new ArrayList<>();
}
