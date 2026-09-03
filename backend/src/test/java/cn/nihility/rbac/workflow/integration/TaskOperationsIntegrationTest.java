package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.AddSignCommand;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.DelegateCommand;
import cn.nihility.rbac.workflow.dto.ReturnTaskCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.TransferCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import java.util.Comparator;
import java.util.List;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

/**
 * 转办（Transfer）/委派（Delegate）/加签（AddSign）/退回（Return）四个任务处理操作对着真实
 * Flowable 引擎的端到端集成测试（workflow-approval-engine change tasks.md 13.3）。逐条对应
 * specs/workflow-approval-engine/spec.md "转办、委派与加签"以及"Reject 与 Return 语义区分"
 * 两个 Requirement 下与本类相关的全部 Scenario。
 * <p>
 * 转办/委派/退回场景使用 {@code test-transfer-delegate-return.bpmn20.xml}（三级单人固定审批人
 * 串行流程）；加签场景使用 {@code test-multi-instance-and.bpmn20.xml}（会签节点），因为加签
 * 只对会签节点有意义。
 */
class TaskOperationsIntegrationTest extends AbstractWorkflowEngineIntegrationTest {

    /** 转办/委派/退回测试流程资源路径。 */
    private static final String TRANSFER_DELEGATE_RETURN_BPMN = "processes/test-transfer-delegate-return.bpmn20.xml";

