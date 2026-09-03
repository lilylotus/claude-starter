package cn.nihility.rbac.workflow.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 条件节点出边携带的分支条件：字段 + 比较符 + 比较值，比较符限定为
 * {@code EQ}/{@code NE}/{@code GT}/{@code GTE}/{@code LT}/{@code LTE} 白名单，不允许使用者
 * 直接输入自由表达式字符串，避免表达式注入（workflow-approval-engine change design.md
 * Decision 9）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeConditionDsl {

    /** 参与比较的流程启动变量字段名。 */
    private String field;

    /** 比较符字面量，取值限定为 {@code EQ}/{@code NE}/{@code GT}/{@code GTE}/{@code LT}/
     *  {@code LTE}。 */
    private String operator;

    /** 比较值，仅接受字符串/数字/布尔字面量。 */
    private Object value;
}
