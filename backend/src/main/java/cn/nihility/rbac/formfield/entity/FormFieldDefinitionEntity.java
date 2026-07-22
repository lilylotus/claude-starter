package cn.nihility.rbac.formfield.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 表单字段定义持久化实体，对应表 {@code tab_form_field_definition}。在元数据字段
 * 配置目录的基础上，绑定 {@code metadataFieldId} 并配上业务侧关心的展示/校验属性，
 * 驱动组织/用户/任职/应用四个管理页面的动态渲染。{@code metadataFieldId}/
 * {@code bizType} 一经创建不可修改；是否为承重字段（"锁定"）不落库，由
 * {@code cn.nihility.rbac.formfield.constant.LockedFormFields} 在读取时计算得出。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_form_field_definition")
public class FormFieldDefinitionEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务对象类型，创建时取自所绑定元数据字段，之后不可变。 */
    private String bizType;

    /** 绑定的元数据字段 id，关联 {@code tab_metadata_field.id}，创建后不可改绑。 */
    private Long metadataFieldId;

    /** 展示名称，创建时默认取自元数据字段的 fieldName，此后可独立编辑。 */
    private String fieldName;

    /** 前端/DTO 使用的字段标识，如 idCardNo，同一 bizType 下唯一。 */
    private String fieldCode;

    /** 控件类型：1=文本框，2=数字框，3=字典下拉。 */
    private Integer controlType;

    /**
     * 关联的字典类型 id，仅 controlType=3 时必填；控件类型切换为非字典下拉时需要
     * 显式写入 {@code NULL}，MyBatis-Plus 默认的 {@code NOT_NULL} 更新策略会跳过
     * null 值字段，故用 {@link FieldStrategy#ALWAYS} 覆盖，确保 null 也能写入。
     */
    @TableField(value = "dict_type_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long dictTypeId;

    /** 是否要求同 bizType 下有效数据唯一。 */
    private Boolean isUnique;

    /** 是否必填。 */
    private Boolean isRequired;

    /** 是否在列表中展示。 */
    private Boolean showInList;

    /** 是否在新增表单中展示。 */
    private Boolean showInCreate;

    /** 是否在编辑表单中展示。 */
    private Boolean showInEdit;

    /** 表单中展示时是否可编辑，为否则只读展示。 */
    private Boolean editable;

    /** 正则校验规则，前后端共用同一个字符串。 */
    private String validateRegex;

    /** 输入提示文字。 */
    private String placeholder;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）。 */
    private Integer status;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
