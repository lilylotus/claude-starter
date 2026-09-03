package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 审批人/抄送接收人来源类型，在 v1 {@code AssigneeType} 基础上补齐
 * "应用管理员""表单引用人员"（design.md Decision 5）。数据来源缺失的类型
 * （如 {@code APP_ADMIN} 尚无应用管理员角色数据源）解析恒为空并按空人策略处理，
 * 发布校验会因"审批来源相关必填字段不存在"拒绝发布，不假定已有字段。
 */
public enum AssigneeTypeV2 {

    /** 指定人员，取值为用户 id。 */
    USER,

    /** 指定角色，取值为角色编码。 */
    ROLE,

    /** 指定岗位任职，取值为岗位编码。 */
    POSITION,

    /** 指定组织负责人，取值为组织负责人角色编码。 */
    ORG_LEADER,

    /** 申请人所属组织的负责人。 */
    APPLICANT_DEPT_LEADER,

    /** 申请人所属组织的上级组织负责人（本级无负责人时向上查找）。 */
    APPLICANT_DEPT_PARENT_LEADER,

    /** 应用管理员，当前无数据来源，解析结果恒为空。 */
    APP_ADMIN,

    /** 表单引用人员，取值为表单字段标识，取该字段填写的用户。 */
    FORM_REFERENCE_PERSON,

    /** 上一节点处理人；并行汇合后存在多个来源时必须显式指定 {@code sourceNodeId}。 */
    PREVIOUS_APPROVER,

    /** 流程发起人。 */
    INITIATOR
}
