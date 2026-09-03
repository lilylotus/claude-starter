package cn.nihility.rbac.workflow.dslv2.dto;

/**
 * "条件"节点，不携带额外属性；分支信息由该节点的出边（{@link EdgeDslV2#getCondition()}）
 * 表达，多条出边按 {@code priority} 从小到大取第一个命中的分支，必须至少有一条不携带
 * {@code condition} 作为兜底默认分支且 {@code priority} 最大（design.md Decision 3）。
 */
public class ConditionNodeDslV2 extends ProcessNodeDslV2 {
}
