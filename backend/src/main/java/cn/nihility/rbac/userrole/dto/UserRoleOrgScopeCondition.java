package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织范围条件，形状对齐
 * {@code cn.nihility.rbac.appaccess.policy.dto.PolicyOrgScopeRequestItem}，但独立成类。
 * 既作为用户角色规则新增/编辑/预览接口的请求项，也是
 * {@code UserMatchConditionResolver#resolve} 的输入类型——规则条件持久化在
 * {@code tab_user_role_rule_org_scope}，执行引擎读取后转换为本类型再调用条件匹配组件
 * （add-user-role-batch-assignment change design.md Decision 1/2，二次设计版本）。命中
 * 语义为"该用户存在至少一条状态未删除的任职记录，其所属组织落在本条组织范围内
 * （{@code includeChildren} 为真时递归包含子组织）"；多条组织范围条件之间取并集。
 */
@Getter
@Setter
@Schema(description = "用户角色批量添加的组织范围条件")
public class UserRoleOrgScopeCondition {

    /** 组织 id，必填。 */
    @NotNull(message = "组织不能为空")
    @Schema(description = "组织 id")
    private Long orgId;

    /** 是否包含递归子组织，必填。 */
    @NotNull(message = "是否包含子组织不能为空")
    @Schema(description = "是否包含递归子组织", defaultValue = "false")
    private Boolean includeChildren = Boolean.FALSE;
}
