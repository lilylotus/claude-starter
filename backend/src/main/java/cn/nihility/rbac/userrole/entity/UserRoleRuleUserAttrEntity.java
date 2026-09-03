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
 * 用户角色规则用户属性条件持久化实体，对应表 {@code tab_user_role_rule_user_attr}，字段
 * 形状对齐 {@code cn.nihility.rbac.appaccess.policy.entity.PolicyUserAttrEntity}，
 * {@code metadataFieldId} 关联的元数据字段允许 {@code bizType} 为 {@code USER} 或
 * {@code POSITION}（比现成的应用访问授权多一个域）。无独立 {@code status}，随所属规则保存
 * 时整体替换（先删后插），物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_user_role_rule_user_attr")
public class UserRoleRuleUserAttrEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属规则 id，关联 {@code tab_user_role_rule.id}。 */
    private Long ruleId;

    /** 关联的元数据字段 id，{@code bizType} 为 {@code USER} 或 {@code POSITION}。 */
    private Long metadataFieldId;

    /** 运算符：{@code EQ}=等于，{@code NE}=不等于，{@code IN}=属于多值。 */
    private String operator;

    /** 比较值：{@code EQ}/{@code NE} 为单个值，{@code IN} 为逗号分隔的多个值。 */
    private String attrValue;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
