package cn.nihility.rbac.app.authconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 整体替换应用用户信息响应字段映射列表时，单行映射的请求参数。{@code metadataFieldId} 允许
 * 为 {@code null}（表示固定的"用户ID"伪字段），不加 {@code @NotNull}；是否必填
 * {@code transformValue} 取决于 {@code transformType}（服务层校验），此处只做长度约束。
 */
@Getter
@Setter
@Schema(description = "用户信息字段映射保存请求参数（列表单行）")
public class AppUserinfoFieldMappingSaveRequest {

    /**
     * 本地字段 id，关联 {@code tab_metadata_field.id}；为 {@code null} 时表示固定的
     * "用户ID"伪字段（design.md Decision 2），服务层跳过其存在性/启用状态/bizType 校验。
     */
    @Schema(description = "本地字段 id，关联元数据字段；为 null 表示固定的“用户ID”伪字段")
    private Long metadataFieldId;

    /** 应用侧目标字段名称，必填。 */
    @NotBlank(message = "应用字段名称不能为空")
    @Size(max = 128, message = "应用字段名称长度不能超过 128 个字符")
    @Schema(description = "应用侧目标字段名称")
    private String appFieldName;

    /** 应用侧目标字段编码，必填，必须是合法标识符（字母开头，仅含字母、数字、下划线、短横线）。 */
    @NotBlank(message = "应用字段编码不能为空")
    @Size(max = 128, message = "应用字段编码长度不能超过 128 个字符")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$", message = "应用字段编码必须以字母开头，只能包含字母、数字、下划线、短横线")
    @Schema(description = "应用侧目标字段编码，字母开头，仅含字母/数字/下划线/短横线")
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
