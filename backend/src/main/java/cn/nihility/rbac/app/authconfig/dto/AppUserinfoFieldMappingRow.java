package cn.nihility.rbac.app.authconfig.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code AppUserinfoFieldMappingMapper#selectByAppRefId} 联表查询结果的载体 DTO（非对外
 * VO），承载 {@code tab_app_userinfo_field_mapping} LEFT JOIN {@code tab_metadata_field}
 * 后的一行数据，也复用于该应用未保存过任何映射记录时的现算默认两行（design.md Decision 4），
 * 供 {@code AppUserinfoFieldMappingConvert} 转换为对外的 {@link AppUserinfoFieldMappingVO}，
 * 以及 {@code SsoUserinfoAttributesResolver} 运行时解析使用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserinfoFieldMappingRow {

    /** 主键 id（{@code tab_app_userinfo_field_mapping.id}），现算默认行时为 {@code null}。 */
    private Long id;

    /** 本地字段 id，关联 {@code tab_metadata_field.id}；为空表示固定的"用户ID"伪字段。 */
    private Long metadataFieldId;

    /** 本地字段名称，实时取自关联的元数据字段，或固定字面量"用户ID"。 */
    private String fieldName;

    /** 本地字段编码，实时取自关联的元数据字段，或固定字面量"id"。 */
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
