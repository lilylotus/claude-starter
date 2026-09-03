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
 * 用户角色规则持久化实体，对应表 {@code tab_user_role_rule}，通过 {@code roleId} 关联
 * {@code tab_role.id}（不建物理外键）。一个角色可以有多条规则，规则条件持久化在
 * {@code tab_user_role_rule_org_scope}/{@code tab_user_role_rule_user_attr} 两张子表，
 * 执行结果落在 {@code tab_user_role_rule_grant}（按 {@code ruleId} 整体重建），是"用户是否
 * 持有某角色"的唯一数据来源，取代最初设计里独立的 {@code tab_user_role} 表
 * （add-user-role-batch-assignment change design.md Decision 1，二次设计版本）。本能力不
 * 引入"启用/停用"暂停态：规则要么存在要么删除，存在就参与自动重算。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_user_role_rule")
public class UserRoleRuleEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标角色 id，关联 {@code tab_role.id}。 */
    private Long roleId;

    /** 规则名称，便于同一角色下管理多条规则。 */
    private String name;

    /** 备注。 */
    private String remark;

    /** 最近一次执行时间，从未执行过为空。 */
    private LocalDateTime lastExecTime;

    /** 最近一次执行人（人工保存触发时为操作人，事件自动触发时为原始事件操作人）。 */
    private String lastExecBy;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
