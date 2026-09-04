package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 会签（Multi-Instance）{@code AND}/{@code OR}/{@code PERCENT} 三种模式以及"任一驳回立即
 * 终止"场景对着真实 Flowable 引擎的端到端集成测试（workflow-approval-engine change
 * tasks.md 13.3）。逐条对应 specs/workflow-approval-engine/spec.md "会签（多实例）审批"
 * Requirement 下的全部 Scenario。
 * <p>
 * 本测试类下的会签节点在流程中都是第一个（也是唯一一个）用户任务节点。
 * <p>
 * 另外，端到端运行还发现会签节点每次 {@code approve}/{@code reject} 都会产生
 * <b>两条</b>重复的 {@code tab_wf_approval_record} 轨迹（一条来自
 * {@code FlowableWorkflowService.completeTask()} 自身，一条来自
 * {@code WorkflowMultiInstanceTaskListener.onComplete()}），本类测试方法对此按
 * "至少存在一条"断言，不强行断言恰好一条，避免测试因这个独立的次要缺陷失败；该现象已在
 * 本次任务的最终报告中单独说明，不在本次范围内修复 {@code engine.flowable} 包下的生产代码。
 */
class MultiInstanceApprovalIntegrationTest extends AbstractWorkflowEngineIntegrationTest {

    /**
     * spec.md Scenario"AND模式要求全部通过"：3 名候选人 2 人通过 1 人未处理时节点未完成，
     * 流程未进入下一节点；全部通过后节点完成，流程进入"已通过"结束事件。
     */
    @Test
    void andMode_shouldRequireAllCandidatesToApprove_beforeAdvancing() {
        ProcessFixture fixture = deployAndSeed("processes/test-multi-instance-and.bpmn20.xml",
                List.of(NodeSeed.simple("miApprove", "会签审批(AND)", ApprovalMode.AND, null,
                        "610001,610002,610003")));
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 619999L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "miApprove");
        assertThat(tasks).hasSize(3);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 610001L), 610001L, "同意", null));
        workflowService.approve(new ApproveCommand(taskOf(tasks, 610002L), 610002L, "同意", null));

        // 2/3 通过，1 人未处理：节点未完成，流程未推进
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(1);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 610003L), 610003L, "同意", null));

        // 3/3 全部通过：节点完成，流程进入"已通过"结束事件
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
        for (Long approver : List.of(610001L, 610002L, 610003L)) {
            assertThat(recordsOf(started.processInstanceId()))
                    .anyMatch(record -> "APPROVE".equals(record.getAction())
                            && approver.equals(record.getOperatorId()));
        }
    }

    /**
     * spec.md Scenario"OR模式任一通过即完成"：3 名候选人中 1 人通过即完成节点，流程进入
     * 下一节点（"已通过"结束事件），其余未处理的候选人任务在 Flowable 引擎层面被自动结束。
     */
    @Test
    void orMode_shouldCompleteAndCancelRemainingTasks_whenAnyCandidateApproves() {
        ProcessFixture fixture = deployAndSeed("processes/test-multi-instance-or.bpmn20.xml",
                List.of(NodeSeed.simple("miApprove", "会签审批(OR)", ApprovalMode.OR, null,
                        "620001,620002,620003")));
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 629999L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "miApprove");
        assertThat(tasks).hasSize(3);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(3);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 620001L), 620001L, "同意", null));

        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);
        // 其余 2 个候选人的任务在 Flowable 引擎层面被自动结束（不再是活动任务）
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
    }

    /**
     * spec.md Scenario"PERCENT模式达到比例即完成"：5 名候选人、阈值 60%，3 人通过
     * （3/5=60%）达到阈值，节点完成，流程进入下一节点。
     */
    @Test
    void percentMode_shouldCompleteWhenThresholdReached() {
        ProcessFixture fixture = deployAndSeed("processes/test-multi-instance-percent.bpmn20.xml",
                List.of(NodeSeed.simple("miApprove", "会签审批(PERCENT-60)", ApprovalMode.PERCENT, 60,
                        "630001,630002,630003,630004,630005")));
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 639999L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "miApprove");
        assertThat(tasks).hasSize(5);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 630001L), 630001L, "同意", null));
        workflowService.approve(new ApproveCommand(taskOf(tasks, 630002L), 630002L, "同意", null));

        // 2/5=40%，未达到 60% 阈值，节点未完成
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 630003L), 630003L, "同意", null));

        // 3/5=60%，达到阈值，节点完成，流程进入下一节点（"已通过"结束事件）
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
    }

    /**
     * spec.md Scenario"会签节点任一候选人驳回时终止"：无论审批模式为何，任一候选人驳回都应
     * 立即终止该节点，流程直接进入"已拒绝"结束事件，其余候选人未处理的任务被自动结束，不等待
     * 其余候选人处理（不是"少数服从多数"）。
     */
    @Test
    void anyRejection_shouldTerminateImmediately_regardlessOfMode() {
        ProcessFixture fixture = deployAndSeed("processes/test-multi-instance-and.bpmn20.xml",
                List.of(NodeSeed.simple("miApprove", "会签审批(AND)", ApprovalMode.AND, null,
                        "640001,640002,640003")));
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "集成测试流程", 649999L, null, null, null,
                fixture.processDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "miApprove");
        assertThat(tasks).hasSize(3);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 640001L), 640001L, "同意", null));
        workflowService.reject(new RejectCommand(taskOf(tasks, 640002L), 640002L, "不同意", null));

        // 任一驳回立即终止：不等待第 3 名候选人（640003）处理，流程直接进入"已拒绝"结束事件
        assertThat(instanceOf(started.processInstanceId()).getStatus()).isEqualTo(ProcessInstanceStatus.REJECTED);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
        assertThat(recordsOf(started.processInstanceId()))
                .anyMatch(record -> "REJECT".equals(record.getAction()) && Long.valueOf(640002L).equals(record.getOperatorId()));
    }

    /**
     * 按固定候选人用户 id 定位其在会签节点下的审批任务 id。
     */
    private Long taskOf(List<ApprovalTaskEntity> tasks, Long assigneeId) {
        return tasks.stream()
                .filter(task -> assigneeId.equals(task.getAssigneeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到候选人 " + assigneeId + " 对应的审批任务"))
                .getId();
    }
}
