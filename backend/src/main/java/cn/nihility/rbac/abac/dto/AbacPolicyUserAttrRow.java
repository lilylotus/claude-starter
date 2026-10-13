package cn.nihility.rbac.abac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code AbacPolicyUserAttrMapper#selectByPolicyId} 联表查询结果的载体 DTO（非对外 VO），
 * 承载 {@code tab_abac_policy_user_attr} JOIN {@code tab_metadata_field} 后的一行数据；
 * {@code attrValue} 是落库原文（{@code IN} 时为逗号分隔），供服务层拆分为
 * {@link AbacPolicyUserAttrVO#getValues()} 列表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbacPolicyUserAttrRow {

    /** 关联的元数据字段 id。 */
    private Long metadataFieldId;

    /** 关联的元数据字段名称。 */
    private String fieldName;

    /** 关联的元数据字段编码。 */
    private String fieldCode;

    /** 运算符：EQ/NE/IN。 */
    private String operator;

    /** 比较值落库原文，EQ/NE 为单个值，IN 为逗号分隔的多个值。 */
    private String attrValue;
}
