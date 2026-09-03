package cn.nihility.rbac.workflow.constant;

/**
 * 节点审批人规则的审批人来源类型，对应 {@code tab_wf_node_assignee_rule.assignee_type}。
 */
public enum AssigneeType {

    /** 指定人员，{@code assignee_value} 为用户 id（多个以逗号分隔）。 */
    USER,

    /** 指定角色，{@code assignee_value} 为角色编码，解析为持有该角色的启用管理员。 */
    ROLE,

    /** 指定岗位，当前无岗位数据源，解析结果恒为空。 */
    POSITION,

    /** 指定组织负责人，{@code assignee_value} 为要求的管理员角色编码；当前实现按发起人所属
     *  组织解析（详见 {@code OrgLeaderAssigneeResolver} 类注释的假设说明）。 */
    ORG_LEADER,

    /** 发起人部门负责人，{@code assignee_value} 为要求的管理员角色编码。 */
    APPLICANT_DEPT_LEADER,

    /** 发起人部门上级负责人，沿组织路径向上查找第一个能解析出负责人的上级组织。 */
    APPLICANT_DEPT_PARENT_LEADER,

    /** 流程发起人本人。 */
    INITIATOR,

    /** 上一节点审批人。 */
    PREVIOUS_APPROVER
}
