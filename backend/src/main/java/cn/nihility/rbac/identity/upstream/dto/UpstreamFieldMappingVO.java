package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上游字段映射视图对象。{@code upstreamFieldName}/{@code upstreamFieldCode} 是管理员手工
 * 填写、可编辑的源字段信息；{@code fieldName}/{@code fieldCode} 是目标（关联的元数据字段）
 * 的实时展示信息，只读。方向与 {@code AppSyncFieldMappingVO} 相反（design.md Decision 5）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上游字段映射")
public class UpstreamFieldMappingVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 上游字段名称，管理员手工填写。 */
    @Schema(description = "上游字段名称")
    private String upstreamFieldName;

    /** 上游字段编码，管理员手工填写。 */
    @Schema(description = "上游字段编码")
    private String upstreamFieldCode;

    /** 目标元数据字段 id，关联 {@code tab_metadata_field.id}。 */
    @Schema(description = "目标元数据字段 id，关联元数据字段")
    private Long metadataFieldId;

    /** 目标字段名称，只读，实时取自关联的元数据字段。 */
    @Schema(description = "目标字段名称，只读")
    private String fieldName;

    /** 目标字段编码，只读，实时取自关联的元数据字段。 */
    @Schema(description = "目标字段编码，只读")
    private String fieldCode;

    /** 转换方式：NO_TRANSFORM/FIXED_VALUE/SCRIPT。 */
    @Schema(description = "转换方式：NO_TRANSFORM（不转换）/FIXED_VALUE（固定值）/SCRIPT（转换脚本）")
    private String transformType;

    /** 转换取值：固定值的具体值，或脚本源码。 */
    @Schema(description = "转换取值：固定值的具体值，或脚本源码")
    private String transformValue;
}
