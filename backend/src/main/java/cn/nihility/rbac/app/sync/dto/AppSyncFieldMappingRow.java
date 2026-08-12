package cn.nihility.rbac.app.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code AppSyncFieldMappingMapper#selectByAppRefIdAndDomain} 联表查询结果的载体
 * DTO（非对外 VO），承载 {@code tab_app_sync_field_mapping} LEFT JOIN
 * {@code tab_metadata_field} 后的一行数据，供 {@code AppSyncFieldMappingConvert}
 * 转换为对外的 {@link AppSyncFieldMappingVO}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSyncFieldMappingRow {

    /** 主键 id（{@code tab_app_sync_field_mapping.id}）。 */
    private Long id;

    /** 源字段 id，关联 {@code tab_metadata_field.id}。 */
    private Long metadataFieldId;

    /** 源字段名称，实时取自关联的元数据字段。 */
    private String fieldName;

    /** 源字段编码，实时取自关联的元数据字段。 */
    private String fieldCode;

    /** 应用侧目标字段名称。 */
    private String appFieldName;

    /** 应用侧目标字段编码。 */
    private String appFieldCode;

    /** 转换方式：NO_TRANSFORM/FIXED_VALUE/SCRIPT。 */
    private String transformType;

    /** 转换取值：固定值的具体值，或脚本源码。 */
    private String transformValue;
}
