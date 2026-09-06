package cn.nihility.rbac.workflow.dslv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
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
import cn.nihility.rbac.workflow.dto.AddSignCommand;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.RejectCommand;
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
 * DSL v2 会签节点加签（{@code addSign}）针对真实 Flowable 7.2.0 引擎的集成测试
 * （production-approval-lifecycle change tasks.md 6.7）。与 {@link WorkflowV2VoteCountingIntegrationTest}
 * 平行独立，专注覆盖本轮新增的加签前置校验与 {@code tab_wf_node_run} N/K 同步，不重复其纯计票
 * 场景。真实并发（加签与最后一票竞争）场景见单独的
 * {@link WorkflowV2AddSignConcurrencyIntegrationTest}（本类用测试事务回滚，无法验证跨线程可见性）。
 */
@SpringBootTest
@Transactional
class WorkflowV2AddSignIntegrationTest {

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
    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    /**
     * 加签后必须同步更新 {@code tab_wf_node_run.totalCount} 与阈值 K：3 人候选、
     * {@code PERCENT=60}（K=2）时加签第 4 人后 K 应重算为 3（{@code ceil(4*60/100)=3}）；
     * 旧候选人 2 票同意在旧阈值下已足以通过，但因阈值已随加签同步提高，必须等待新候选人投票后
     * 才真正通过——验证 tasks.md 6.7 核实确认的真实缺口已修复。
     */
    @Test
    void addSign_shouldSyncTotalCountAndThreshold_andRequireNewCandidateVoteBeforePass() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_ADDSIGN_SYNC", "991001,991002,991003", VoteMode.PERCENT, 60, RejectPolicy.THRESHOLD);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(3);
        NodeRunEntity before = latestRound(started.processInstanceId(), "mi");
        assertThat(before.getTotalCount()).isEqualTo(3);

        workflowService.addSign(new AddSignCommand(
                taskOf(tasks, 991001L), 991001L, List.of(991004L), "补充审批人", null));

        NodeRunEntity afterAddSign = latestRound(started.processInstanceId(), "mi");
        assertThat(afterAddSign.getTotalCount()).isEqualTo(4);
        List<ApprovalTaskEntity> afterAddSignTasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(afterAddSignTasks).hasSize(4);

