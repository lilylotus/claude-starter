package cn.nihility.rbac.excelimport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新 Excel 导入字段配置的请求参数。不包含 {@code bizType}（创建后不可修改）与
 * {@code fieldCode}（完全派生自绑定的表单字段定义或创建时的初始值，改绑时由服务层
 * 同步刷新，客户端无需也无法直接提交）。对锁定（系统保护）的配置（POSITION 的
 * {@code __userCode}/{@code __orgCode}），服务层会拒绝将 {@code formFieldDefinitionId}
 * 改绑为不同的值，也会拒绝将 {@code isPrimaryKey}/{@code isRequired} 改为 {@code false}；
 * {@code excelHeaderName}/{@code showOrder} 仍可调整。
 */
@Getter
@Setter
@Schema(description = "更新 Excel 导入字段配置请求参数")
public class ImportFieldConfigUpdateRequest {

    /**
     * 关联的表单字段定义 id，可选：省略或与当前值相同表示不改绑。锁定配置不允许传
     * 与当前值（{@code null}）不同的取值，否则会被拒绝。
     */
    @Schema(description = "关联的表单字段定义 id，省略或与当前值相同表示不改绑；锁定配置不允许改绑")
    private Long formFieldDefinitionId;

    /** Excel 表头文字，可与表单展示名称不同。 */
    @NotBlank(message = "Excel 表头文字不能为空")
    @Size(max = 64, message = "Excel 表头文字长度不能超过 64 个字符")
    @Schema(description = "Excel 表头文字")
    private String excelHeaderName;

    /** 是否作为匹配已有记录的主键列之一。锁定配置不允许改为 {@code false}。 */
    @NotNull(message = "是否主键不能为空")
    @Schema(description = "是否作为匹配已有记录的主键列之一")
    private Boolean isPrimaryKey;

    /** 导入语义下的必填。锁定配置不允许改为 {@code false}。 */
    @NotNull(message = "是否必填不能为空")
    @Schema(description = "导入语义下的必填")
    private Boolean isRequired;

    /** 显示序号，值越大越靠前，决定模板表头列顺序。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;
}
