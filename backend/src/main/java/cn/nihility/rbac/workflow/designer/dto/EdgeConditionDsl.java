package cn.nihility.rbac.workflow.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 条件节点出边携带的分支条件：业务对象类型 + 字段（fieldCode） + 比较符 + 比较值。字段
 * SHALL 引用组织/用户/任职/应用四类业务对象已启用的表单字段定义（不允许自由文本），比较符
 * 限定为 {@code EQ}/{@code NE}/{@code GT}/{@code GTE}/{@code LT}/{@code LTE} 白名单，不允许
 * 使用者直接输入自由表达式字符串，避免表达式注入（workflow-approval-engine change design.md
 * Decision 9）。运行时按提交审批时的表单字段值取值路由，而不是名不副实的"流程启动变量"
 * （workflow-condition-payload-fields change design.md Decision 2）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeConditionDsl {

    /** 字段所属业务对象类型：{@code ORG}/{@code USER}/{@code POSITION}/{@code APP}。 */
    private String fieldBizType;

    /** 参与比较的字段标识（fieldCode），须存在于 {@code fieldBizType} 对应的启用表单字段
     *  定义中，且控件类型不能是多选字典（{@code controlType=5}）。 */
    private String field;

    /** 比较符字面量，取值限定为 {@code EQ}/{@code NE}/{@code GT}/{@code GTE}/{@code LT}/
     *  {@code LTE}。 */
    private String operator;

    /** 比较值，仅接受字符串/数字/布尔字面量。 */
    private Object value;
}
