package cn.nihility.rbac.formfield.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建表单字段定义的请求参数。{@code bizType}/{@code fieldCode} 均不在此请求中，
 * 创建时取自 {@code metadataFieldId} 所绑定的元数据字段，客户端无需（也无法）提交。
 */
@Getter
@Setter
@Schema(description = "创建表单字段定义请求参数")
public class FormFieldDefinitionCreateRequest {

    /** 绑定的元数据字段 id，必须指向一个当前状态为启用、且未被其他有效定义绑定的元数据字段。 */
    @NotNull(message = "绑定的元数据字段不能为空")
    @Schema(description = "绑定的元数据字段 id")
    private Long metadataFieldId;

    /** 展示名称。 */
    @NotBlank(message = "展示名称不能为空")
    @Size(max = 64, message = "展示名称长度不能超过 64 个字符")
    @Schema(description = "展示名称")
    private String fieldName;

    /** 控件类型：1=文本框，2=数字框，3=字典下拉。 */
    @NotNull(message = "控件类型不能为空")
    @Schema(description = "控件类型：1=文本框，2=数字框，3=字典下拉")
    private Integer controlType;

    /** 关联的字典类型 id，仅 controlType=3 时必填。 */
    @Schema(description = "关联的字典类型 id，仅控件类型为字典下拉时必填")
    private Long dictTypeId;

    /** 是否要求同 bizType 下有效数据唯一。 */
    @NotNull(message = "是否唯一不能为空")
    @Schema(description = "是否要求同业务对象类型下有效数据唯一", defaultValue = "false")
    private Boolean isUnique = Boolean.FALSE;

    /** 是否必填。 */
    @NotNull(message = "是否必填不能为空")
    @Schema(description = "是否必填", defaultValue = "false")
    private Boolean isRequired = Boolean.FALSE;

    /** 是否在列表中展示。 */
    @NotNull(message = "是否列表展示不能为空")
    @Schema(description = "是否在列表中展示", defaultValue = "true")
    private Boolean showInList = Boolean.TRUE;

    /** 是否在新增表单中展示。 */
    @NotNull(message = "是否新增表单展示不能为空")
    @Schema(description = "是否在新增表单中展示", defaultValue = "true")
    private Boolean showInCreate = Boolean.TRUE;

    /** 是否在编辑表单中展示。 */
    @NotNull(message = "是否编辑表单展示不能为空")
    @Schema(description = "是否在编辑表单中展示", defaultValue = "true")
    private Boolean showInEdit = Boolean.TRUE;

    /** 表单中展示时是否可编辑，为否则只读展示。 */
    @NotNull(message = "是否可编辑不能为空")
    @Schema(description = "表单中展示时是否可编辑", defaultValue = "true")
    private Boolean editable = Boolean.TRUE;

    /** 正则校验规则，可选。 */
    @Size(max = 255, message = "正则校验规则长度不能超过 255 个字符")
    @Schema(description = "正则校验规则")
    private String validateRegex;

    /** 输入提示文字，可选。 */
    @Size(max = 128, message = "输入提示文字长度不能超过 128 个字符")
    @Schema(description = "输入提示文字")
    private String placeholder;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;
}
