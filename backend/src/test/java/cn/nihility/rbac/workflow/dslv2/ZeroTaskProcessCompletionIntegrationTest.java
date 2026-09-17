package cn.nihility.rbac.workflow.dslv2;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.dslv2.compiler.CompiledProcessV2;
import cn.nihility.rbac.workflow.dslv2.compiler.WorkflowModelCompilerV2;
import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.dto.ActionsConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 零任务流程（命中路径不经过任何审批节点）的 {@link WorkflowService#start} 收尾行为集成测试
 * （fix-approval-zero-task-process-completion change tasks.md 1.2/1.3/1.4）：验证
 * {@code FlowableWorkflowService.start()} 在 Flowable 同步跑完整个流程实例后，立即调用
 * {@code finalizeInstanceIfEnded} 把 {@code tab_wf_process_instance.status} 从 {@code RUNNING}
 * 收尾为真实终态，而不是永远停留在 {@code RUNNING}；同时验证正常需要人工审批的流程不受影响
 * （无回归）。与 {@link WorkflowModelCompilerV2IntegrationTest} 平行独立，聚焦本次修复本身，
 * 不复用其类内私有辅助方法。
 */
@SpringBootTest
@Transactional
class ZeroTaskProcessCompletionIntegrationTest {

    @Autowired
    private WorkflowModelCompilerV2 compiler;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private ProcessModelMapper processModelMapper;
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;
    @Autowired
    private NodeAssigneeRuleMapper nodeAssigneeRuleMapper;
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;
    @Autowired
    private ApprovalTaskMapper approvalTaskMapper;

    /** 测试专用流程编码自增序号，避免同一测试类内多个方法之间的流程编码冲突。 */
    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    /**
     * 零任务流程命中默认分支直接流转到 {@code outcome=APPROVED} 的结束节点：{@code start()}
     * 返回后流程实例应立即收尾为 {@code APPROVED}，不残留任何开放任务，当前节点信息清空。
     */
    @Test
    void zeroTaskDefaultBranchApproved_shouldFinalizeInstanceImmediately() {
        String processCode = "TEST_ZERO_TASK_APPROVED_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("零任务默认通过测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(new ConditionNodeDslV2(), "cond"),
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "cond", 1),
                        edge("e2", "cond", "end", 1)))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        assertThat(compiled.assigneeRules()).isEmpty();
        var fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "零任务默认通过测试", 979999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        assertThat(started.currentNodeId()).isNull();
        assertThat(started.currentNodeName()).isNull();

        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("APPROVED");
        assertThat(finished.getCurrentNodeId()).isNull();
        assertThat(finished.getCurrentNodeName()).isNull();

        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId()));
        assertThat(tasks).isEmpty();
    }

    /**
     * 零任务流程命中默认分支直接流转到 {@code outcome=REJECTED} 的结束节点：{@code start()}
     * 返回后流程实例应立即收尾为 {@code REJECTED}。
     */
    @Test
    void zeroTaskDefaultBranchRejected_shouldFinalizeInstanceImmediately() {
        String processCode = "TEST_ZERO_TASK_REJECTED_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("零任务默认拒绝测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(new ConditionNodeDslV2(), "cond"),
                        endNode("end", "REJECTED")))
                .edges(List.of(
                        edge("e1", "start", "cond", 1),
                        edge("e2", "cond", "end", 1)))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        var fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "零任务默认拒绝测试", 979998L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("REJECTED");
        assertThat(finished.getCurrentNodeId()).isNull();
        assertThat(finished.getCurrentNodeName()).isNull();

        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId()));
        assertThat(tasks).isEmpty();
    }

    /**
     * 回归验证：命中路径经过至少一个审批节点的正常流程，{@code start()} 返回后实例状态仍应
     * 保持 {@code RUNNING}，不会被本次新增的收尾调用误伤。
     */
    @Test
    void normalFlowWithApprovalNode_shouldRemainRunningAfterStart() {
        String processCode = "TEST_ZERO_TASK_REGRESSION_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("零任务修复回归测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        approvalNode("approve", "审批", 979997L),
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "approve"),
                        edge("e2", "approve", "end")))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        var fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "零任务修复回归测试", 979996L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        ProcessInstanceEntity running = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(running.getStatus()).isEqualTo("RUNNING");
        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId())
                .eq(ApprovalTaskEntity::getNodeId, "approve"));
        assertThat(tasks).hasSize(1);

        workflowService.approve(new ApproveCommand(tasks.get(0).getId(), 979997L, "同意", null));
        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("APPROVED");
    }

    private record Fixture(Long modelId, Long definitionId) {
    }

    private Fixture deployAndSeed(String processCode, CompiledProcessV2 compiled) {
        Deployment deployment = repositoryService.createDeployment()
                .name("zero-task-process-completion-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("零任务收尾集成测试流程-" + flowableDefinition.getKey())
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .enabled(true)
                .draftRevision(1L)
                .draftStatus("EDITING")
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processModelMapper.insert(model);

        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(model.getId())
                .processCode(processCode)
                .version(1)
                .schemaVersion(2)
                .flowableDefinitionKey(flowableDefinition.getKey())
                .flowableDefinitionId(flowableDefinition.getId())
                .modelJsonSnapshot("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .publishedBy("test").publishedTime(now)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processDefinitionMapper.insert(definition);

        model.setCurrentDefinitionId(definition.getId());
        model.setUpdateTime(LocalDateTime.now());
        processModelMapper.updateById(model);

        for (NodeAssigneeRuleDraft draft : compiled.assigneeRules()) {
            nodeAssigneeRuleMapper.insert(NodeAssigneeRuleEntity.builder()
                    .processDefinitionId(definition.getId())
                    .nodeId(draft.nodeId())
                    .nodeName(draft.nodeName())
                    .nodeOrder(draft.nodeOrder())
                    .assigneeType(draft.assigneeType() == null ? null : draft.assigneeType().name())
                    .assigneeValue(draft.assigneeValue())
                    .approvalMode(draft.approvalMode() == null ? null : draft.approvalMode().name())
                    .approvalPercent(draft.approvalPercent())
                    .emptyAssigneeStrategy(draft.emptyAssigneeStrategy() == null ? null : draft.emptyAssigneeStrategy().name())
                    .fallbackRoleCode(draft.fallbackRoleCode())
                    .allowSelfApproval(draft.allowSelfApproval())
                    .allowTransfer(draft.allowTransfer())
                    .allowDelegate(draft.allowDelegate())
                    .allowAddSign(draft.allowAddSign())
                    .allowReturn(draft.allowReturn())
                    .rejectPolicy(draft.rejectPolicy())
                    .createBy("test").createTime(now).updateBy("test").updateTime(now)
                    .build());
        }
        return new Fixture(model.getId(), definition.getId());
    }

    private ProcessNodeDslV2 node(ProcessNodeDslV2 node, String id) {
        node.setId(id);
        node.setType(typeOf(node));
        return node;
    }

    private String typeOf(ProcessNodeDslV2 node) {
        if (node instanceof StartNodeDslV2) {
            return "START";
        }
        if (node instanceof ConditionNodeDslV2) {
            return "CONDITION";
        }
        return node.getClass().getSimpleName();
    }

    private ApprovalNodeDslV2 approvalNode(String id, String name, Long userId) {
        ApprovalNodeDslV2 approval = new ApprovalNodeDslV2();
        approval.setId(id);
        approval.setType("APPROVAL");
        approval.setName(name);
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(AssigneeTypeV2.USER);
        assignee.setValue(userId.toString());
        approval.setAssignee(assignee);
        approval.setEmptyPolicy(EmptyPolicy.BLOCK);
        approval.setActions(new ActionsConfigDsl());
        return approval;
    }

    private EndNodeDslV2 endNode(String id, String outcome) {
        EndNodeDslV2 end = new EndNodeDslV2();
        end.setId(id);
        end.setType("END");
        end.setOutcome(outcome);
        return end;
    }

    /** 构造普通无条件连线（源节点非条件节点，或条件节点上唯一的默认兜底分支）。 */
    private EdgeDslV2 edge(String id, String source, String target) {
        return EdgeDslV2.builder().id(id).source(source).target(target).build();
    }

    /** 构造条件节点的默认兜底分支连线：不携带 {@code condition}，仅携带必填的 {@code priority}。 */
    private EdgeDslV2 edge(String id, String source, String target, Integer priority) {
        return EdgeDslV2.builder().id(id).source(source).target(target).priority(priority).build();
    }
}
