package cn.nihility.rbac.workflow.dslv2;

import static org.assertj.core.api.Assertions.assertThat;

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
import cn.nihility.rbac.workflow.dto.AddSignCommand;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 加签与"最后一票"并发提交的真实并发集成测试（production-approval-lifecycle change tasks.md
 * 6.7）。本类不使用测试专用回滚事务，理由与 {@code TaskClaimConcurrencyIntegrationTest}/
 * {@code IdempotencyServiceImplConcurrencyIntegrationTest} 一致：两个线程需要在各自真实独立、
 * 真正提交的物理事务里并发操作同一 {@code tab_wf_node_run} 行，用测试事务把整个方法包起来会让
 * 一个线程的落库对另一个线程不可见。{@link WorkflowService} 各方法自身已声明
 * {@code @Transactional}，测试线程直接调用即可各自拥有独立的物理事务。
 * <p>
 * 并发确定性来源：{@code FlowableWorkflowService.completeTask}/{@code doAddSign} 都以
 * "先对 {@code tab_wf_process_instance} 行加 {@code SELECT ... FOR UPDATE}" 作为各自事务的
 * 第一步，两个线程对同一流程实例的这一步天然互斥——先拿到锁的事务完整跑完（含引擎调用）并提交，
 * 后拿到锁的事务才能继续，因此最终结果必然落在两个互斥且自洽的分支之一（见各测试方法内注释），
 * 不存在"两边都成功导致重复计票"或"两边都失败"的中间状态。
 */
@SpringBootTest
class WorkflowV2AddSignConcurrencyIntegrationTest {

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
    private NodeRunMapper nodeRunMapper;
    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    /** 本次测试真实部署的 Flowable 部署 id，测试结束后级联删除（含运行时/历史数据）。 */
    private String deploymentIdToCleanup;
    /** 本次测试真实落库的自有业务数据主键，测试结束后逐一清理。 */
    private Long processModelIdToCleanup;
    private Long processDefinitionIdToCleanup;
    private Long processInstanceIdToCleanup;