        boolean addSignRecorded = approvalRecordMapper.selectCount(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, started.processInstanceId())
                .eq(ApprovalRecordEntity::getAction, ApprovalAction.ADD_SIGN)) > 0;
        assertThat(addSignRecorded).isTrue();

        // 旧阈值(N=3,K=2)下 2 票同意即可通过，但加签后 K 已重算为 3，仅两名旧候选人同意不应通过
        workflowService.approve(new ApproveCommand(taskOf(afterAddSignTasks, 991001L), 991001L, "同意", null));
        workflowService.approve(new ApproveCommand(taskOf(afterAddSignTasks, 991002L), 991002L, "同意", null));
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.RUNNING);
        NodeRunEntity midRound = latestRound(started.processInstanceId(), "mi");
        assertThat(midRound.getAgreeCount()).isEqualTo(2);
        assertThat(midRound.getRunStatus()).isEqualTo("RUNNING");

        // 新候选人投票后才真正达到新阈值 K=3
        workflowService.approve(new ApproveCommand(taskOf(afterAddSignTasks, 991004L), 991004L, "同意", null));
        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.APPROVED);
        NodeRunEntity finalRound = latestRound(started.processInstanceId(), "mi");
        assertThat(finalRound.getTotalCount()).isEqualTo(4);
        assertThat(finalRound.getAgreeCount()).isEqualTo(3);
        assertThat(finalRound.getRunStatus()).isEqualTo("COMPLETED");
    }

    /**
     * 重复加签同一人（该用户已是本轮候选人）应明确拒绝，而不是静默忽略或产生重复计票。
     */
    @Test
    void addSign_shouldReject_whenUserAlreadyCandidateInCurrentRound() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_ADDSIGN_DUP_EXISTING", "992001,992002", VoteMode.ALL, null, RejectPolicy.VETO);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");

        assertThatThrownBy(() -> workflowService.addSign(new AddSignCommand(
                taskOf(tasks, 992001L), 992001L, List.of(992002L), "重复加签", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已是本轮候选人");

        // 拒绝后不应产生任何额外任务
        assertThat(tasksOf(started.processInstanceId(), "mi")).hasSize(2);
    }

    /**
     * 单次加签请求内部列出重复用户，应明确拒绝。
     */
    @Test
    void addSign_shouldReject_whenAddUserIdsContainDuplicates() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_ADDSIGN_DUP_REQUEST", "993001,993002", VoteMode.ALL, null, RejectPolicy.VETO);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");

        assertThatThrownBy(() -> workflowService.addSign(new AddSignCommand(
                taskOf(tasks, 993001L), 993001L, List.of(993999L, 993999L), "同一请求内重复", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    /**
     * 串行会签（{@code vote.execution=SEQUENTIAL}）节点不支持加签：{@code ActionsConfigDsl#getAddSign()}
     * 字段注释本就写明"仅对仍活跃的并行会签节点有意义"，本轮补齐运行时校验（tasks.md 6.7）。
     */
    @Test
    void addSign_shouldReject_onSequentialMultiInstanceNode() {
        String processCode = "TEST_V2_ADDSIGN_SEQUENTIAL_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 miNode = approvalNode(
                "mi", "串行会签审批", "994001,994002,994003", VoteMode.PERCENT, 60, RejectPolicy.THRESHOLD);
        miNode.getVote().setExecution(VoteExecution.SEQUENTIAL);
        miNode.getActions().setAddSign(true);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 串行会签加签拒绝测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), miNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "mi"), edge("e2", "mi", "end")))
                .build();
        CompiledProcessV2 compiled = compiler.compile(dsl);
        Fixture fixture = deployAndSeed(processCode, compiled);
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 串行会签加签拒绝测试", 0L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(1);

        assertThatThrownBy(() -> workflowService.addSign(new AddSignCommand(
                tasks.get(0).getId(), tasks.get(0).getAssigneeId(), List.of(994999L), "串行加签", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("串行");
    }

    /**
     * 单人/候选组节点（{@code approvalMode=SINGLE}）不是会签节点，不支持加签——即使误配置
     * {@code allowAddSign=true}，也应在业务层给出清晰拒绝，而不是让 Flowable
     * {@code addMultiInstanceExecution} 因找不到多实例根执行抛出未经包装的引擎异常。
     */
    @Test
    void addSign_shouldReject_onSingleModeNode() {
        String processCode = "TEST_V2_ADDSIGN_SINGLE_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 singleNode = approvalNode("single", "单人审批", "995001", null, null, null);
        singleNode.getActions().setAddSign(true);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 单人节点加签拒绝测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), singleNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "single"), edge("e2", "single", "end")))
                .build();
        CompiledProcessV2 compiled = compiler.compile(dsl);
        Fixture fixture = deployAndSeed(processCode, compiled);
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 单人节点加签拒绝测试", 0L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "single");
        assertThat(tasks).hasSize(1);

        assertThatThrownBy(() -> workflowService.addSign(new AddSignCommand(
                tasks.get(0).getId(), 995001L, List.of(995999L), "单人节点加签", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是会签节点");
    }

    /**
     * 已经决出结果（{@code COMPLETED}）的会签轮次不允许加签，即便所属流程实例仍在
     * {@code RUNNING}（已进入下一节点）——本项专门校验"节点轮次"维度的已结束状态，与下面
     * "流程实例已结束"场景是两个独立的校验点。
     */
    @Test
    void addSign_shouldReject_whenNodeRunAlreadyCompleted_evenIfInstanceStillRunning() {
        String processCode = "TEST_V2_ADDSIGN_ROUND_ENDED_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 miNode = approvalNode("mi", "会签审批", "996001,996002", VoteMode.ALL, null, RejectPolicy.VETO);
        miNode.getActions().setAddSign(true);
        ApprovalNodeDslV2 afterNode = approvalNode("after", "后续单人节点", "996999", null, null, null);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 轮次已结束加签拒绝测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), miNode, afterNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "mi"), edge("e2", "mi", "after"), edge("e3", "after", "end")))
                .build();
        CompiledProcessV2 compiled = compiler.compile(dsl);
        Fixture fixture = deployAndSeed(processCode, compiled);
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 轮次已结束加签拒绝测试", 0L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(2);
        workflowService.approve(new ApproveCommand(taskOf(tasks, 996001L), 996001L, "同意", null));
        workflowService.approve(new ApproveCommand(taskOf(tasks, 996002L), 996002L, "同意", null));

        NodeRunEntity round = latestRound(started.processInstanceId(), "mi");
        assertThat(round.getRunStatus()).isEqualTo("COMPLETED");
        ProcessInstanceEntity instance = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(instance.getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);

        // 用已完成轮次里的历史任务作为锚点尝试加签
        assertThatThrownBy(() -> workflowService.addSign(new AddSignCommand(
                taskOf(tasks, 996001L), 996001L, List.of(996998L), "轮次已结束仍尝试加签", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");
    }

    /**
     * 流程实例已终止（{@code REJECTED}）后不允许加签，与"节点轮次已结束"是独立的两层校验
     * （前者拦截整个流程已经没有后续动作意义的场景，即使调用方凑巧还持有一个已完成的历史
     * 任务 id）。
     */
    @Test
    void addSign_shouldReject_whenProcessInstanceAlreadyTerminated() {
        WorkflowInstanceResult started = startVoteProcess(
                "TEST_V2_ADDSIGN_INSTANCE_ENDED", "997001", VoteMode.ALL, null, RejectPolicy.VETO);
        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        workflowService.reject(new RejectCommand(tasks.get(0).getId(), 997001L, "不同意", null));

        assertThat(processInstanceMapper.selectById(started.processInstanceId()).getStatus())
                .isEqualTo(ProcessInstanceStatus.REJECTED);

        assertThatThrownBy(() -> workflowService.addSign(new AddSignCommand(
                tasks.get(0).getId(), 997001L, List.of(997999L), "流程已终止仍尝试加签", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程实例已结束");
    }

    // ---- 测试夹具构造辅助方法（与 WorkflowV2VoteCountingIntegrationTest 平行独立，不复用其
    // 私有辅助方法） ----

    /**
     * 构造并启动一个"start → mi(单会签节点，允许加签) → end(APPROVED)"的最简流程，返回启动结果。
     */
    private WorkflowInstanceResult startVoteProcess(
            String processCodePrefix, String candidateUserIds, VoteMode mode, Integer percent, RejectPolicy rejectPolicy) {
        String processCode = processCodePrefix + "_" + PROCESS_CODE_SEQ.incrementAndGet();
        ApprovalNodeDslV2 miNode = approvalNode("mi", "会签审批", candidateUserIds, mode, percent, rejectPolicy);
        miNode.getActions().setAddSign(true);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 加签测试-" + processCodePrefix)
                .nodes(List.of(node(new StartNodeDslV2(), "start"), miNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "mi"), edge("e2", "mi", "end")))
                .build();

        CompiledProcessV2 compiled = compiler.compile(dsl);
        Fixture fixture = deployAndSeed(processCode, compiled);

        return workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 加签测试", 0L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));
    }

    private record Fixture(Long modelId, Long definitionId) {
    }

    private Fixture deployAndSeed(String processCode, CompiledProcessV2 compiled) {
        Deployment deployment = repositoryService.createDeployment()
                .name("workflow-v2-addsign-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("v2 加签测试流程-" + flowableDefinition.getKey())
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
