package cn.nihility.rbac.approval.service.impl;

import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于通用审批引擎 {@link WorkflowService} 的主数据审批流程操作实现，业务代码之外不再直接
 * 依赖 Flowable 的 {@code RuntimeService}/{@code TaskService}（workflow-approval-engine change
 * design.md Decision 8）。
 */
@Service
@RequiredArgsConstructor
public class ApprovalProcessServiceImpl implements ApprovalProcessService {

    /** 主数据变更审批流程业务侧编码，关联 {@code tab_wf_process_model.process_code}。 */
    private static final String PROCESS_CODE = WorkflowConstants.MASTER_DATA_APPROVAL_PROCESS_CODE;

    /** 通用审批引擎接口。 */
    private final WorkflowService workflowService;

    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowInstanceResult start(Long requestId, String bizType, Long applicantId, Long applicantOrgId) {
        return workflowService.start(new StartProcessCommand(
                PROCESS_CODE,
                bizType,
                requestId,
                "主数据变更审批申请#" + requestId,
                applicantId,
                applicantOrgId,
                null,
                null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void approve(Long taskId, Long approverId, String opinion) {
        workflowService.approve(new ApproveCommand(taskId, approverId, opinion, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reject(Long taskId, Long approverId, String opinion) {
        workflowService.reject(new RejectCommand(taskId, approverId, opinion, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void withdraw(Long processInstanceId, Long operatorId) {
        workflowService.withdraw(new WithdrawCommand(processInstanceId, operatorId, null, null));
    }
}
