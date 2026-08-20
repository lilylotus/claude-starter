package cn.nihility.rbac.appaccess.policy.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code PolicyUserAttrMapper#selectByPolicyId} 联表查询结果的载体 DTO（非对外 VO），
 * 承载 {@code tab_app_access_policy_user_attr} JOIN {@code tab_metadata_field} 后的
 * 一行数据；{@code attrValue} 是落库原文（IN 时为逗号分隔），供服务层拆分为
 * {@link PolicyUserAttrVO#getValues()} 列表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyUserAttrRow {

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

    /** 本条记录最近一次更新时间。 */
    private LocalDateTime updateTime;
}
