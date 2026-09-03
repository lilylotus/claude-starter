package cn.nihility.rbac.userrole.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code UserRoleRuleUserAttrMapper#selectByRuleId} 联表查询结果的载体 DTO（非对外 VO），
 * 承载 {@code tab_user_role_rule_user_attr} JOIN {@code tab_metadata_field} 后的一行数据；
 * {@code attrValue} 是落库原文（{@code IN} 时为逗号分隔），供服务层拆分为
 * {@link UserRoleRuleUserAttrVO#getValues()} 列表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleRuleUserAttrRow {

    /** 关联的元数据字段 id。 */
    private Long metadataFieldId;

    /** 关联的元数据字段名称。 */
    private String fieldName;

    /** 关联的元数据字段编码。 */
    private String fieldCode;

    /** 关联的元数据字段业务域：{@code USER}/{@code POSITION}。 */
    private String bizType;

    /** 运算符：EQ/NE/IN。 */
    private String operator;

    /** 比较值落库原文，EQ/NE 为单个值，IN 为逗号分隔的多个值。 */
    private String attrValue;
}
