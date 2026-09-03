package cn.nihility.rbac.workflow.constant;

/**
 * 候选人明细类型，对应 {@code tab_wf_approval_task.candidate_type} 与
 * {@code tab_wf_approval_task_candidate.candidate_type}。
 */
public final class CandidateType {

    /** 候选人为具体用户，取值为用户 id 文本。 */
    public static final String USER = "USER";

    /** 候选人为角色，取值为角色编码。 */
    public static final String ROLE = "ROLE";

    /**
     * 工具类不允许实例化。
     */
    private CandidateType() {
    }
}
