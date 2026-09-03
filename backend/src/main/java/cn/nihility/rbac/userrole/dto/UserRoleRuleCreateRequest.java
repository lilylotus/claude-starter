package cn.nihility.rbac.userrole.dto;

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
 * 新增用户角色规则请求参数。{@code orgScopes}、{@code userAttrs} 至少配置一类，均为空时
 * 接口 SHALL 拒绝请求；保存成功后立即按当前条件执行一次
 * （add-user-role-batch-assignment change design.md Decision 3）。
 */
@Getter
@Setter
@Schema(description = "新增用户角色规则请求参数")
public class UserRoleRuleCreateRequest {

    /** 目标角色 id，必填。 */
    @NotNull(message = "角色不能为空")
    @Schema(description = "目标角色 id")
    private Long roleId;

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
