package cn.nihility.rbac.app.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用同步字段映射视图对象。{@code fieldName}/{@code fieldCode} 是源字段（关联的元数据字段）
 * 的实时展示信息，只读；{@code appFieldName}/{@code appFieldCode}/{@code transformType}/
 * {@code transformValue} 是本行映射自身持久化的可编辑内容。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "应用同步字段映射")
public class AppSyncFieldMappingVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 源字段 id，关联 {@code tab_metadata_field.id}。 */
    @Schema(description = "源字段 id，关联元数据字段")
    private Long metadataFieldId;

    /** 源字段名称，只读，实时取自关联的元数据字段。 */
    @Schema(description = "源字段名称，只读")
    private String fieldName;

    /** 源字段编码，只读，实时取自关联的元数据字段。 */
    @Schema(description = "源字段编码，只读")
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
