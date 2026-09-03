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
 * 用户角色规则计算结果持久化实体，对应表 {@code tab_user_role_rule_grant}，通过
 * {@code ruleId} 关联 {@code tab_user_role_rule.id}、{@code userId} 关联
 * {@code tab_user.id}（均不建物理外键）；{@code roleId} 冗余存储自所属规则的
 * {@code roleId}，避免查询时反查规则表。按 {@code ruleId} 整体重建（
 * {@code UserRoleRuleExecutionServiceImpl#execute}），是"用户是否持有某角色"的唯一数据
 * 来源：{@code EXISTS (SELECT 1 FROM tab_user_role_rule_grant WHERE user_id = U AND
 * role_id = R)} 即视为用户 U 当前持有角色 R；一个用户的同一个角色可能被多条规则同时命中
 * （各自一行，{@code ruleId} 不同），任一规则仍然命中即视为持有
 * （add-user-role-batch-assignment change design.md Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_user_role_rule_grant")
public class UserRoleRuleGrantEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产生该关联的规则 id，关联 {@code tab_user_role_rule.id}。 */
    private Long ruleId;

    /** 用户 id，关联 {@code tab_user.id}。 */
    private Long userId;

    /** 角色 id，冗余存储自 {@code tab_user_role_rule.roleId}。 */
    private Long roleId;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
