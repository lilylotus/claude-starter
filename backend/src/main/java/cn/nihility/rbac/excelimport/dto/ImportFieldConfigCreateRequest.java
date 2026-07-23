package cn.nihility.rbac.excelimport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建 Excel 导入字段配置的请求参数。{@code fieldCode} 仅在 {@code formFieldDefinitionId}
 * 为空时生效（服务层直接采用其取值）；{@code formFieldDefinitionId} 非空时
 * {@code fieldCode} 由服务层取自所绑定表单字段定义的当前 {@code fieldCode}，请求中提交
 * 的值会被忽略。
 */
@Getter
@Setter
@Schema(description = "创建 Excel 导入字段配置请求参数")
public class ImportFieldConfigCreateRequest {

    /** 业务对象类型：ORG/USER/POSITION/APP。 */
    @NotBlank(message = "业务对象类型不能为空")
    @Schema(description = "业务对象类型：ORG/USER/POSITION/APP")
    private String bizType;

    /** 关联的表单字段定义 id，可空；非空时须指向一个存在且启用、bizType 一致的表单字段定义。 */
    @Schema(description = "关联的表单字段定义 id，可空")
    private Long formFieldDefinitionId;

    /** 字段标识，仅在 formFieldDefinitionId 为空时生效且必填，非空时由服务层取自所绑定定义的当前值。 */
    @Size(max = 64, message = "字段标识长度不能超过 64 个字符")
    @Schema(description = "字段标识，仅在未关联表单字段定义时生效且必填")
    private String fieldCode;

    /** Excel 表头文字，可与表单展示名称不同。 */
    @NotBlank(message = "Excel 表头文字不能为空")
    @Size(max = 64, message = "Excel 表头文字长度不能超过 64 个字符")
    @Schema(description = "Excel 表头文字")
    private String excelHeaderName;

    /** 是否作为匹配已有记录的主键列之一。 */
    @NotNull(message = "是否主键不能为空")
    @Schema(description = "是否作为匹配已有记录的主键列之一", defaultValue = "false")
    private Boolean isPrimaryKey = Boolean.FALSE;

    /** 导入语义下的必填，独立于表单字段定义的必填开关。 */
    @NotNull(message = "是否必填不能为空")
    @Schema(description = "导入语义下的必填", defaultValue = "false")
    private Boolean isRequired = Boolean.FALSE;

    /** 显示序号，值越大越靠前，决定模板表头列顺序。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;
}
