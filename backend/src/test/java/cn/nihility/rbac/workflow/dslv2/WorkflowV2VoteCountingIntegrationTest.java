package cn.nihility.rbac.workflow.dslv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.dslv2.compiler.CompiledProcessV2;
import cn.nihility.rbac.workflow.dslv2.compiler.WorkflowModelCompilerV2;
import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.VoteExecution;
import cn.nihility.rbac.workflow.dslv2.constant.VoteMode;
import cn.nihility.rbac.workflow.dslv2.dto.ActionsConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.VoteConfigDsl;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.DelegateCommand;
import cn.nihility.rbac.workflow.dto.DisagreeCommand;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.ReturnTaskCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.NodeRunEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.NodeRunMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * DSL v2 会签节点 N/A/R/U 计票（design.md 第7节 {@code rejectPolicy=VETO/THRESHOLD} 区分）针对
 * 真实 Flowable 7.2.0 引擎的集成测试（production-approval-lifecycle change tasks.md 6.3）。与
 * {@link WorkflowModelCompilerV2IntegrationTest} 平行独立，专注覆盖本轮新增的计票/终止判定与
 * {@code tab_wf_node_run} 落库，不重复其并行/条件/抄送场景。
 */
@SpringBootTest
@Transactional
class WorkflowV2VoteCountingIntegrationTest {

    @Autowired
    private WorkflowModelCompilerV2 compiler;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private TaskService taskService;
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
    private NodeRunMapper nodeRunMapper;

    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    /**
     * 1人节点（等价于 ALL=ANY=1）：唯一候选人通过，节点即通过，流程进入"已通过"结束事件。
     */
    @Test
    void singleCandidateVeto_shouldPass_whenApproved() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_SINGLE_APPROVE", "970001", VoteMode.ALL, null, RejectPolicy.VETO);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(1);

