package cn.nihility.rbac.workflow.constant;

/**
 * Workflow 引擎抽象层通用常量。
 */
public final class WorkflowConstants {

    /** 空审批人策略 {@code TO_WORKFLOW_ADMIN} 兜底转交的角色编码：复用已有的超级管理员角色，
     *  保证任意环境下至少存在一个持有该角色的启用管理员，避免兜底再次落空
     *  （workflow-approval-engine change design.md Risks 一节）。 */
    public static final String WORKFLOW_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    /** {@code ORG_LEADER}/{@code APPLICANT_DEPT_LEADER}/{@code APPLICANT_DEPT_PARENT_LEADER}
     *  三类组织负责人审批人规则在 {@code assignee_value} 未配置时使用的默认管理员角色编码。 */
    public static final String DEFAULT_ORG_LEADER_ROLE_CODE = "DEPT_LEADER";

    /** 默认主数据审批流程的 Flowable 流程定义 key，保持与升级前单节点版本一致
     *  （workflow-approval-engine change design.md Decision 8）。 */
    public static final String MASTER_DATA_APPROVAL_PROCESS_KEY = "masterDataApprovalProcess";

    /** 默认主数据审批流程对应的业务侧流程编码（{@code tab_wf_process_model.process_code}）。 */
    public static final String MASTER_DATA_APPROVAL_PROCESS_CODE = "MASTER_DATA_APPROVAL";

    /**
     * 工具类不允许实例化。
     */
    private WorkflowConstants() {
    }
}
