package cn.nihility.rbac.workflow.designer.dto;

/**
 * "条件"节点，不携带额外属性；分支信息由该节点的出边（{@link EdgeDsl#getCondition()}）表达，
 * 多条出边中必须至少有一条不携带 {@code condition} 作为兜底默认分支（workflow-approval-engine
 * change design.md Decision 9）。
 */
public class ConditionNodeDsl extends ProcessNodeDsl {
}
