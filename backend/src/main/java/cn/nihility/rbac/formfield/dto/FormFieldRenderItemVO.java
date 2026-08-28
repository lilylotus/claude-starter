package cn.nihility.rbac.formfield.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 动态字段渲染元数据条目，供组织/用户/任职/应用四个管理页面驱动列表列与
 * 新增/编辑表单的动态渲染，一次性返回指定业务对象类型下全部启用状态的字段定义。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态字段渲染元数据条目")
public class FormFieldRenderItemVO {

    /** 前端/DTO 使用的字段标识，前端据此把值绑定到表单/表格的对应字段。 */
    @Schema(description = "前端/DTO 使用的字段标识")
    private String fieldCode;

    /** 展示名称。 */
    @Schema(description = "展示名称")
    private String fieldName;

    /** 绑定的元数据字段列名，冗余展示，便于前端确认对应的物理列。 */
    @Schema(description = "绑定的元数据字段列名")
    private String columnName;

    /** 控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉。 */
    @Schema(description = "控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉")
    private Integer controlType;

    /** 是否必填。 */
    @Schema(description = "是否必填")
    private Boolean isRequired;

    /** 是否要求同业务对象类型下有效数据唯一。 */
    @Schema(description = "是否要求同业务对象类型下有效数据唯一")
    private Boolean isUnique;

    /** 是否在列表中展示。 */
    @Schema(description = "是否在列表中展示")
    private Boolean showInList;

    /** 是否在新增表单中展示。 */
    @Schema(description = "是否在新增表单中展示")
    private Boolean showInCreate;

    /** 是否在编辑表单中展示。 */
    @Schema(description = "是否在编辑表单中展示")
    private Boolean showInEdit;

    /** 是否导出，独立于是否列表展示，决定该字段是否出现在导出 Excel 的列中。 */
    @Schema(description = "是否导出，独立于是否列表展示配置")
    private Boolean showInExport;

    /** 表单中展示时是否可编辑，为否则前端应渲染为只读态而非隐藏。 */
    @Schema(description = "表单中展示时是否可编辑")
    private Boolean editable;

    /** 是否为承重字段（锁定字段），计算得出。 */
    @Schema(description = "是否为承重字段（锁定字段）")
    private Boolean locked;

    /** 正则校验规则。 */
    @Schema(description = "正则校验规则")
    private String validateRegex;

    /** 输入提示文字。 */
    @Schema(description = "输入提示文字")
    private String placeholder;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 字典下拉可选项，仅 {@code controlType=3}（字典下拉）或 {@code controlType=5}（多选字典下拉）时内嵌。 */
    @Builder.Default
    @Schema(description = "字典下拉可选项，仅控件类型为字典下拉或多选字典下拉时内嵌")
    private List<FormFieldDictOptionVO> dictOptions = new ArrayList<>();
}
