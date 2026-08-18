package cn.nihility.rbac.app.authconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用用户信息响应字段映射视图对象。{@code fieldName}/{@code fieldCode} 是本地字段（关联
 * 的元数据字段，或固定的"用户ID"伪字段）的实时展示信息，只读；{@code appFieldName}/
 * {@code appFieldCode}/{@code transformType}/{@code transformValue} 是本行映射自身持久化
 * 的可编辑内容。该应用未保存过任何映射记录时，返回现算的默认两行（design.md Decision 4），
 * 此时 {@code id} 为 {@code null}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "应用用户信息响应字段映射")
public class AppUserinfoFieldMappingVO {

    /** 主键 id，现算默认行时为 {@code null}。 */
    @Schema(description = "主键 id，未保存过时的默认行为 null")
    private Long id;

    /** 本地字段 id，关联 {@code tab_metadata_field.id}；为空表示固定的"用户ID"伪字段。 */
    @Schema(description = "本地字段 id，关联元数据字段；为空表示固定的“用户ID”伪字段")
    private Long metadataFieldId;

    /** 本地字段名称，只读，实时取自关联的元数据字段，或固定字面量"用户ID"。 */
    @Schema(description = "本地字段名称，只读")
    private String fieldName;

    /** 本地字段编码，只读，实时取自关联的元数据字段，或固定字面量"id"。 */
    @Schema(description = "本地字段编码，只读")
    private String fieldCode;

    /** 应用侧目标字段名称。 */
    @Schema(description = "应用侧目标字段名称")
    private String appFieldName;

    /** 应用侧目标字段编码。 */
    @Schema(description = "应用侧目标字段编码")
    private String appFieldCode;

    /** 转换方式：NO_TRANSFORM/FIXED_VALUE/SCRIPT。 */
    @Schema(description = "转换方式：NO_TRANSFORM（不转换）/FIXED_VALUE（固定值）/SCRIPT（转换脚本）")
    private String transformType;

    /** 转换取值：固定值的具体值，或脚本源码。 */
    @Schema(description = "转换取值：固定值的具体值，或脚本源码")
    private String transformValue;
}
