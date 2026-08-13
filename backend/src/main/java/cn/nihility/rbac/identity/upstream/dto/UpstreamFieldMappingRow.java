package cn.nihility.rbac.identity.upstream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code UpstreamFieldMappingMapper#selectBySourceIdAndDataType} 联表查询结果的载体
 * DTO（非对外 VO），承载 {@code tab_upstream_field_mapping} LEFT JOIN
 * {@code tab_metadata_field} 后的一行数据。{@link #fieldCode} 是目标元数据字段实时的
 * {@code fieldCode}（等价于对应 CreateRequest/UpdateRequest 的 Java 属性名），供
 * {@code UpstreamFieldMappingTransformer} 直接复用同一份查询结果做字段转换，不必重复
 * 查询一遍（design.md Decision 3）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamFieldMappingRow {

    /** 主键 id（{@code tab_upstream_field_mapping.id}）。 */
    private Long id;

    /** 上游字段名称，管理员手工填写。 */
    private String upstreamFieldName;

    /** 上游字段编码，管理员手工填写，取数结果按该编码作为 key 查找原始值。 */
    private String upstreamFieldCode;

    /** 目标元数据字段 id，关联 {@code tab_metadata_field.id}。 */
    private Long metadataFieldId;

    /** 目标字段名称，实时取自关联的元数据字段。 */
    private String fieldName;

    /** 目标字段编码，实时取自关联的元数据字段，等价于对应请求对象的 Java 属性名。 */
    private String fieldCode;

    /** 转换方式：NO_TRANSFORM/FIXED_VALUE/SCRIPT。 */
    private String transformType;

    /** 转换取值：固定值的具体值，或脚本源码。 */
    private String transformValue;
}
