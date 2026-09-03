package cn.nihility.rbac.workflow.policy;

/**
 * 撤回策略抽象（workflow-approval-engine change design.md Decision 7）。
 */
public interface WithdrawPolicy {

    /**
     * 判断给定流程实例当前是否允许被撤回。
     *
     * @param processInstanceId 流程实例 id（{@code tab_wf_process_instance.id}）
     * @param operatorId        发起撤回的用户 id
     * @return 是否允许撤回
     */
    boolean canWithdraw(Long processInstanceId, Long operatorId);
}
