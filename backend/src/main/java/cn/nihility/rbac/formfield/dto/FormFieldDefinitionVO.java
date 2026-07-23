package cn.nihility.rbac.formfield.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 表单字段定义详情/列表行视图对象。{@code columnName}/{@code locked} 并非该实体的
 * 持久化列，而是根据绑定的元数据字段解析/计算得出，供调用方（含四个业务模块的
 * 动态校验管线）直接使用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表单字段定义详情")
public class FormFieldDefinitionVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 业务对象类型：ORG/USER/POSITION/APP。 */
    @Schema(description = "业务对象类型：ORG/USER/POSITION/APP")
    private String bizType;

    /** 绑定的元数据字段 id。 */
    @Schema(description = "绑定的元数据字段 id")
    private Long metadataFieldId;

    /** 绑定的元数据字段列名，解析自 {@code metadataFieldId}，供动态校验/渲染使用。 */
    @Schema(description = "绑定的元数据字段列名")
    private String columnName;

    /** 展示名称。 */
    @Schema(description = "展示名称")
    private String fieldName;

    /** 前端/DTO 使用的字段标识。 */
    @Schema(description = "前端/DTO 使用的字段标识")
    private String fieldCode;

    /** 控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉。 */
    @Schema(description = "控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉")
    private Integer controlType;

    /** 关联的字典类型编码。 */
    @Schema(description = "关联的字典类型编码")
    private String dictTypeCode;

    /** 关联的字典类型名称，供前端展示。 */
    @Schema(description = "关联的字典类型名称")
    private String dictTypeName;

    /** 是否要求同 bizType 下有效数据唯一。 */
    @Schema(description = "是否要求同业务对象类型下有效数据唯一")
    private Boolean isUnique;

    /** 是否必填。 */
    @Schema(description = "是否必填")
    private Boolean isRequired;

    /** 是否在列表中展示。 */
    @Schema(description = "是否在列表中展示")
    private Boolean showInList;

    /** 是否在新增表单中展示。 */
    @Schema(description = "是否在新增表单中展示")
    private Boolean showInCreate;

    /** 是否在编辑表单中展示。 */
    @Schema(description = "是否在编辑表单中展示")
    private Boolean showInEdit;

    /** 表单中展示时是否可编辑。 */
    @Schema(description = "表单中展示时是否可编辑")
    private Boolean editable;

    /** 正则校验规则。 */
    @Schema(description = "正则校验规则")
    private String validateRegex;

    /** 输入提示文字。 */
    @Schema(description = "输入提示文字")
    private String placeholder;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /**
     * 是否为承重字段（锁定字段），根据绑定的元数据字段是否命中
     * {@code cn.nihility.rbac.formfield.constant.LockedFormFields} 白名单计算得出，
     * 不落库。锁定的定义不可停用/删除，也不可把必填/新增或编辑表单展示改为否。
     */
    @Schema(description = "是否为承重字段（锁定字段），计算得出，不可停用/删除/放松必填与展示配置")
    private Boolean locked;

    /** 创建人。 */
    @Schema(description = "创建人")
    private String createBy;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新人。 */
    @Schema(description = "更新人")
    private String updateBy;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
