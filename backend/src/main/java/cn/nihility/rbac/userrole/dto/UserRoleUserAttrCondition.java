package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户属性条件，形状对齐
 * {@code cn.nihility.rbac.appaccess.policy.dto.PolicyUserAttrRequestItem}，但独立成类。
 * 既作为用户角色规则新增/编辑/预览接口的请求项，也是
 * {@code UserMatchConditionResolver#resolve} 的输入类型——规则条件持久化在
 * {@code tab_user_role_rule_user_attr}，执行引擎读取后转换为本类型再调用条件匹配组件
 * （add-user-role-batch-assignment change design.md Decision 1/2，二次设计版本）。
 * {@code metadataFieldId} 关联的元数据字段须为启用状态，且 {@code bizType} 属于
 * {@code USER}（用户主表字段）或 {@code POSITION}（任职记录字段）之一，否则拒绝请求；
 * {@code operator} 仅支持 {@code EQ}/{@code NE}/{@code IN} 三种取值，{@code attrValue}
 * 在 {@code IN} 时为逗号分隔的多个值；多条属性条件之间取交集。
 */
@Getter
@Setter
@Schema(description = "用户角色批量添加的用户属性条件")
public class UserRoleUserAttrCondition {

    /** 关联的元数据字段 id（{@code bizType} 须为 {@code USER} 或 {@code POSITION}），必填。 */
    @NotNull(message = "属性字段不能为空")
    @Schema(description = "元数据字段 id，取值范围覆盖 bizType=USER 与 bizType=POSITION 两类字段")
    private Long metadataFieldId;

    /** 运算符：EQ/NE/IN，必填。 */
    @NotBlank(message = "运算符不能为空")
    @Schema(description = "运算符：EQ=等于，NE=不等于，IN=属于多值")
    private String operator;

    /** 比较值：EQ/NE 为单个值，IN 为逗号分隔的多个值。 */
    @NotBlank(message = "比较值不能为空")
    @Schema(description = "比较值：EQ/NE 为单个值，IN 为逗号分隔的多个值")
    private String attrValue;
}
