package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.TerminateCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 运维强制终止流程实例（{@code WorkflowService#terminate}）对着真实 Flowable 引擎的集成测试
 * （production-approval-lifecycle change design.md 第7节"terminate：独立运维权限和必填原因，
 * 结束流程并取消任务，不执行主数据变更"，tasks.md 6.8）。权限点校验属于 HTTP 层
 * {@code IdentityAuthFilter} 的职责，见
 * {@code cn.nihility.rbac.auth.filter.IdentityAuthFilterTest} 内新增的固定权限映射回归用例；
 * 本类只聚焦引擎层 {@code doTerminate} 本身的状态流转正确性。
 */
class WorkflowTerminateIntegrationTest extends AbstractWorkflowEngineIntegrationTest {

    /** 转办/委派/退回测试流程资源路径：本类复用其三级单人固定审批人结构。 */
    private static final String TRANSFER_DELEGATE_RETURN_BPMN = "processes/test-transfer-delegate-return.bpmn20.xml";

    /**
     * 成功路径：对一个仍在运行、已有开放任务的流程实例发起终止，流程实例状态置为
     * {@code TERMINATED}、全部开放任务被取消且写入非空 {@code cancel_reason}、落一条
     * {@code TERMINATE} 审批轨迹，且真实 Flowable 运行时实例被结束（不再可查询到）。
     */
    @Test
    void terminate_shouldEndRunningInstanceAndCancelOpenTasksWithReason() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 909999L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        Long levelOneTaskId = singleTaskId(started.processInstanceId(), "levelOne");

        workflowService.terminate(new TerminateCommand(
                started.processInstanceId(), 900001L, "运维核实该流程数据异常，强制终止", null));

        ProcessInstanceEntity terminatedInstance = instanceOf(started.processInstanceId());
        assertThat(terminatedInstance.getStatus()).isEqualTo(ProcessInstanceStatus.TERMINATED);
        assertThat(terminatedInstance.getFinishedTime()).isNotNull();
        assertThat(terminatedInstance.getCurrentNodeId()).isNull();

        ApprovalTaskEntity cancelledTask = approvalTaskMapper.selectById(levelOneTaskId);
        assertThat(cancelledTask.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(cancelledTask.getCancelReason()).isEqualTo("运维核实该流程数据异常，强制终止");

        List<ApprovalRecordEntity> records = recordsOf(started.processInstanceId());
        assertThat(records).anyMatch(record -> ApprovalAction.TERMINATE.equals(record.getAction())
                && Long.valueOf(900001L).equals(record.getOperatorId())
                && "运维核实该流程数据异常，强制终止".equals(record.getRemark()));

        // 真实 Flowable 运行时实例已被结束，不再可查询到。
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(terminatedInstance.getFlowableInstanceId())
                .count()).isZero();

        // 不触发任何业务执行事件：终止后该任务不能再被 approve（引擎任务已不存在）。
        assertThatThrownBy(() -> workflowService.approve(new ApproveCommand(levelOneTaskId, 710001L, "同意", null)))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 对已结束（本例为已终止一次）的流程实例重复终止必须被拒绝，不能覆盖已有的终止状态/原因。
     */
    @Test
    void terminate_shouldBeRejected_whenInstanceAlreadyTerminated() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 919999L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        workflowService.terminate(new TerminateCommand(started.processInstanceId(), 900001L, "首次终止", null));

        assertThatThrownBy(() -> workflowService.terminate(
                new TerminateCommand(started.processInstanceId(), 900001L, "重复终止", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");

        // 重复终止被拒绝，原终止原因不应被覆盖。
        ApprovalTaskEntity task = singleTask(started.processInstanceId(), "levelOne");
        assertThat(task.getCancelReason()).isEqualTo("首次终止");
    }

    /**
     * 对已正常审批通过结束（{@code APPROVED}）的流程实例发起终止必须被拒绝——终止只适用于
     * 仍在运行中的流程，不能覆盖已有的正常终态。
     */
    @Test
    void terminate_shouldBeRejected_whenInstanceAlreadyApproved() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 929999L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        workflowService.approve(new ApproveCommand(singleTaskId(started.processInstanceId(), "levelOne"),
                710001L, "同意", null));
        workflowService.approve(new ApproveCommand(singleTaskId(started.processInstanceId(), "levelTwo"),
                710002L, "同意", null));
        workflowService.approve(new ApproveCommand(singleTaskId(started.processInstanceId(), "levelThree"),
                710003L, "同意", null));
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);

        assertThatThrownBy(() -> workflowService.terminate(
                new TerminateCommand(started.processInstanceId(), 900001L, "尝试终止已通过流程", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");

        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);
    }

    /**
     * 三级单人固定审批人节点种子数据（与 {@link TaskOperationsIntegrationTest} 保持一致的固定
     * 审批人取值，便于对照阅读）。
     */
    private List<NodeSeed> threeLevelNodeSeeds() {
        return List.of(
                new NodeSeed("levelOne", "第一级审批", ApprovalMode.SINGLE, null,
                        "710001", true, true, false, false),
                new NodeSeed("levelTwo", "第二级审批", ApprovalMode.SINGLE, null,
                        "710002", false, false, false, true),
                new NodeSeed("levelThree", "第三级审批", ApprovalMode.SINGLE, null,
                        "710003", false, false, false, false));
    }

    /**
     * 断言指定流程实例在指定节点下恰好存在一个任务，并返回其 id。
     */
    private Long singleTaskId(Long processInstanceId, String nodeId) {
        return singleTask(processInstanceId, nodeId).getId();
    }

    /**
     * 断言指定流程实例在指定节点下恰好存在一个任务，并返回该任务实体。
     */
    private ApprovalTaskEntity singleTask(Long processInstanceId, String nodeId) {
        List<ApprovalTaskEntity> tasks = tasksOf(processInstanceId, nodeId);
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }
}
