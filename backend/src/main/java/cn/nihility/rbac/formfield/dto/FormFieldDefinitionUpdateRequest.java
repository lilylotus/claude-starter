package cn.nihility.rbac.formfield.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新表单字段定义的请求参数。不包含 {@code bizType}/{@code fieldCode}：
 * {@code bizType} 创建后不可修改；{@code fieldCode} 完全派生自所绑定的元数据字段，
 * 由服务层在改绑时同步刷新，客户端无需（也无法）提交；状态不通过本接口修改，需
 * 调用启用/停用/删除专用接口。若目标定义绑定的是承重字段（{@code locked=true}），
 * 服务层会拒绝将 {@code metadataFieldId} 改为不同的值，也会拒绝将
 * {@code isRequired}/{@code showInCreate}/{@code showInEdit} 改为 {@code false}；
 * 非锁定定义允许把 {@code metadataFieldId} 改绑到同一 {@code bizType} 下另一个
 * 启用且未被占用的元数据字段。
 */
@Getter
@Setter
@Schema(description = "更新表单字段定义请求参数")
public class FormFieldDefinitionUpdateRequest {

    /**
     * 绑定的元数据字段 id，可选：省略或与当前值相同表示不改绑。锁定定义（
     * {@code locked=true}）不允许传与当前值不同的取值，否则会被拒绝。
     */
    @Schema(description = "绑定的元数据字段 id，省略或与当前值相同表示不改绑；锁定定义不允许改绑")
    private Long metadataFieldId;

    /** 展示名称。 */
    @NotBlank(message = "展示名称不能为空")
    @Size(max = 64, message = "展示名称长度不能超过 64 个字符")
    @Schema(description = "展示名称")
    private String fieldName;

    /** 控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉。 */
    @NotNull(message = "控件类型不能为空")
    @Schema(description = "控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉")
    private Integer controlType;

    /** 关联的字典类型编码，仅 controlType=3（字典下拉）或 controlType=5（多选字典下拉）时必填。 */
    @Size(max = 64, message = "字典类型编码长度不能超过 64 个字符")
    @Schema(description = "关联的字典类型编码，仅控件类型为字典下拉或多选字典下拉时必填")
    private String dictTypeCode;

    /** 是否要求同 bizType 下有效数据唯一。 */
    @NotNull(message = "是否唯一不能为空")
    @Schema(description = "是否要求同业务对象类型下有效数据唯一")
    private Boolean isUnique;

    /** 是否必填。绑定承重字段的定义不允许改为 {@code false}。 */
    @NotNull(message = "是否必填不能为空")
    @Schema(description = "是否必填")
    private Boolean isRequired;

    /** 是否在列表中展示。 */
    @NotNull(message = "是否列表展示不能为空")
    @Schema(description = "是否在列表中展示")
    private Boolean showInList;

    /** 是否在新增表单中展示。绑定承重字段的定义不允许改为 {@code false}。 */
    @NotNull(message = "是否新增表单展示不能为空")
    @Schema(description = "是否在新增表单中展示")
    private Boolean showInCreate;

    /** 是否在编辑表单中展示。绑定承重字段的定义不允许改为 {@code false}。 */
    @NotNull(message = "是否编辑表单展示不能为空")
    @Schema(description = "是否在编辑表单中展示")
    private Boolean showInEdit;

    /** 表单中展示时是否可编辑，为否则只读展示。 */
    @NotNull(message = "是否可编辑不能为空")
    @Schema(description = "表单中展示时是否可编辑")
    private Boolean editable;

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
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;
}