        workflowService.approve(new ApproveCommand(tasks.get(0).getId(), 970001L, "同意", null));

        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.APPROVED);
        NodeRunEntity round = latestRound(started.processInstanceId(), "mi");
        assertThat(round.getTotalCount()).isEqualTo(1);
        assertThat(round.getAgreeCount()).isEqualTo(1);
        assertThat(round.getRejectCount()).isEqualTo(0);
        assertThat(round.getRunStatus()).isEqualTo("COMPLETED");
    }

    /**
     * 1人节点：唯一候选人拒绝，立即终止整个流程实例。
     */
    @Test
    void singleCandidateVeto_shouldTerminate_whenRejected() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_SINGLE_REJECT", "970002", VoteMode.ALL, null, RejectPolicy.VETO);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(1);

        workflowService.reject(new RejectCommand(tasks.get(0).getId(), 970002L, "不同意", null));

        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.REJECTED);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
        NodeRunEntity round = latestRound(started.processInstanceId(), "mi");
        assertThat(round.getRejectCount()).isEqualTo(1);
        assertThat(round.getRunStatus()).isEqualTo("REJECTED");
    }

    /**
     * 3人 THRESHOLD 边界（design.md 第7节公式 {@code PERCENT=ceil(N×percent/100)}）：
     * percent=60、N=3 → K=2（{@code ceil(1.8)=2}）。2 人同意即达到阈值通过，不等待第 3 人。
     */
    @Test
    void thresholdThreeCandidates_shouldPass_whenTwoOfThreeApprove_withoutWaitingForThird() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_THRESHOLD_PASS", "981001,981002,981003", VoteMode.PERCENT, 60, RejectPolicy.THRESHOLD);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(3);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 981001L), 981001L, "同意", null));
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.RUNNING);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 981002L), 981002L, "同意", null));

        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.APPROVED);
        // 第 3 人的任务未处理即被引擎自动取消，不等待
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
        NodeRunEntity round = latestRound(started.processInstanceId(), "mi");
        assertThat(round.getTotalCount()).isEqualTo(3);
        assertThat(round.getAgreeCount()).isEqualTo(2);
        assertThat(round.getRunStatus()).isEqualTo("COMPLETED");
    }

    /**
     * 同一 THRESHOLD 节点（K=2）：2 人反对（{@code DISAGREE}）使"同意票+未决票"
     * {@code N-R=3-2=1<K=2}，节点判定不可能再通过，立即终止整个流程实例，不等待第 3 人处理，
     * 也不需要走 {@code REJECT} 硬终止动作。
     */
    @Test
    void thresholdThreeCandidates_shouldTerminate_whenTwoDisagree_withoutWaitingForThird() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_THRESHOLD_FAIL", "982001,982002,982003", VoteMode.PERCENT, 60, RejectPolicy.THRESHOLD);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(3);

        workflowService.disagree(new DisagreeCommand(taskOf(tasks, 982001L), 982001L, "反对", null));
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.RUNNING);

        workflowService.disagree(new DisagreeCommand(taskOf(tasks, 982002L), 982002L, "反对", null));

        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.REJECTED);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
        NodeRunEntity round = latestRound(started.processInstanceId(), "mi");
        assertThat(round.getRejectCount()).isEqualTo(2);
        assertThat(round.getAgreeCount()).isEqualTo(0);
        assertThat(round.getRunStatus()).isEqualTo("REJECTED");
    }

    /**
     * 百分比边界：5 人候选、percent=66 → {@code ceil(5×66/100)=ceil(3.3)=4}（整数公式
     * {@code (5*66+99)/100=4}），验证不是简单的向下取整（{@code floor(3.3)=3}会算错）。
     */
    @Test
    void thresholdPercentBoundary_shouldRoundUp_fiveCandidatesAt66Percent() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_PERCENT_66", "983001,983002,983003,983004,983005",
                VoteMode.PERCENT, 66, RejectPolicy.THRESHOLD);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(5);

        workflowService.approve(new ApproveCommand(taskOf(tasks, 983001L), 983001L, "同意", null));
        workflowService.approve(new ApproveCommand(taskOf(tasks, 983002L), 983002L, "同意", null));
        workflowService.approve(new ApproveCommand(taskOf(tasks, 983003L), 983003L, "同意", null));

        // 3/5=60%，未达到 66%（K=4），节点未完成
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.RUNNING);
        NodeRunEntity beforeFourth = latestRound(started.processInstanceId(), "mi");
        assertThat(beforeFourth.getAgreeCount()).isEqualTo(3);
        assertThat(beforeFourth.getRunStatus()).isEqualTo("RUNNING");

        workflowService.approve(new ApproveCommand(taskOf(tasks, 983004L), 983004L, "同意", null));

        // 4/5 达到 K=4，节点完成，不等待第 5 人
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.APPROVED);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
    }

    /**
     * VETO 模式：3 人候选，第一票反对立即终止整个流程实例，不等待其余候选人处理。
     */
    @Test
    void vetoThreeCandidates_shouldTerminateImmediately_onFirstReject_withoutWaitingForOthers() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_VETO_THREE", "984001,984002,984003", VoteMode.ALL, null, RejectPolicy.VETO);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(3);

        workflowService.reject(new RejectCommand(taskOf(tasks, 984001L), 984001L, "不同意", null));

        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.REJECTED);
        assertThat(taskService.createTaskQuery().processInstanceId(started.flowableProcessInstanceId()).count())
                .isEqualTo(0);
        NodeRunEntity round = latestRound(started.processInstanceId(), "mi");
        assertThat(round.getRunStatus()).isEqualTo("REJECTED");
        assertThat(round.getAgreeCount() + round.getRejectCount()).isEqualTo(1);
    }

    /**
     * VETO 节点不支持 {@code DISAGREE} 反对票动作，明确拒绝而不是静默按 {@code REJECT} 处理。
     */
    @Test
    void disagree_shouldBeRejected_onVetoNode() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_DISAGREE_ON_VETO", "985001", VoteMode.ALL, null, RejectPolicy.VETO);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");

        assertThatThrownBy(() -> workflowService.disagree(
                new DisagreeCommand(tasks.get(0).getId(), 985001L, "反对", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("一票否决");
    }

    /**
     * 委派归还（{@code resolve}）不计票：受托人 resolve 后节点轮次计票为 0；原处理人后续真正
     * 提交决策才计入票数（production-approval-lifecycle change tasks.md 6.3"取消/委派归还不
     * 计票"）。
     */
    @Test
    void delegateResolve_shouldNotCountAsVote() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_VOTE_DELEGATE_RESOLVE", "986001,986002,986003", VoteMode.PERCENT, 60, RejectPolicy.THRESHOLD);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        Long taskId = taskOf(tasks, 986001L);

        workflowService.delegate(new DelegateCommand(taskId, 986001L, 999001L, "帮忙看一下", null));
        // 受托人 resolve：调用 approve/reject 会命中 DelegationState.PENDING 分支，归还原处理
        // 人，不驱动流程、不计票
        workflowService.approve(new ApproveCommand(taskId, 999001L, "已阅", null));

        NodeRunEntity afterResolve = latestRound(started.processInstanceId(), "mi");
        assertThat(afterResolve.getAgreeCount()).isEqualTo(0);
        assertThat(afterResolve.getRejectCount()).isEqualTo(0);
        assertThat(afterResolve.getRunStatus()).isEqualTo("RUNNING");

        // 原处理人真正提交决策才计票
        workflowService.approve(new ApproveCommand(taskId, 986001L, "同意", null));
        NodeRunEntity afterRealVote = latestRound(started.processInstanceId(), "mi");
        assertThat(afterRealVote.getAgreeCount()).isEqualTo(1);
    }

    /**
     * 重入节点重新计票：会签节点通过后流程进入下一个单人节点，从该节点退回到会签节点，触发
     * 第二轮（{@code round_no} 递增），第二轮的计票与第一轮完全隔离——第一轮已通过的历史计数
     * 不会影响第二轮的判定，第二轮独立地因反对票立即终止。
     */
    @Test
    void reentrantNode_shouldIsolateVoteCounts_acrossRounds() {
        String processCode = "TEST_V2_VOTE_REENTRANT_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 miNode = approvalNode("mi", "会签", "987001,987002", VoteMode.ALL, null, RejectPolicy.VETO);
        miNode.getActions().setReturnAllowed(true);
        ApprovalNodeDslV2 afterNode = approvalNode("after", "退回发起节点", "987999", null, null, null);
        afterNode.getActions().setReturnAllowed(false);

        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 会签重入计票隔离测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        miNode,
                        afterNode,
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "mi"),
                        edge("e2", "mi", "after"),
                        edge("e3", "after", "end")))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        Fixture fixture = deployAndSeed(processCode, compiled);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 重入测试", 987999L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        // 第一轮：两名候选人全部同意，节点通过，进入 after 节点
        List<ApprovalTaskEntity> round1Tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(round1Tasks).hasSize(2);
        workflowService.approve(new ApproveCommand(taskOf(round1Tasks, 987001L), 987001L, "同意", null));
        workflowService.approve(new ApproveCommand(taskOf(round1Tasks, 987002L), 987002L, "同意", null));

        NodeRunEntity round1 = latestRound(started.processInstanceId(), "mi");
        assertThat(round1.getRoundNo()).isEqualTo(1);
        assertThat(round1.getRunStatus()).isEqualTo("COMPLETED");
        assertThat(round1.getAgreeCount()).isEqualTo(2);

        List<ApprovalTaskEntity> afterTasks = tasksOf(started.processInstanceId(), "after");
        assertThat(afterTasks).hasSize(1);

        // 退回到会签节点，触发第二轮
        workflowService.returnTask(new ReturnTaskCommand(
                afterTasks.get(0).getId(), 987999L, "mi", "驳回重审", null));

        List<NodeRunEntity> allRounds = nodeRunMapper.selectList(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getInstanceId, started.processInstanceId())
                .eq(NodeRunEntity::getNodeId, "mi")
                .orderByAsc(NodeRunEntity::getRoundNo));
        assertThat(allRounds).hasSize(2);
        assertThat(allRounds.get(1).getRoundNo()).isEqualTo(2);
        assertThat(allRounds.get(1).getAgreeCount()).isEqualTo(0);
        assertThat(allRounds.get(1).getRejectCount()).isEqualTo(0);
        assertThat(allRounds.get(1).getRunStatus()).isEqualTo("RUNNING");

        // 第二轮独立计票：第一票反对立即终止，不受第一轮"已通过"历史影响
        List<ApprovalTaskEntity> round2Tasks = tasksOf(started.processInstanceId(), "mi").stream()
                .filter(t -> !TaskStatus.COMPLETED.equals(t.getStatus()) && !TaskStatus.RETURNED.equals(t.getStatus()))
                .toList();
        assertThat(round2Tasks).hasSize(2);
        workflowService.reject(new RejectCommand(taskOf(round2Tasks, 987001L), 987001L, "重审后不同意", null));

        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.REJECTED);
        NodeRunEntity round2Final = nodeRunMapper.selectOne(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getInstanceId, started.processInstanceId())
                .eq(NodeRunEntity::getNodeId, "mi")
                .eq(NodeRunEntity::getRoundNo, 2)
                .last("LIMIT 1"));
        assertThat(round2Final.getRunStatus()).isEqualTo("REJECTED");
        assertThat(round2Final.getRejectCount()).isEqualTo(1);
        // 第一轮记录本身不受第二轮影响
        NodeRunEntity round1Untouched = nodeRunMapper.selectOne(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getInstanceId, started.processInstanceId())
                .eq(NodeRunEntity::getNodeId, "mi")
                .eq(NodeRunEntity::getRoundNo, 1)
                .last("LIMIT 1"));
        assertThat(round1Untouched.getAgreeCount()).isEqualTo(2);
        assertThat(round1Untouched.getRunStatus()).isEqualTo("COMPLETED");
    }

    // ---- 测试夹具构造辅助方法（与 WorkflowModelCompilerV2IntegrationTest 平行独立，不复用其
    // 私有辅助方法） ----

    /**
     * 构造并启动一个"start → mi(单会签节点) → end(APPROVED)"的最简流程，返回启动结果。
     */
    private WorkflowInstanceResult startVoteProcess(
            String processCodePrefix, String candidateUserIds, VoteMode mode, Integer percent, RejectPolicy rejectPolicy) {
        String processCode = processCodePrefix + "_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 miNode = approvalNode("mi", "会签审批", candidateUserIds, mode, percent, rejectPolicy);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 会签计票测试-" + processCodePrefix)
                .nodes(List.of(node(new StartNodeDslV2(), "start"), miNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "mi"), edge("e2", "mi", "end")))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        Fixture fixture = deployAndSeed(processCode, compiled);

        return workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 会签计票测试", 0L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));
    }

    private record Fixture(Long modelId, Long definitionId) {
    }

    private Fixture deployAndSeed(String processCode, CompiledProcessV2 compiled) {
        Deployment deployment = repositoryService.createDeployment()
                .name("workflow-v2-vote-counting-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("v2 会签计票测试流程-" + flowableDefinition.getKey())
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

    private Long taskOf(List<ApprovalTaskEntity> tasks, Long assigneeId) {
        return tasks.stream()
                .filter(task -> assigneeId.equals(task.getAssigneeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到候选人 " + assigneeId + " 对应的任务"))
                .getId();
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
        return node.getClass().getSimpleName();
    }

    private ApprovalNodeDslV2 approvalNode(
            String id, String name, String candidateUserIds, VoteMode mode, Integer percent, RejectPolicy rejectPolicy) {
        ApprovalNodeDslV2 approval = new ApprovalNodeDslV2();
        approval.setId(id);
        approval.setType("APPROVAL");
        approval.setName(name);
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(AssigneeTypeV2.USER);
        assignee.setValue(candidateUserIds);
        approval.setAssignee(assignee);
        if (mode != null) {
            VoteConfigDsl vote = new VoteConfigDsl();
            vote.setMode(mode);
            vote.setExecution(VoteExecution.PARALLEL);
            vote.setPercent(percent);
            vote.setRejectPolicy(rejectPolicy);
            approval.setVote(vote);
        }
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

    private EdgeDslV2 edge(String id, String source, String target) {
        return EdgeDslV2.builder().id(id).source(source).target(target).build();
    }
}
