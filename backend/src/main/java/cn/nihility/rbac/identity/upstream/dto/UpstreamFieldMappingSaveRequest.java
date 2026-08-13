package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 整体替换上游字段映射列表时，单行映射的请求参数。是否必填 {@code transformValue}
 * 取决于 {@code transformType}（服务层校验），此处只做长度约束。
 */
@Getter
@Setter
@Schema(description = "上游字段映射保存请求参数（列表单行）")
public class UpstreamFieldMappingSaveRequest {

    /** 上游字段名称，必填，管理员手工填写。 */
    @NotBlank(message = "上游字段名称不能为空")
    @Size(max = 128, message = "上游字段名称长度不能超过 128 个字符")
    @Schema(description = "上游字段名称")
    private String upstreamFieldName;

    /** 上游字段编码，必填，管理员手工填写，同一数据域内不允许重复。 */
    @NotBlank(message = "上游字段编码不能为空")
    @Size(max = 128, message = "上游字段编码长度不能超过 128 个字符")
    @Schema(description = "上游字段编码")
    private String upstreamFieldCode;

    /** 目标元数据字段 id，必填，关联 {@code tab_metadata_field.id}。 */
    @NotNull(message = "目标字段不能为空")
    @Schema(description = "目标元数据字段 id，关联元数据字段")
    private Long metadataFieldId;

    /** 是否作为落库匹配的主键标识字段之一，必填；同一数据域可多选组成联合主键（AND 语义），
     * 若本次提交的列表非空，须至少有一行为 {@code true}（服务层校验）。 */
    @NotNull(message = "主键标识不能为空")
    @Schema(description = "是否作为落库匹配的主键标识字段之一")
    private Boolean isPrimaryKey;

    /** 转换方式，必填，只能是 NO_TRANSFORM/FIXED_VALUE/SCRIPT。 */
    @NotBlank(message = "转换方式不能为空")
    @Pattern(regexp = "^(NO_TRANSFORM|FIXED_VALUE|SCRIPT)$", message = "转换方式只能是 NO_TRANSFORM、FIXED_VALUE 或 SCRIPT")
    @Schema(description = "转换方式：NO_TRANSFORM（不转换）/FIXED_VALUE（固定值）/SCRIPT（转换脚本）")
    private String transformType;

    /**
     * 转换取值，是否必填取决于 {@link #transformType}（服务层校验）：{@code FIXED_VALUE}
     * 时是固定值的具体值，{@code SCRIPT} 时是脚本源码，{@code NO_TRANSFORM} 时应为空。
     */
    @Size(max = 5000, message = "转换取值长度不能超过 5000 个字符")
    @Schema(description = "转换取值：固定值的具体值，或脚本源码")
    private String transformValue;
}
