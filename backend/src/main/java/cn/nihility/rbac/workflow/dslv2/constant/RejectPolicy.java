package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 审批节点反对票处理策略（production-approval-lifecycle change design.md
 * Decision 7）。{@code DISAGREE}（反对票）与 {@code REJECT}（节点允许的终止拒绝）是两个
 * 不同的任务动作，不合并成一个按钮；本枚举只影响 {@code DISAGREE} 动作的计票语义，
 * {@code REJECT} 恒直接终止全流程，不受本策略影响。
 */
public enum RejectPolicy {

    /** 一票否决：出现任意一票反对（R&gt;0）即判定该节点未通过。 */
    VETO,

    /** 阈值制：同意票数达到阈值 K 即通过；同意票数 + 未处理票数 &lt; K 时判定未通过；
     *  其余情况继续等待。 */
    THRESHOLD
}