    /**
     * spec.md Scenario"转办变更处理人"：审批人对配置 {@code allow_transfer=true} 的当前任务
     * 执行转办操作，任务处理人变更为新处理人，审批记录表记录原处理人与新处理人。
     */
    @Test
    void transfer_shouldChangeAssigneeAndRecordFromAndToUser() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 809999L, null, null, null));

        Long taskId = singleTaskId(started.processInstanceId(), "levelOne");
        workflowService.transfer(new TransferCommand(taskId, 710001L, 719000L, "临时出差，转办处理", null));

        ApprovalTaskEntity afterTransfer = approvalTaskMapper.selectById(taskId);
        assertThat(afterTransfer.getAssigneeId()).isEqualTo(719000L);

        Task flowableTask = taskService.createTaskQuery().taskId(afterTransfer.getFlowableTaskId()).singleResult();
        assertThat(flowableTask.getAssignee()).isEqualTo("719000");

        assertThat(recordsOf(started.processInstanceId())).anyMatch(record ->
                ApprovalAction.TRANSFER.equals(record.getAction())
                        && Long.valueOf(710001L).equals(record.getFromUserId())
                        && Long.valueOf(719000L).equals(record.getToUserId()));

        // 转办后新处理人应能正常审批推进流程
        workflowService.approve(new ApproveCommand(taskId, 719000L, "同意", null));
        assertThat(tasksOf(started.processInstanceId(), "levelTwo")).hasSize(1);
    }

    /**
     * spec.md Scenario"转办不允许的节点被拒绝"：审批人对未配置 {@code allow_transfer=true}
     * 的节点尝试转办，系统拒绝该次操作，返回业务错误。
     */
    @Test
    void transfer_shouldBeRejected_whenTargetNodeDoesNotAllowTransfer() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 819999L, null, null, null));

        Long levelOneTaskId = singleTaskId(started.processInstanceId(), "levelOne");
        workflowService.approve(new ApproveCommand(levelOneTaskId, 710001L, "同意", null));
        Long levelTwoTaskId = singleTaskId(started.processInstanceId(), "levelTwo");

        assertThatThrownBy(() -> workflowService.transfer(
                new TransferCommand(levelTwoTaskId, 710002L, 819000L, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许转办");

        // 操作被拒绝，任务状态不变
        ApprovalTaskEntity levelTwoTask = approvalTaskMapper.selectById(levelTwoTaskId);
        assertThat(levelTwoTask.getAssigneeId()).isEqualTo(710002L);
        assertThat(levelTwoTask.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    /**
     * spec.md"委派"语义：受托人处理完成后归还原处理人（Flowable 原生委派语义），归还后仍需
     * 原处理人再次调用审批接口才能真正驱动流程往下走。
     */
    @Test
    void delegate_shouldReturnToOriginalAssignee_afterDelegateCompletesTask() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 829999L, null, null, null));

        Long taskId = singleTaskId(started.processInstanceId(), "levelOne");
        workflowService.delegate(new DelegateCommand(taskId, 710001L, 718001L, "外出学习，委托代为审批", null));

        Task delegatedTask = taskService.createTaskQuery().taskId(
                approvalTaskMapper.selectById(taskId).getFlowableTaskId()).singleResult();
        assertThat(delegatedTask.getDelegationState()).isEqualTo(DelegationState.PENDING);
        assertThat(delegatedTask.getAssignee()).isEqualTo("718001");
        assertThat(delegatedTask.getOwner()).isEqualTo("710001");
        assertThat(approvalTaskMapper.selectById(taskId).getAssigneeId()).isEqualTo(718001L);
        assertThat(recordsOf(started.processInstanceId())).anyMatch(record ->
                ApprovalAction.DELEGATE.equals(record.getAction())
                        && Long.valueOf(710001L).equals(record.getFromUserId())
                        && Long.valueOf(718001L).equals(record.getToUserId()));

        // 受托人完成任务：仅归还给原处理人，不驱动流程往下走
        workflowService.approve(new ApproveCommand(taskId, 718001L, "受托人已审阅，同意", null));
        assertThat(approvalTaskMapper.selectById(taskId).getAssigneeId()).isEqualTo(710001L);
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);
        assertThat(tasksOf(started.processInstanceId(), "levelTwo")).isEmpty();

        // 原处理人最终确认，流程才真正推进到下一节点
        workflowService.approve(new ApproveCommand(taskId, 710001L, "确认通过", null));
        assertThat(tasksOf(started.processInstanceId(), "levelTwo")).hasSize(1);
    }

    /**
     * spec.md Scenario"会签节点加签"：审批人对配置 {@code allow_add_sign=true} 的会签节点
     * 执行加签操作，新增一个待处理的审批任务分支，原有候选人的任务与完成条件判定不受影响。
     */
    @Test
    void addSign_shouldAddNewPendingBranch_withoutAffectingExistingCandidates() {
        ProcessFixture fixture = deployAndSeed("processes/test-multi-instance-and.bpmn20.xml",
                List.of(new NodeSeed("miApprove", "会签审批(AND，可加签)", ApprovalMode.AND, null,
                        "830001,830002", false, false, true, false)));
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 839999L, null, null, null));

        List<ApprovalTaskEntity> beforeAddSign = tasksOf(started.processInstanceId(), "miApprove");
        assertThat(beforeAddSign).hasSize(2);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(2);

        Long anyExistingTaskId = beforeAddSign.get(0).getId();
        workflowService.addSign(new AddSignCommand(anyExistingTaskId, 830001L, List.of(830003L), "追加审批人", null));

        // 新增了一个待处理的审批任务分支：Flowable 引擎层面活动任务数由 2 增至 3
        // （即 nrOfInstances 由 2 增至 3，通过 TaskService 这一 Flowable API 间接验证，
        // 不直接查询 ACT_RU_EXECUTION 表）
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(3);

        List<ApprovalTaskEntity> afterAddSign = tasksOf(started.processInstanceId(), "miApprove");
        assertThat(afterAddSign).hasSize(3);
        // 原有两个候选人的任务不受影响：同一个 flowableTaskId、仍为 PENDING
        for (ApprovalTaskEntity before : beforeAddSign) {
            ApprovalTaskEntity stillPending = afterAddSign.stream()
                    .filter(task -> task.getId().equals(before.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(stillPending.getFlowableTaskId()).isEqualTo(before.getFlowableTaskId());
            assertThat(stillPending.getStatus()).isEqualTo(TaskStatus.PENDING);
        }
        ApprovalTaskEntity newTask = afterAddSign.stream()
                .filter(task -> Long.valueOf(830003L).equals(task.getAssigneeId()))
                .findFirst()
                .orElseThrow();
        assertThat(newTask.getStatus()).isEqualTo(TaskStatus.PENDING);

        assertThat(recordsOf(started.processInstanceId())).anyMatch(record ->
                ApprovalAction.ADD_SIGN.equals(record.getAction())
                        && Long.valueOf(830001L).equals(record.getOperatorId()));

        // 完成条件判定不受影响：仍需全部（含新增的）候选人通过才算完成
        workflowService.approve(new ApproveCommand(beforeAddSign.get(0).getId(), 830001L, "同意", null));
        workflowService.approve(new ApproveCommand(beforeAddSign.get(1).getId(), 830002L, "同意", null));
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);
        workflowService.approve(new ApproveCommand(newTask.getId(), 830003L, "同意", null));
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);
    }

    /**
     * spec.md Scenario"退回历史节点重新解析审批人"：审批人对当前任务执行退回操作，目标为
     * 已配置 {@code allow_return=true} 的历史节点，流程状态回到目标节点，该节点重新解析
     * 审批人（不复用退回前遗留的审批人信息，本测试用一个全新的 {@code tab_wf_approval_task}
     * 行体现"重新解析"，而不是复用退回前已完成的旧行）。
     */
    @Test
    void returnTask_shouldMoveBackToHistoryNodeAndReResolveAssignee() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 849999L, null, null, null));

        Long levelOneTaskId = singleTaskId(started.processInstanceId(), "levelOne");
        workflowService.approve(new ApproveCommand(levelOneTaskId, 710001L, "同意", null));
        Long originalLevelTwoTaskId = singleTaskId(started.processInstanceId(), "levelTwo");
        workflowService.approve(new ApproveCommand(originalLevelTwoTaskId, 710002L, "同意", null));
        Long levelThreeTaskId = singleTaskId(started.processInstanceId(), "levelThree");

        workflowService.returnTask(new ReturnTaskCommand(levelThreeTaskId, 710003L, "levelTwo", "材料不全，退回二级重新审核", null));

        ApprovalTaskEntity levelThreeAfterReturn = approvalTaskMapper.selectById(levelThreeTaskId);
        assertThat(levelThreeAfterReturn.getStatus()).isEqualTo(TaskStatus.RETURNED);

        List<ApprovalTaskEntity> levelTwoTasksAfterReturn = tasksOf(started.processInstanceId(), "levelTwo");
        assertThat(levelTwoTasksAfterReturn).hasSize(2);
        ApprovalTaskEntity newLevelTwoTask = levelTwoTasksAfterReturn.stream()
                .max(Comparator.comparing(ApprovalTaskEntity::getId))
                .orElseThrow();
        assertThat(newLevelTwoTask.getId()).isNotEqualTo(originalLevelTwoTaskId);
        assertThat(newLevelTwoTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(newLevelTwoTask.getAssigneeId()).isEqualTo(710002L);

        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId())
                .taskDefinitionKey("levelTwo").count()).isEqualTo(1);

        List<ApprovalRecordEntity> records = recordsOf(started.processInstanceId());
        assertThat(records).anyMatch(record -> ApprovalAction.RETURN.equals(record.getAction())
                && Long.valueOf(710003L).equals(record.getOperatorId()));
    }

    /**
     * spec.md Scenario"退回不允许的节点被拒绝"：审批人尝试退回到未配置
     * {@code allow_return=true} 的节点，系统拒绝该次操作，返回业务错误，流程状态不变。
     */
    @Test
    void returnTask_shouldBeRejected_whenTargetNodeDoesNotAllowReturn() {
        ProcessFixture fixture = deployAndSeed(TRANSFER_DELEGATE_RETURN_BPMN, threeLevelNodeSeeds());
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 859999L, null, null, null));

        Long levelOneTaskId = singleTaskId(started.processInstanceId(), "levelOne");
        workflowService.approve(new ApproveCommand(levelOneTaskId, 710001L, "同意", null));
        Long levelTwoTaskId = singleTaskId(started.processInstanceId(), "levelTwo");

        // levelOne 未配置 allow_return=true，退回到 levelOne 应被拒绝
        assertThatThrownBy(() -> workflowService.returnTask(
                new ReturnTaskCommand(levelTwoTaskId, 710002L, "levelOne", "尝试退回一级", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许退回");

        ApprovalTaskEntity levelTwoTask = approvalTaskMapper.selectById(levelTwoTaskId);
        assertThat(levelTwoTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(tasksOf(started.processInstanceId(), "levelOne"))
                .noneMatch(task -> TaskStatus.PENDING.equals(task.getStatus()));
    }

    /**
     * 三级单人固定审批人节点种子数据：levelOne 允许转办/委派，不允许退回；levelTwo 不允许
     * 转办/委派，允许退回；levelThree 均不允许（见测试 BPMN 资源文件头注释的角色分工说明）。
     */
    private List<NodeSeed> threeLevelNodeSeeds() {
        return List.of(
                new NodeSeed("levelOne", "第一级审批", ApprovalMode.SINGLE, null, "710001",
                        true, true, false, false),
                new NodeSeed("levelTwo", "第二级审批", ApprovalMode.SINGLE, null, "710002",
                        false, false, false, true),
                new NodeSeed("levelThree", "第三级审批", ApprovalMode.SINGLE, null, "710003",
                        false, false, false, false));
    }

    /**
     * 断言指定流程实例在指定节点下恰好存在一个待处理任务，并返回其 id。
     */
    private Long singleTaskId(Long processInstanceId, String nodeId) {
        List<ApprovalTaskEntity> tasks = tasksOf(processInstanceId, nodeId);
        assertThat(tasks).hasSize(1);
        return tasks.get(0).getId();
    }
}
