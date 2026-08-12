package cn.nihility.rbac.app.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 整体替换应用同步字段映射列表时，单行映射的请求参数。是否必填 {@code transformValue}
 * 取决于 {@code transformType}（服务层校验），此处只做长度约束。
 */
@Getter
@Setter
@Schema(description = "同步字段映射保存请求参数（列表单行）")
public class AppSyncFieldMappingSaveRequest {

    /** 源字段 id，必填，关联 {@code tab_metadata_field.id}。 */
    @NotNull(message = "源字段不能为空")
    @Schema(description = "源字段 id，关联元数据字段")
    private Long metadataFieldId;

    /** 应用侧目标字段名称，必填。 */
    @NotBlank(message = "应用字段名称不能为空")
    @Size(max = 128, message = "应用字段名称长度不能超过 128 个字符")
    @Schema(description = "应用侧目标字段名称")
    private String appFieldName;

    /** 应用侧目标字段编码，必填。 */
    @NotBlank(message = "应用字段编码不能为空")
    @Size(max = 128, message = "应用字段编码长度不能超过 128 个字符")
    @Schema(description = "应用侧目标字段编码")
    private String appFieldCode;

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
