package cn.nihility.rbac.workflow.dslv2.dto;

import cn.nihility.rbac.workflow.dslv2.constant.ConditionOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 单条条件项：字段 + 比较符 + 比较值。字段来自版本化表单白名单，按字段类型限定运算符
 * （design.md Decision 3）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionItemDsl {

    /** 参与比较的字段标识，须存在于绑定的表单版本字段白名单内。 */
    private String field;

    /** 比较符。 */
    private ConditionOperator op;

    /** 比较值：字符串不隐式转数字；{@code IN} 接受数组；{@code IS_NULL} 忽略本字段。 */
    private Object value;
}
