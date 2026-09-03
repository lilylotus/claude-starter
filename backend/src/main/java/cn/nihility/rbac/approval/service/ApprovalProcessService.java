package cn.nihility.rbac.approval.service;

import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;

/**
 * 主数据变更审批流程操作的薄封装，内部全部委托给通用审批引擎
 * {@link cn.nihility.rbac.workflow.engine.WorkflowService}，隔离审批业务与流程引擎实现细节
 * （workflow-approval-engine change design.md Decision 8）。
 */
public interface ApprovalProcessService {

    /**
     * 启动主数据审批流程。
     *
     * @param requestId      审批申请 id，作为流程实例的业务对象 id
     * @param bizType        业务对象类型：ORG/USER/POSITION/APP
     * @param applicantId    发起人用户 id
     * @param applicantOrgId 发起人所属组织 id，解析不到时传 {@code null}
     * @return 流程启动结果，含流程实例 id 与当前所在节点信息
     */
    WorkflowInstanceResult start(Long requestId, String bizType, Long applicantId, Long applicantOrgId);

    /**
     * 审批通过当前节点任务。
     *
     * @param taskId     审批任务 id（{@code tab_wf_approval_task.id}）
     * @param approverId 审批人用户 id
     * @param opinion    审批意见，可为空
     */
    void approve(Long taskId, Long approverId, String opinion);

    /**
     * 驳回当前节点任务，直接终止流程实例。
     *
     * @param taskId     审批任务 id（{@code tab_wf_approval_task.id}）
     * @param approverId 审批人用户 id
     * @param opinion    拒绝意见
     */
    void reject(Long taskId, Long approverId, String opinion);

    /**
     * 撤回尚未产生任何一级审批记录的流程实例。
     *
     * @param processInstanceId 流程实例 id（{@code tab_wf_process_instance.id}）
     * @param operatorId        操作人用户 id，须为流程发起人
     */
    void withdraw(Long processInstanceId, Long operatorId);
}
