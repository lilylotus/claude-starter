package cn.nihility.rbac.workflow.dslv2;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.dslv2.compiler.CompiledProcessV2;
import cn.nihility.rbac.workflow.dslv2.compiler.WorkflowModelCompilerV2;
import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.ConditionLogic;
import cn.nihility.rbac.workflow.dslv2.constant.ConditionOperator;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.VoteExecution;
import cn.nihility.rbac.workflow.dslv2.constant.VoteMode;
import cn.nihility.rbac.workflow.dslv2.dto.ActionsConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.CcNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionAstDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionItemDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelJoinNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelSplitNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.VoteConfigDsl;
import cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2ReassignmentService;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.CcRecordEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.CcRecordMapper;
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
 * DSL v2 编译器针对真实 Flowable 7.2.0 引擎的集成测试（production-approval-lifecycle change
 * 第 3 节）：编译 → 部署 → 落库定义/规则 → 通过 {@link WorkflowService} 真实驱动，覆盖并行
 * 分叉/汇合配对块、抄送节点持久化、条件分支、单人/会签空审批人 {@code BLOCK} 策略与运维
 * 重分配幂等。与 {@code AbstractWorkflowEngineIntegrationTest}（v1）平行独立，不共用部署/
 * 落库辅助方法——v1 直接部署手写 BPMN 资源文件，本类经由 {@link WorkflowModelCompilerV2}
 * 真实编译 DSL 产出 BPMN，验证的是编译器本身而非手写夹具。
 */
@SpringBootTest
@Transactional
class WorkflowModelCompilerV2IntegrationTest {

