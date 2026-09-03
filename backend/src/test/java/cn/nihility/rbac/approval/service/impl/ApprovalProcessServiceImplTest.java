package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ApprovalProcessServiceImpl} 单元测试：验证其作为薄封装正确构造命令对象并转调用
 * {@link WorkflowService}（workflow-approval-engine change design.md Decision 8）。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalProcessServiceImplTest {

    @Mock
    private WorkflowService workflowService;

    private ApprovalProcessServiceImpl service;

    /** 构造被测服务。 */
    @BeforeEach
    void setUp() {
        service = new ApprovalProcessServiceImpl(workflowService);
    }

    /** 启动流程时应使用固定的 MASTER_DATA_APPROVAL 流程编码，透传业务参数。 */
    @Test
    void start_shouldDelegateToWorkflowServiceWithMasterDataProcessCode() {
        WorkflowInstanceResult expected = new WorkflowInstanceResult(1L, "flowable-1", "deptLeaderApprove", "部门负责人审批");
        when(workflowService.start(any())).thenReturn(expected);

        WorkflowInstanceResult result = service.start(10L, "ORG", 1L, 100L);

        ArgumentCaptor<StartProcessCommand> captor = ArgumentCaptor.forClass(StartProcessCommand.class);
        verify(workflowService).start(captor.capture());
        StartProcessCommand command = captor.getValue();
        assertThat(command.processCode()).isEqualTo("MASTER_DATA_APPROVAL");
        assertThat(command.businessType()).isEqualTo("ORG");
        assertThat(command.businessId()).isEqualTo(10L);
        assertThat(command.applicantId()).isEqualTo(1L);
        assertThat(command.applicantOrgId()).isEqualTo(100L);
        assertThat(result).isSameAs(expected);
    }

    /** 审批通过应透传任务 id、操作人与意见。 */
    @Test
    void approve_shouldDelegateApproveCommand() {
        service.approve(5L, 2L, "同意");

        ArgumentCaptor<ApproveCommand> captor = ArgumentCaptor.forClass(ApproveCommand.class);
        verify(workflowService).approve(captor.capture());
        assertThat(captor.getValue().taskId()).isEqualTo(5L);
        assertThat(captor.getValue().operatorId()).isEqualTo(2L);
        assertThat(captor.getValue().remark()).isEqualTo("同意");
    }

    /** 审批拒绝应透传任务 id、操作人与拒绝原因。 */
    @Test
    void reject_shouldDelegateRejectCommand() {
        service.reject(5L, 2L, "拒绝原因");

        ArgumentCaptor<RejectCommand> captor = ArgumentCaptor.forClass(RejectCommand.class);
        verify(workflowService).reject(captor.capture());
        assertThat(captor.getValue().taskId()).isEqualTo(5L);
        assertThat(captor.getValue().operatorId()).isEqualTo(2L);
        assertThat(captor.getValue().remark()).isEqualTo("拒绝原因");
    }

    /** 撤回应透传流程实例 id 与操作人。 */
    @Test
    void withdraw_shouldDelegateWithdrawCommand() {
        service.withdraw(9L, 1L);

        ArgumentCaptor<WithdrawCommand> captor = ArgumentCaptor.forClass(WithdrawCommand.class);
        verify(workflowService).withdraw(captor.capture());
        assertThat(captor.getValue().processInstanceId()).isEqualTo(9L);
        assertThat(captor.getValue().operatorId()).isEqualTo(1L);
    }
}
