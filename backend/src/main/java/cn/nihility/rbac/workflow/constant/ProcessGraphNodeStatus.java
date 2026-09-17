package cn.nihility.rbac.workflow.constant;

/**
 * 流程实例详情"完整节点/连线图"里单个节点的三态状态字面量（add-approval-remark-and-process
 * -flowchart change design.md Decision 4）：按 {@code tab_wf_approval_record.node_id} 是否
 * 出现过、以及是否命中当前开放任务节点集合计算得出，不依赖对未评估条件分支的预测。
 */
public final class ProcessGraphNodeStatus {

    /** 已完成：该节点在审批轨迹中出现过至少一条记录。 */
    public static final String COMPLETED = "COMPLETED";

    /** 进行中：该节点当前存在开放任务（PENDING/CLAIMED），尚未出现在审批轨迹中。 */
    public static final String CURRENT = "CURRENT";

    /** 未到达：该节点既不在审批轨迹中，也没有当前开放任务。 */
    public static final String PENDING = "PENDING";

    /**
     * 工具类不允许实例化。
     */
    private ProcessGraphNodeStatus() {
    }
}
