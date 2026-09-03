package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 按角色批量设置管理员请求参数。以 {@code user-role-assignment} 能力维护的用户角色规则
 * 计算结果（{@code tab_user_role_rule_grant}）为匹配来源，不再重新配置组织/属性条件；
 * {@code orgScopes} 仅应用于本批次新建的管理员，不影响"已是管理员、仅补角色"的用户的
 * 既有管辖组织范围（add-user-role-batch-assignment change design.md Decision 5，
 * 二次设计版本）。
 */
@Getter
@Setter
@Schema(description = "按角色批量设置管理员请求参数")
public class AdminBatchPromoteByRoleRequest {

    /** 目标角色 id，必填。 */
    @NotNull(message = "角色不能为空")
    @Schema(description = "目标角色 id")
    private Long roleId;

    /** 统一应用于本批次全部"新建"管理员的管辖组织范围，可为空表示不限。 */
    @Valid
    @Schema(description = "统一应用于本批次全部新建管理员的管辖组织范围，可为空数组表示不限，"
            + "不影响已是管理员、仅补角色的用户的既有管辖组织范围")
    private List<AdminOrgScopeRequest> orgScopes = new ArrayList<>();

    /** 是否为预览模式：true 只返回"将新建管理员"/"将补充角色"两个分组预览，false 执行。 */
    @Schema(description = "是否为预览模式：true 只返回将新建管理员/将补充角色两个分组预览，false 执行", defaultValue = "false")
    private Boolean preview = Boolean.FALSE;
}
