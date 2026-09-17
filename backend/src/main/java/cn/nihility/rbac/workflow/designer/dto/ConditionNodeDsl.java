package cn.nihility.rbac.workflow.designer.dto;

/**
 * "条件"节点，不携带额外属性；分支信息由该节点的出边（{@link EdgeDsl#getCondition()}）表达，
 * 没有不携带 {@code condition} 的默认出边时，编译器自动补全直达结束的兜底分支（
 * workflow-condition-auto-default-branch change design.md Decision 1）。
 */
public class ConditionNodeDsl extends ProcessNodeDsl {
}
