package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 条件比较符白名单，按字段类型限定可用运算符（production-approval-lifecycle
 * change design.md Decision 3）：字符串支持 {@code EQ}/{@code NE}/{@code IN}/
 * {@code IS_NULL}；数值/日期额外支持 {@code GT}/{@code GE}/{@code LT}/{@code LE}；
 * 字符串不隐式转数字。
 */
public enum ConditionOperator {

    /** 等于。 */
    EQ,

    /** 不等于。 */
    NE,

    /** 大于（数值/日期字段）。 */
    GT,

    /** 大于等于（数值/日期字段）。 */
    GE,

    /** 小于（数值/日期字段）。 */
    LT,

    /** 小于等于（数值/日期字段）。 */
    LE,

    /** 属于给定集合。 */
    IN,

    /** 为空。 */
    IS_NULL
}