    @Autowired
    private WorkflowModelCompilerV2 compiler;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private WorkflowV2ReassignmentService reassignmentService;
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
    @Autowired
    private CcRecordMapper ccRecordMapper;

    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    @Test
    void parallelBlockAndCc_shouldCompileDeployAndRunToApproval() {
        String processCode = "TEST_V2_PARALLEL_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 并行块+抄送测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        approvalNode("leader", "负责人审批", 910001L, null),
                        node(splitNode("split", "join"), "split"),
                        approvalNode("branchA", "分支A审批", 910002L, null),
                        approvalNode("branchB", "分支B审批", 910003L, null),
                        node(joinNode("join", "split"), "join"),
                        ccNode("cc", 910004L),
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "leader", null, null),
                        edge("e2", "leader", "split", null, null),
                        edge("e3", "split", "branchA", null, null),
                        edge("e4", "split", "branchB", null, null),
                        edge("e5", "branchA", "join", null, null),
                        edge("e6", "branchB", "join", null, null),
                        edge("e7", "join", "cc", null, null),
                        edge("e8", "cc", "end", null, null)))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        assertThat(compiled.assigneeRules()).hasSize(3);

        var fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 集成测试", 919999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> leaderTasks = tasksOf(started.processInstanceId(), "leader");
        assertThat(leaderTasks).hasSize(1);
        workflowService.approve(new ApproveCommand(leaderTasks.get(0).getId(), 910001L, "同意", null));

        List<ApprovalTaskEntity> branchATasks = tasksOf(started.processInstanceId(), "branchA");
        List<ApprovalTaskEntity> branchBTasks = tasksOf(started.processInstanceId(), "branchB");
        assertThat(branchATasks).hasSize(1);
        assertThat(branchBTasks).hasSize(1);
        workflowService.approve(new ApproveCommand(branchATasks.get(0).getId(), 910002L, "同意", null));
        workflowService.approve(new ApproveCommand(branchBTasks.get(0).getId(), 910003L, "同意", null));

        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("APPROVED");

        List<CcRecordEntity> ccRecords = ccRecordMapper.selectList(new LambdaQueryWrapper<CcRecordEntity>()
                .eq(CcRecordEntity::getInstanceId, started.processInstanceId()));
        assertThat(ccRecords).extracting(CcRecordEntity::getRecipientId).containsExactly(910004L);
    }

    @Test
    void singleNodeEmptyAssignee_shouldBlockThenResumeAfterReassignment() {
        String processCode = "TEST_V2_BLOCK_SINGLE_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 单人空审批人 BLOCK 测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        approvalNode("approve", "审批", null, null),
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "approve", null, null),
                        edge("e2", "approve", "end", null, null)))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        var fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 空人测试", 929999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "approve");
        assertThat(tasks).hasSize(1);
        ApprovalTaskEntity task = tasks.get(0);
        assertThat(task.getAssigneeId()).isNull();
        assertThat(task.getCandidateType()).isNull();

        ProcessInstanceEntity blocked = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(blocked.getExceptionCode()).isEqualTo("ASSIGNEE_EMPTY");

        reassignmentService.reassign(task.getId(), List.of(921001L), 900001L, "reassign-key-1", "运维补充候选人");
        reassignmentService.reassign(task.getId(), List.of(921001L), 900001L, "reassign-key-1", "运维补充候选人");

        ApprovalTaskEntity reassigned = approvalTaskMapper.selectById(task.getId());
        assertThat(reassigned.getAssigneeId()).isEqualTo(921001L);
        ProcessInstanceEntity resumed = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(resumed.getExceptionCode()).isNull();

        workflowService.approve(new ApproveCommand(task.getId(), 921001L, "同意", null));
        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void multiInstanceEmptyAssignee_shouldNotAutoComplete_andResumeAfterReassignment() {
        String processCode = "TEST_V2_BLOCK_MI_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 miNode = approvalNode("miApprove", "会签审批", null, null);
        miNode.setVote(voteConfig(VoteMode.ALL, VoteExecution.PARALLEL, null));
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 会签空审批人 BLOCK 测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), miNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "miApprove", null, null), edge("e2", "miApprove", "end", null, null)))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        var fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 会签空人测试", 939999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "miApprove");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getAssigneeId()).isEqualTo(0L);

        ProcessInstanceEntity blocked = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(blocked.getStatus()).isEqualTo("RUNNING");
        assertThat(blocked.getExceptionCode()).isEqualTo("ASSIGNEE_EMPTY");

        reassignmentService.reassign(tasks.get(0).getId(), List.of(931001L, 931002L, 931003L), 900001L,
                "reassign-key-mi-1", "运维补充三名候选人");
        reassignmentService.reassign(tasks.get(0).getId(), List.of(931001L, 931002L, 931003L), 900001L,
                "reassign-key-mi-1", "运维补充三名候选人");

        List<ApprovalTaskEntity> realTasks = tasksOf(started.processInstanceId(), "miApprove").stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()))
                .toList();
        assertThat(realTasks).hasSize(3);

        for (ApprovalTaskEntity realTask : realTasks) {
            workflowService.approve(new ApproveCommand(realTask.getId(), realTask.getAssigneeId(), "同意", null));
        }
        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void conditionNode_shouldRouteByAstEvaluationAtRuntime() {
        // 条件节点前放一个审批节点：流程实例的终态目前由 approve()/reject() 在完成最后一个
        // 用户任务时显式写入（design.md Decision 6 提出的统一终态协调器本轮未实现，纯网关
        // 直通、不经过任何用户任务的流程不会有任何代码路径回写 tab_wf_process_instance.status，
        // 这是本轮范围外的已知架构缺口，测试按当前真实能覆盖的路径设计，不构造这种边缘场景）。
        String processCode = "TEST_V2_CONDITION_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 条件分支测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        approvalNode("leader", "负责人审批", 950001L, null),
                        node(new ConditionNodeDslV2(), "cond"),
                        endNode("highRisk", "REJECTED"),
                        endNode("normal", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "leader", null, null),
                        edge("e2", "leader", "cond", null, null),
                        edge("e3", "cond", "highRisk",
                                ConditionAstDsl.builder().logic(ConditionLogic.AND)
                                        .items(List.of(ConditionItemDsl.builder()
                                                .field("riskLevel").op(ConditionOperator.EQ).value("HIGH").build()))
                                        .build(),
                                1),
                        edge("e4", "cond", "normal", null, 2)))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        var fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 条件测试", 949999L, null,
                java.util.Map.of("riskLevel", "HIGH"), null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> leaderTasks = tasksOf(started.processInstanceId(), "leader");
        assertThat(leaderTasks).hasSize(1);
        workflowService.approve(new ApproveCommand(leaderTasks.get(0).getId(), 950001L, "同意", null));

        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("REJECTED");
    }

    // ---- 测试夹具构造辅助方法 ----

    private record Fixture(Long modelId, Long definitionId) {
    }

    private Fixture deployAndSeed(String processCode, CompiledProcessV2 compiled) {
        Deployment deployment = repositoryService.createDeployment()
                .name("workflow-v2-compiler-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("v2 集成测试流程-" + flowableDefinition.getKey())
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

    private List<ApprovalTaskEntity> tasksOf(Long processInstanceId, String nodeId) {
        return approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .eq(ApprovalTaskEntity::getNodeId, nodeId)
                .orderByAsc(ApprovalTaskEntity::getId));
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
        if (node instanceof ParallelSplitNodeDslV2) {
            return "PARALLEL_SPLIT";
        }
        if (node instanceof ParallelJoinNodeDslV2) {
            return "PARALLEL_JOIN";
        }
        return node.getClass().getSimpleName();
    }

    private ParallelSplitNodeDslV2 splitNode(String id, String joinNodeId) {
        ParallelSplitNodeDslV2 split = new ParallelSplitNodeDslV2();
        split.setJoinNodeId(joinNodeId);
        return split;
    }

    private ParallelJoinNodeDslV2 joinNode(String id, String splitNodeId) {
        ParallelJoinNodeDslV2 join = new ParallelJoinNodeDslV2();
        join.setSplitNodeId(splitNodeId);
        return join;
    }

    private ApprovalNodeDslV2 approvalNode(String id, String name, Long userId, VoteConfigDsl vote) {
        ApprovalNodeDslV2 approval = new ApprovalNodeDslV2();
        approval.setId(id);
        approval.setType("APPROVAL");
        approval.setName(name);
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        if (userId != null) {
            assignee.setType(AssigneeTypeV2.USER);
            assignee.setValue(userId.toString());
        } else {
            // 引用一个不存在成员的角色编码，validator 认为 value 非空、结构合法，
            // 但运行时 RoleAssigneeResolver 真实解析结果为空集合，用于真实触发 BLOCK 路径
            // （而不是靠结构上就非法的空 value 来伪造"空审批人"场景）。
            assignee.setType(AssigneeTypeV2.ROLE);
            assignee.setValue("NO_SUCH_ROLE_CODE_FOR_V2_BLOCK_TEST");
        }
        approval.setAssignee(assignee);
        approval.setVote(vote);
        approval.setEmptyPolicy(EmptyPolicy.BLOCK);
        ActionsConfigDsl actions = new ActionsConfigDsl();
        approval.setActions(actions);
        return approval;
    }

    private VoteConfigDsl voteConfig(VoteMode mode, VoteExecution execution, Integer percent) {
        VoteConfigDsl vote = new VoteConfigDsl();
        vote.setMode(mode);
        vote.setExecution(execution);
        vote.setPercent(percent);
        vote.setRejectPolicy(cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy.VETO);
        return vote;
    }

    private CcNodeDslV2 ccNode(String id, Long recipientUserId) {
        CcNodeDslV2 cc = new CcNodeDslV2();
        cc.setId(id);
        cc.setType("CC");
        cc.setName("抄送");
        cc.setRecipientType("USER");
        cc.setRecipientValue(recipientUserId.toString());
        return cc;
    }

    private EndNodeDslV2 endNode(String id, String outcome) {
        EndNodeDslV2 end = new EndNodeDslV2();
        end.setId(id);
        end.setType("END");
        end.setOutcome(outcome);
        return end;
    }

    private EdgeDslV2 edge(String id, String source, String target, ConditionAstDsl condition, Integer priority) {
        return EdgeDslV2.builder().id(id).source(source).target(target).condition(condition).priority(priority).build();
    }
}
