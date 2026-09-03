package cn.nihility.rbac.userrole.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户角色规则组织范围条件持久化实体，对应表 {@code tab_user_role_rule_org_scope}，字段
 * 形状对齐 {@code cn.nihility.rbac.appaccess.policy.entity.PolicyOrgScopeEntity}，仅把
 * {@code policyId} 换成 {@code ruleId}。无独立 {@code status}，随所属规则保存时整体替换
 * （先删后插），物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_user_role_rule_org_scope")
public class UserRoleRuleOrgScopeEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属规则 id，关联 {@code tab_user_role_rule.id}。 */
    private Long ruleId;

    /** 组织 id，关联 {@code tab_org.id}。 */
    private Long orgId;

    /** 是否包含递归子组织：{@code false}=否，{@code true}=是。 */
    private Boolean includeChildren;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
