package cn.nihility.rbac.approval.service.impl;

import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.workflow.dslv2.binding.ProcessBindingResolutionService;
import cn.nihility.rbac.workflow.dslv2.binding.ResolvedProcessBinding;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于通用审批引擎 {@link WorkflowService} 的主数据审批流程操作实现，业务代码之外不再直接
 * 依赖 Flowable 的 {@code RuntimeService}/{@code TaskService}（workflow-approval-engine change
 * design.md Decision 8）。{@link #start} 不再硬编码固定的
 * {@code MASTER_DATA_APPROVAL_PROCESS_CODE}，改为经 {@link ProcessBindingResolutionService}
 * 按 {@code (bizType, operationType, applicantOrgId)} 解析实际生效的业务绑定
 * （production-approval-lifecycle change design.md Decision 4，tasks.md 4.5）。
 */
@Service
@RequiredArgsConstructor
public class ApprovalProcessServiceImpl implements ApprovalProcessService {

    /** 通用审批引擎接口。 */
    private final WorkflowService workflowService;

    /** 业务绑定解析服务，负责加锁解析绑定并校验模型/绑定/执行模式是否允许发起。 */
    private final ProcessBindingResolutionService processBindingResolutionService;

    /**
     * {@inheritDoc}
     * <p>
     * 绑定解析（含 {@code SELECT ... FOR UPDATE} 行锁）与流程实例创建须处于同一事务边界，
     * 本方法显式声明 {@code @Transactional}，与
     * {@link ProcessBindingResolutionService#resolveForStart}、
     * {@link WorkflowService#start} 各自的 {@code Propagation.REQUIRED} 共同保证三步在同一
     * 事务内完成（design.md Decision 4"启动时事务内读取并锁定所选绑定...再按 Flowable
     * definitionId 启动"）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public WorkflowInstanceResult start(
            Long requestId, String bizType, String operationType, Long applicantId, Long applicantOrgId) {
        ResolvedProcessBinding resolved = processBindingResolutionService.resolveForStart(
                bizType, operationType, applicantOrgId);
        return workflowService.start(new StartProcessCommand(
                resolved.definition().getProcessCode(),
                bizType,
                requestId,
                "主数据变更审批申请#" + requestId,
                applicantId,
                applicantOrgId,
                null,
                null,
                resolved.definition().getId(),
                resolved.binding().getId(),
                resolved.binding().getRevision(),
                resolved.binding().getExecutionMode()));
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
