package cn.nihility.rbac.workflow.dslv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.dslv2.compiler.CompiledProcessV2;
import cn.nihility.rbac.workflow.dslv2.compiler.WorkflowModelCompilerV2;
import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.ConditionLogic;
import cn.nihility.rbac.workflow.dslv2.constant.ConditionOperator;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.VoteExecution;
import cn.nihility.rbac.workflow.dslv2.constant.VoteMode;
import cn.nihility.rbac.workflow.dslv2.dto.ActionsConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
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
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.ReturnTaskCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.NodeRunEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.NodeRunMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退回（Return）目标节点校验针对真实 Flowable 7.2.0 引擎的集成测试
 * （production-approval-lifecycle change tasks.md 6.6）：核实并补齐
 * {@code FlowableWorkflowService.doReturnTask} 此前只校验目标节点 {@code allowReturn}、未校验
 * "目标节点是否真的是当前实例走过的历史节点""是否与当前节点处于同一串行域（不允许跨并行块，
 * 也不允许跨同一并行块内的不同分支）"这两项遗漏，以及退回后取消当前节点被撤销的其余开放任务、
 * 重建会签轮次。与 {@link WorkflowModelCompilerV2IntegrationTest}/
 * {@link WorkflowV2VoteCountingIntegrationTest} 平行独立，专注覆盖本轮新增的退回前置校验与
 * 善后逻辑，不重复其余场景；本类的 {@code deployAndSeed} 落库真实的 DSL v2
 * {@code modelJsonSnapshot}（而非二者共用的占位 {@code "{}"}），因为并行块作用域校验需要从
 * 快照里真实解析出并行分叉/汇合结构。
 */