    @AfterEach
    void cleanup() {
        if (processInstanceIdToCleanup != null) {
            approvalRecordMapper.delete(new LambdaQueryWrapper<ApprovalRecordEntity>()
                    .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceIdToCleanup));
            nodeRunMapper.delete(new LambdaQueryWrapper<NodeRunEntity>()
                    .eq(NodeRunEntity::getInstanceId, processInstanceIdToCleanup));
            approvalTaskMapper.delete(new LambdaQueryWrapper<ApprovalTaskEntity>()
                    .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceIdToCleanup));
            processInstanceMapper.deleteById(processInstanceIdToCleanup);
        }
        if (processDefinitionIdToCleanup != null) {
            nodeAssigneeRuleMapper.delete(new LambdaQueryWrapper<NodeAssigneeRuleEntity>()
                    .eq(NodeAssigneeRuleEntity::getProcessDefinitionId, processDefinitionIdToCleanup));
            processDefinitionMapper.deleteById(processDefinitionIdToCleanup);
        }
        if (processModelIdToCleanup != null) {
            processModelMapper.deleteById(processModelIdToCleanup);
        }
        if (deploymentIdToCleanup != null) {
            repositoryService.deleteDeployment(deploymentIdToCleanup, true);
        }
    }

    /**
     * 2 人 {@code ALL}（K=N）会签节点，候选人 A 已同意。并发发起"候选人 B 提交最后一票"与
     * "对该节点加签候选人 C"：两个操作各自的 {@code @Transactional} 事务都以对
     * {@code tab_wf_process_instance} 行加 {@code SELECT ... FOR UPDATE} 作为第一步，天然互斥
     * ——先拿到锁的事务完整跑完并提交，后拿到锁的事务才能继续，因此恰好其中一个会先"跑赢"：
     * <ul>
     *   <li>若加签先赢：{@code totalCount} 变为 3，阈值重算为 {@code K=3}；随后 B 的一票落地后
     *       {@code agreeCount=2<3}，轮次必须仍是 {@code RUNNING}，等待新候选人 C 投票——不能
     *       因为凑巧与旧阈值 2 相等就提前通过。</li>
     *   <li>若 B 的一票先赢：{@code A+B=2=K(旧阈值)} 达成，轮次 {@code COMPLETED}、流程
     *       {@code APPROVED}；随后加签因流程实例/轮次已不再 {@code RUNNING} 被明确拒绝
     *       （不会产生游离的候选分支）。</li>
     * </ul>
     * 后拿到锁的那一方在恢复执行后，即使通过了我们自己基于行锁的业务校验，仍可能因为 MySQL
     * 默认 {@code REPEATABLE READ} 隔离级别下自身事务的一致性读快照早于对方提交而在真正调用
     * Flowable 引擎命令时撞上引擎自身的乐观锁（{@code FlowableOptimisticLockingException}，
     * {@code FlowableWorkflowService.runEngineCommand} 统一转换为清晰的
     * {@link BusinessException}，tasks.md 6.7 真实并发测试核实确认的缺口）——因此"加签成功"与
     * "投票成功"两个结果都需要分别捕获对方可能失败的清晰业务异常，不能假设其中一方必然成功。
     * 两个分支互斥且都是自洽的终态，不存在第三种（重复计票/游离执行/未经包装的引擎异常）结果。
     */
    @Test
    void concurrentAddSignAndFinalVote_shouldProduceOneOfTwoDeterministicOutcomes() throws Exception {
        String processCode = "TEST_V2_ADDSIGN_RACE_" + PROCESS_CODE_SEQ.incrementAndGet() + "_" + UUID.randomUUID();
        ApprovalNodeDslV2 miNode = approvalNode("mi", "会签审批", "961001,961002", VoteMode.ALL, null, RejectPolicy.VETO);
        miNode.getActions().setAddSign(true);
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 加签并发竞争测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), miNode, endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "mi"), edge("e2", "mi", "end")))
                .build();
        CompiledProcessV2 compiled = compiler.compile(dsl);
        Fixture fixture = deployAndSeed(processCode, compiled);
        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 加签并发竞争测试", 0L, null, null, null,
                fixture.definitionId(), null, null, ExecutionMode.LEGACY_SYNC));
        processInstanceIdToCleanup = started.processInstanceId();

        List<ApprovalTaskEntity> tasks = tasksOf(started.processInstanceId(), "mi");
        assertThat(tasks).hasSize(2);
        Long taskA = taskOf(tasks, 961001L);
        Long taskB = taskOf(tasks, 961002L);

        workflowService.approve(new ApproveCommand(taskA, 961001L, "同意", null));
        NodeRunEntity afterFirstVote = latestRound(started.processInstanceId(), "mi");
        assertThat(afterFirstVote.getAgreeCount()).isEqualTo(1);
        assertThat(afterFirstVote.getRunStatus()).isEqualTo("RUNNING");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // 幂等键必须每次测试运行唯一（{@code IdempotencyService} 按 request_key 全局判重，
        // 固定字面量会在同一开发库上重复跑本测试时被误判为"同一幂等键提交了不同内容"）。
        String voteIdempotencyKey = "race-vote-" + UUID.randomUUID();
        String addSignIdempotencyKey = "race-addsign-" + UUID.randomUUID();
        Callable<Boolean> finalVote = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                workflowService.approve(new ApproveCommand(taskB, 961002L, "同意", voteIdempotencyKey));
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };
        Callable<Boolean> addSignAttempt = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                workflowService.addSign(new AddSignCommand(
                        taskA, 961001L, List.of(961003L), "并发加签", addSignIdempotencyKey));
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };

        Future<Boolean> voteFuture = executor.submit(finalVote);
        Future<Boolean> addSignFuture = executor.submit(addSignAttempt);
        boolean voteSucceeded = voteFuture.get(30, TimeUnit.SECONDS);
        boolean addSignSucceeded = addSignFuture.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 两个并发操作的业务锁互斥，必然恰好一方成功、另一方得到清晰的业务拒绝，不存在两者都
        // 成功（重复计票/游离执行）或两者都失败（无谓阻塞对方）的情形。
        assertThat(voteSucceeded).as("加签与最后一票应恰好一方成功").isNotEqualTo(addSignSucceeded);

        ProcessInstanceEntity finalInstance = processInstanceMapper.selectById(started.processInstanceId());
        NodeRunEntity finalRound = latestRound(started.processInstanceId(), "mi");

        if (addSignSucceeded) {
            // 加签先赢：阈值已提高到 3，仅 A+B 两票不足以通过，必须等待新候选人 C；B 的最后一票
            // 因撞上引擎自身乐观锁被清晰拒绝，不影响已落库的计票（未被误计为已投票），未产生
            // 重复计票或半完成状态
            assertThat(finalRound.getTotalCount()).isEqualTo(3);
            assertThat(finalRound.getAgreeCount()).isEqualTo(1);
            assertThat(finalRound.getRunStatus()).isEqualTo("RUNNING");
            assertThat(finalInstance.getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);
            ApprovalTaskEntity taskBEntity = approvalTaskMapper.selectById(taskB);
            assertThat(taskBEntity.getStatus()).isNotEqualTo(TaskStatus.COMPLETED);
            assertThat(tasksOf(started.processInstanceId(), "mi")).hasSize(3);
        } else {
            // 最后一票先赢：旧阈值 2 已达成，轮次与流程都已终态，加签被明确拒绝，未产生第 3 个候选人
            assertThat(finalRound.getTotalCount()).isEqualTo(2);
            assertThat(finalRound.getAgreeCount()).isEqualTo(2);
            assertThat(finalRound.getRunStatus()).isEqualTo("COMPLETED");
            assertThat(finalInstance.getStatus()).isEqualTo(ProcessInstanceStatus.APPROVED);
            assertThat(tasksOf(started.processInstanceId(), "mi")).hasSize(2);
        }
    }

    // ---- 测试夹具构造辅助方法（与 WorkflowV2VoteCountingIntegrationTest/
    // WorkflowV2AddSignIntegrationTest 平行独立，不复用其私有辅助方法） ----

    private record Fixture(Long modelId, Long definitionId) {
    }

    private Fixture deployAndSeed(String processCode, CompiledProcessV2 compiled) {
        Deployment deployment = repositoryService.createDeployment()
                .name("workflow-v2-addsign-concurrency-integration-test")
                .addBpmnModel(processCode + ".bpmn20.xml", compiled.bpmnModel())
                .deploy();
        deploymentIdToCleanup = deployment.getId();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("v2 加签并发竞争测试流程-" + flowableDefinition.getKey())
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .enabled(true)
                .draftRevision(1L)
                .draftStatus("EDITING")
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processModelMapper.insert(model);
        processModelIdToCleanup = model.getId();

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
        processDefinitionIdToCleanup = definition.getId();

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
