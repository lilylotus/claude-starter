package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 回归验证一个曾经存在、现已修复的生产缺陷：{@code FlowableWorkflowService.start()} 内
 * {@code runtimeService.startProcessInstanceById(...)} 会同步执行到第一个用户任务创建，期间
 * 触发的 {@code WorkflowAssigneeTaskListener}/{@code WorkflowMultiInstanceTaskListener}/
 * {@code WorkflowMultiInstanceExecutionListener} 此前按 {@code flowable_instance_id} 反查
 * {@code tab_wf_process_instance}，而该列要等 {@code start()} 方法返回前最后一步才回填，
 * 流程第一个节点创建时查询命中不到、把 {@code tab_wf_approval_task.process_instance_id}
 * 落成了 {@code NULL}，导致对第一个节点调用 {@code approve} 等操作时抛出
 * {@code BusinessException("流程实例不存在")}。现已改为这三个监听器统一按 Flowable
 * businessKey（{@code start()} 发起流程时即传入的自有主键，从一开始就可用，不存在该时序
 * 问题）反查，本测试用于防止该缺陷回归。
 */
class FirstNodeInstanceLinkageBugTest extends AbstractWorkflowEngineIntegrationTest {

    /**
     * 流程发起后，第一个节点的合法处理人应能像后续节点一样正常审批通过并推进流程。
     */
    @Test
    void approvingFirstNodeTask_shouldSucceed_withoutManualLinkageWorkaround() {
        ProcessFixture fixture = deployAndSeed("processes/test-transfer-delegate-return.bpmn20.xml",
                List.of(
                        new NodeSeed("levelOne", "第一级审批", ApprovalMode.SINGLE, null, "710001",
                                false, false, false, false),
                        new NodeSeed("levelTwo", "第二级审批", ApprovalMode.SINGLE, null, "710002",
                                false, false, false, false),
                        new NodeSeed("levelThree", "第三级审批", ApprovalMode.SINGLE, null, "710003",
                                false, false, false, false)));

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                fixture.processCode(), "TEST", 1L, "缺陷复现", 899999L, null, null, null));

        List<ApprovalTaskEntity> levelOneTasks = tasksOf(started.processInstanceId(), "levelOne");
        assertThat(levelOneTasks).hasSize(1);
        Long taskId = levelOneTasks.get(0).getId();

        workflowService.approve(new ApproveCommand(taskId, 710001L, "同意", null));

        assertThat(tasksOf(started.processInstanceId(), "levelTwo")).hasSize(1);
    }
}