@SpringBootTest
@Transactional
class TaskReturnScopeIntegrationTest {

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
    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;
    @Autowired
    private NodeRunMapper nodeRunMapper;

    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    /**
     * design.md 第7节"只退到同一串行域中实际完成且配置可退的节点"：目标节点与当前节点同处
     * 一个并行块内的同一分支（{@code branchA1 → branchA2}），退回应当成功。
     */
    @Test
    void returnTask_shouldSucceed_whenTargetIsHistoricalNodeInSameParallelBranch() {
        String processCode = "TEST_RETURN_SCOPE_SAME_BRANCH_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = parallelBranchDsl(processCode);
        Fixture fixture = deployAndSeed(processCode, dsl);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "退回同分支测试", 979999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        Long branchA1TaskId = singleTaskId(started.processInstanceId(), "branchA1");
        workflowService.approve(new ApproveCommand(branchA1TaskId, 971001L, "同意", null));
        Long branchA2TaskId = singleTaskId(started.processInstanceId(), "branchA2");

        workflowService.returnTask(new ReturnTaskCommand(
                branchA2TaskId, 971002L, "branchA1", "同分支退回重审", null));

        ApprovalTaskEntity branchA2AfterReturn = approvalTaskMapper.selectById(branchA2TaskId);
        assertThat(branchA2AfterReturn.getStatus()).isEqualTo(TaskStatus.RETURNED);

        List<ApprovalTaskEntity> branchA1TasksAfterReturn = tasksOf(started.processInstanceId(), "branchA1");
        assertThat(branchA1TasksAfterReturn).hasSize(2);
        ApprovalTaskEntity newBranchA1Task = branchA1TasksAfterReturn.stream()
                .filter(task -> !task.getId().equals(branchA1TaskId))
                .findFirst().orElseThrow();
        assertThat(newBranchA1Task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(newBranchA1Task.getAssigneeId()).isEqualTo(971001L);

        assertThat(recordsOf(started.processInstanceId())).anyMatch(record ->
                ApprovalAction.RETURN.equals(record.getAction()) && Long.valueOf(971002L).equals(record.getOperatorId()));
    }

    /**
     * design.md Non-Goals"跨并行域退回"：目标节点虽然与当前节点同处一个并行块，但属于另一条
     * 并行分支（{@code branchB}），必须明确拒绝，而不是静默按"同一并行块"放行。
     */
    @Test
    void returnTask_shouldBeRejected_whenTargetIsInDifferentParallelBranch() {
        String processCode = "TEST_RETURN_SCOPE_CROSS_BRANCH_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelDslV2 dsl = parallelBranchDsl(processCode);
        Fixture fixture = deployAndSeed(processCode, dsl);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "退回跨分支测试", 989999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        // 先让 branchB 分支走完（历史记录存在、允许退回），branchA 分支仍在 branchA1
        Long branchBTaskId = singleTaskId(started.processInstanceId(), "branchB");
        workflowService.approve(new ApproveCommand(branchBTaskId, 971003L, "同意", null));
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.RUNNING);

        Long branchA1TaskId = singleTaskId(started.processInstanceId(), "branchA1");
        workflowService.approve(new ApproveCommand(branchA1TaskId, 971001L, "同意", null));
        Long branchA2TaskId = singleTaskId(started.processInstanceId(), "branchA2");

        assertThatThrownBy(() -> workflowService.returnTask(new ReturnTaskCommand(
                branchA2TaskId, 971002L, "branchB", "尝试跨分支退回", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并行块");

        // 操作被拒绝，任务/实例状态不变
        ApprovalTaskEntity branchA2Task = approvalTaskMapper.selectById(branchA2TaskId);
        assertThat(branchA2Task.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    /**
     * design.md 第7节"拒绝……未经过节点"：目标节点虽然配置了 {@code allowReturn=true} 且流程
     * 图结构上可达，但本次流程实例实际走的是条件分支的另一侧，目标节点从未被真正处理过
     * （{@code tab_wf_approval_record} 无历史记录），必须拒绝。
     */
    @Test
    void returnTask_shouldBeRejected_whenTargetNodeNeverActuallyVisited() {
        String processCode = "TEST_RETURN_SCOPE_NEVER_VISITED_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 nodeA = approvalNode("nodeA", "第一级审批", 973001L, false);
        ApprovalNodeDslV2 nodeX = approvalNode("nodeX", "高风险专审(未经过)", 973002L, true);
        ApprovalNodeDslV2 nodeB = approvalNode("nodeB", "常规二级审批", 973003L, true);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("退回未经过节点测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        nodeA,
                        node(new ConditionNodeDslV2(), "cond"),
                        nodeX,
                        endNode("endHigh", "REJECTED"),
                        nodeB,
                        endNode("endNormal", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "nodeA", null, null),
                        edge("e2", "nodeA", "cond", null, null),
                        edge("e3", "cond", "nodeX",
                                ConditionAstDsl.builder().logic(ConditionLogic.AND)
                                        .items(List.of(ConditionItemDsl.builder()
                                                .field("riskLevel").op(ConditionOperator.EQ).value("HIGH").build()))
                                        .build(),
                                1),
                        edge("e4", "cond", "nodeB", null, 2),
                        edge("e5", "nodeX", "endHigh", null, null),
                        edge("e6", "nodeB", "endNormal", null, null)))
                .build();
        Fixture fixture = deployAndSeed(processCode, dsl);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "退回未经过节点测试", 993999L, null,
                Map.of("riskLevel", "LOW"), null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        Long nodeATaskId = singleTaskId(started.processInstanceId(), "nodeA");
        workflowService.approve(new ApproveCommand(nodeATaskId, 973001L, "同意", null));
        // 条件判定走默认分支到 nodeB，nodeX 从未被真正处理过
        Long nodeBTaskId = singleTaskId(started.processInstanceId(), "nodeB");
        assertThat(tasksOf(started.processInstanceId(), "nodeX")).isEmpty();

        assertThatThrownBy(() -> workflowService.returnTask(new ReturnTaskCommand(
                nodeBTaskId, 973003L, "nodeX", "尝试退回从未经过的节点", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史节点");

        ApprovalTaskEntity nodeBTask = approvalTaskMapper.selectById(nodeBTaskId);
        assertThat(nodeBTask.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    /**
     * tasks.md 6.6"退回后正确取消当前节点被撤销的任务并重建轮次"：当前节点本身是会签
     * （Multi-Instance）节点、本轮票数尚未收满时，一名候选人对该任务发起退回——{@code
     * moveActivityIdTo} 会连同该活动 id 上其余并发的候选人任务一起取消，此前实现遗漏同步业务
     * 投影表，其余候选人任务会变成永远查不到对应 Flowable 任务的僵尸记录；退回后目标节点重新
     * 推进、再次到达该会签节点时，应当开启全新一轮（{@code round_no} 递增），与被取消的第一轮
     * 计票完全隔离。
     */
    @Test
    void returnTask_shouldCancelSiblingOpenTasks_andRebuildRoundNo_whenReturningFromMiNode() {
        String processCode = "TEST_RETURN_SCOPE_MI_SIBLING_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 beforeNode = approvalNode("before", "前置单人审批", 975000L, true);
        ApprovalNodeDslV2 miNode = voteApprovalNode("mi", "会签审批(AND)", "975001,975002",
                VoteMode.ALL, RejectPolicy.VETO);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("退回取消会签同节点其余任务测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), beforeNode, miNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "before", null, null),
                        edge("e2", "before", "mi", null, null),
                        edge("e3", "mi", "end", null, null)))
                .build();
        Fixture fixture = deployAndSeed(processCode, dsl);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "退回取消同节点其余任务测试", 975999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        Long beforeTaskId = singleTaskId(started.processInstanceId(), "before");
        workflowService.approve(new ApproveCommand(beforeTaskId, 975000L, "同意", null));

        List<ApprovalTaskEntity> round1Tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(round1Tasks).hasSize(2);
        NodeRunEntity round1 = latestRound(started.processInstanceId(), "mi");
        assertThat(round1.getRoundNo()).isEqualTo(1);
        assertThat(round1.getRunStatus()).isEqualTo("RUNNING");

        ApprovalTaskEntity returningTask = round1Tasks.get(0);
        ApprovalTaskEntity siblingTask = round1Tasks.get(1);

        // 候选人之一在本轮票数尚未收满时发起退回，退回到已完成的 before 节点
        workflowService.returnTask(new ReturnTaskCommand(
                returningTask.getId(), returningTask.getAssigneeId(), "before", "材料不全，退回重审", null));

        ApprovalTaskEntity returningTaskAfter = approvalTaskMapper.selectById(returningTask.getId());
        assertThat(returningTaskAfter.getStatus()).isEqualTo(TaskStatus.RETURNED);

        // 同一节点其余尚未处理的候选人任务被同步取消，不再是查不到对应 Flowable 任务的僵尸记录
        ApprovalTaskEntity siblingTaskAfter = approvalTaskMapper.selectById(siblingTask.getId());
        assertThat(siblingTaskAfter.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(siblingTaskAfter.getCancelReason()).isNotBlank();

        // 被放弃的第一轮标记为已取消，不再是 RUNNING
        NodeRunEntity round1After = nodeRunMapper.selectById(round1.getId());
        assertThat(round1After.getRunStatus()).isEqualTo("CANCELLED");

        // 目标节点重新解析出新的待处理任务
        List<ApprovalTaskEntity> beforeTasksAfterReturn = tasksOf(started.processInstanceId(), "before");
        ApprovalTaskEntity newBeforeTask = beforeTasksAfterReturn.stream()
                .filter(task -> !task.getId().equals(beforeTaskId))
                .findFirst().orElseThrow();
        assertThat(newBeforeTask.getStatus()).isEqualTo(TaskStatus.PENDING);

        // 重新推进到会签节点，开启全新一轮，与被取消的第一轮计票完全隔离
        workflowService.approve(new ApproveCommand(newBeforeTask.getId(), 975000L, "再次同意", null));
        List<ApprovalTaskEntity> round2Tasks = tasksOf(started.processInstanceId(), "mi").stream()
                .filter(task -> TaskStatus.PENDING.equals(task.getStatus()))
                .toList();
        assertThat(round2Tasks).hasSize(2);
        NodeRunEntity round2 = latestRound(started.processInstanceId(), "mi");
        assertThat(round2.getId()).isNotEqualTo(round1.getId());
        assertThat(round2.getRoundNo()).isEqualTo(2);
        assertThat(round2.getRunStatus()).isEqualTo("RUNNING");
        assertThat(round2.getAgreeCount()).isEqualTo(0);

        workflowService.approve(new ApproveCommand(round2Tasks.get(0).getId(), round2Tasks.get(0).getAssigneeId(), "同意", null));
        workflowService.approve(new ApproveCommand(round2Tasks.get(1).getId(), round2Tasks.get(1).getAssigneeId(), "同意", null));
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.APPROVED);
    }

    // ---- 测试夹具构造辅助方法（与 WorkflowModelCompilerV2IntegrationTest/
    // WorkflowV2VoteCountingIntegrationTest 平行独立，不复用其私有辅助方法） ----

    /**
     * 构造"start → split[branchA1 → branchA2, branchB] → join → end"并行块流程：branchA 分支
     * 内部串行两个节点，branchB 单节点分支，三个审批节点均允许退回，用于并行块内同分支/跨分支
     * 两组场景共用。
     */
    private ProcessModelDslV2 parallelBranchDsl(String processCode) {
        return ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("退回并行块作用域测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(splitNode("split", "join"), "split"),
                        approvalNode("branchA1", "分支A-第一级", 971001L, true),
                        approvalNode("branchA2", "分支A-第二级", 971002L, true),
                        approvalNode("branchB", "分支B", 971003L, true),
                        node(joinNode("join", "split"), "join"),
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "split", null, null),
                        edge("e2", "split", "branchA1", null, null),
                        edge("e3", "branchA1", "branchA2", null, null),
                        edge("e4", "branchA2", "join", null, null),
                        edge("e5", "split", "branchB", null, null),
                        edge("e6", "branchB", "join", null, null),
                        edge("e7", "join", "end", null, null)))
                .build();
    }

    private record Fixture(Long modelId, Long definitionId) {
    }

    /**
     * 部署 + 落库测试夹具，与 {@code WorkflowModelCompilerV2IntegrationTest}/
     * {@code WorkflowV2VoteCountingIntegrationTest} 的关键差异：{@code modelJsonSnapshot}
     * 落库真实的 DSL v2 JSON（而非占位 {@code "{}"}），本类的并行块作用域校验依赖从中真实解析
     * 出并行分叉/汇合结构。
     */
    private Fixture deployAndSeed(String processCode, ProcessModelDslV2 dsl) {
        CompiledProcessV2 compiled = compiler.compile(dsl);
        Deployment deployment = repositoryService.createDeployment()
                .name("task-return-scope-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        String modelJson = JacksonUtils.toJson(dsl);
        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("退回作用域测试流程-" + flowableDefinition.getKey())
                .modelJson(modelJson)
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
                .modelJsonSnapshot(modelJson)
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

    private Long singleTaskId(Long processInstanceId, String nodeId) {
        List<ApprovalTaskEntity> tasks = tasksOf(processInstanceId, nodeId).stream()
                .filter(task -> TaskStatus.PENDING.equals(task.getStatus()) || TaskStatus.CLAIMED.equals(task.getStatus()))
                .toList();
        assertThat(tasks).hasSize(1);
        return tasks.get(0).getId();
    }

    private List<ApprovalRecordEntity> recordsOf(Long processInstanceId) {
        return approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                .orderByAsc(ApprovalRecordEntity::getId));
    }

    private NodeRunEntity latestRound(Long processInstanceId, String nodeId) {
        return nodeRunMapper.selectOne(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getInstanceId, processInstanceId)
                .eq(NodeRunEntity::getNodeId, nodeId)
                .orderByDesc(NodeRunEntity::getRoundNo)
                .last("LIMIT 1"));
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

    /**
     * 构造单人审批节点。
     */
    private ApprovalNodeDslV2 approvalNode(String id, String name, Long userId, boolean allowReturn) {
        ApprovalNodeDslV2 approval = new ApprovalNodeDslV2();
        approval.setId(id);
        approval.setType("APPROVAL");
        approval.setName(name);
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(AssigneeTypeV2.USER);
        assignee.setValue(userId.toString());
        approval.setAssignee(assignee);
        approval.setEmptyPolicy(EmptyPolicy.BLOCK);
        ActionsConfigDsl actions = new ActionsConfigDsl();
        actions.setReturnAllowed(allowReturn);
        approval.setActions(actions);
        return approval;
    }

    /**
     * 构造多候选人会签审批节点（逗号分隔用户 id）。
     */
    private ApprovalNodeDslV2 voteApprovalNode(
            String id, String name, String candidateUserIds, VoteMode mode, RejectPolicy rejectPolicy) {
        ApprovalNodeDslV2 approval = new ApprovalNodeDslV2();
        approval.setId(id);
        approval.setType("APPROVAL");
        approval.setName(name);
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(AssigneeTypeV2.USER);
        assignee.setValue(candidateUserIds);
        approval.setAssignee(assignee);
        VoteConfigDsl vote = new VoteConfigDsl();
        vote.setMode(mode);
        vote.setExecution(VoteExecution.PARALLEL);
        vote.setRejectPolicy(rejectPolicy);
        approval.setVote(vote);
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

    private EdgeDslV2 edge(String id, String source, String target, ConditionAstDsl condition, Integer priority) {
        return EdgeDslV2.builder().id(id).source(source).target(target).condition(condition).priority(priority).build();
    }
}
