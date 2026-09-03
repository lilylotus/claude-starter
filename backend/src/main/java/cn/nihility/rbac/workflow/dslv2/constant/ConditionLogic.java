package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 条件表达式组的逻辑连接符（production-approval-lifecycle change design.md
 * Decision 3）。
 */
public enum ConditionLogic {

    /** 且：组内全部条件项均满足才算命中。 */
    AND,

    /** 或：组内任一条件项满足即算命中。 */
    OR
}
