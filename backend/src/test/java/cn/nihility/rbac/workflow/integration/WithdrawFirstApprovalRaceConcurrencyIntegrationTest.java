package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
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
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * "撤回"与"第一票 approve"真实并发竞争的集成测试（production-approval-lifecycle change
 * design.md 第7节"withdraw：仅申请人在没有 APPROVE/DISAGREE/REJECT 决策前可撤回；与首票
 * 竞争串行化"，tasks.md 6.8）。
 * <p>
 * 核实结论：{@code doWithdraw}（{@code BeforeFirstApprovalWithdrawPolicy}）与
 * {@code completeTask} 对同一 {@code tab_wf_process_instance} 行使用完全相同的加锁顺序
 * （均在动作方法一开始就 {@code SELECT ... FOR UPDATE} 该实例行），MySQL InnoDB 下真正互斥；
 * 后到达者在实例行锁上排队等待，直到先到达者提交后才能继续，此时其事务内的第一次一致性读
 * （{@code BeforeFirstApprovalWithdrawPolicy#canWithdraw} 的 {@code selectList}）才建立快照，
 * 天然能看到先到达者已提交的审批记录，不存在"撤回成功但同时又真实计入一票"的竞态。本测试类
 * 用 {@link RepeatedTest} 重复至少3次验证该结论的确定性，而非偶然通过一次。
 * <p>
 * 不使用测试专用回滚事务：两个线程需要在各自真实独立、真正提交的物理事务里同时对同一流程
 * 实例发起撤回/审批，与 {@link TaskClaimConcurrencyIntegrationTest} 同样的理由。
 */
@SpringBootTest
class WithdrawFirstApprovalRaceConcurrencyIntegrationTest {

    /** 复用既有的三级单人审批测试流程夹具（levelOne/levelTwo/levelThree 均为固定单人）。 */
    private static final String TRANSFER_DELEGATE_RETURN_BPMN = "processes/test-transfer-delegate-return.bpmn20.xml";

    /** 流程发起人（申请人）用户 id。 */
    private static final Long APPLICANT_ID = 889999L;

    /** levelOne 固定审批人用户 id。 */
    private static final Long LEVEL_ONE_ASSIGNEE_ID = 881001L;

    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private RepositoryService repositoryService;
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
    private ApprovalTaskCandidateMapper approvalTaskCandidateMapper;
    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

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
            List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                    .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceIdToCleanup));
            for (ApprovalTaskEntity task : tasks) {
                approvalTaskCandidateMapper.delete(new LambdaQueryWrapper<ApprovalTaskCandidateEntity>()
                        .eq(ApprovalTaskCandidateEntity::getTaskId, task.getId()));
            }
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
        deploymentIdToCleanup = null;
        processModelIdToCleanup = null;
        processDefinitionIdToCleanup = null;
        processInstanceIdToCleanup = null;
    }

    /**
     * 申请人几乎同时发起撤回、levelOne 审批人几乎同时发起第一票通过：真实并发下必须恰好一个
     * 成功，另一个收到明确的业务拒绝，且最终数据状态与"成功的那一方"完全一致——不能出现"两者
     * 都成功"“撤回成功但仍然多出一条通过记录/任务被判为已完成”这类不一致状态。重复运行 3 次
     * 验证结果确定性，而非偶然通过一次。
     */
    @RepeatedTest(3)
    void concurrentWithdrawAndFirstApprove_exactlyOneSucceeds_andProjectionConsistent() throws Exception {
        String processCode = "TEST_WITHDRAW_RACE_" + UUID.randomUUID();
        Deployment deployment = repositoryService.createDeployment()
                .name("withdraw-first-approval-race-integration-test")
                .addClasspathResource(TRANSFER_DELEGATE_RETURN_BPMN)
                .deploy();
        deploymentIdToCleanup = deployment.getId();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("撤回与首票竞争测试")
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processModelMapper.insert(model);
        processModelIdToCleanup = model.getId();

        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(model.getId())
                .processCode(processCode)
                .version(1)
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

        // 三级均为固定单人审批人，levelOne 由 LEVEL_ONE_ASSIGNEE_ID 一人处理。
        seedRule(definition.getId(), "levelOne", "第一级审批", 1, LEVEL_ONE_ASSIGNEE_ID.toString(), now);
        seedRule(definition.getId(), "levelTwo", "第二级审批", 2, "882001", now);
        seedRule(definition.getId(), "levelThree", "第三级审批", 3, "883001", now);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "撤回与首票竞争测试", APPLICANT_ID, null, null, null,
                definition.getId(), null, null, ExecutionMode.LEGACY_SYNC));
        Long processInstanceId = started.processInstanceId();
        processInstanceIdToCleanup = processInstanceId;

        List<ApprovalTaskEntity> levelOneTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .eq(ApprovalTaskEntity::getNodeId, "levelOne"));
        assertThat(levelOneTasks).hasSize(1);
        Long taskId = levelOneTasks.get(0).getId();
        assertThat(levelOneTasks.get(0).getAssigneeId()).isEqualTo(LEVEL_ONE_ASSIGNEE_ID);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> withdrawAttempt = withdrawAttempt(processInstanceId, barrier);
        Callable<Boolean> approveAttempt = approveAttempt(taskId, barrier);

        Future<Boolean> withdrawFuture = executor.submit(withdrawAttempt);
        Future<Boolean> approveFuture = executor.submit(approveAttempt);
        boolean withdrawSucceeded = withdrawFuture.get(30, TimeUnit.SECONDS);
        boolean approveSucceeded = approveFuture.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(withdrawSucceeded ^ approveSucceeded)
                .as("撤回与第一票 approve 并发提交，必须恰好一个成功").isTrue();

        ProcessInstanceEntity finalInstance = processInstanceMapper.selectById(processInstanceId);
        ApprovalTaskEntity finalTask = approvalTaskMapper.selectById(taskId);
        long approveRecordCount = approvalRecordMapper.selectCount(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                .eq(ApprovalRecordEntity::getAction, "APPROVE"));

        if (withdrawSucceeded) {
            assertThat(finalInstance.getStatus()).isEqualTo(ProcessInstanceStatus.WITHDRAWN);
            assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.CANCELLED);
            assertThat(finalTask.getCancelReason()).isNotBlank();
            assertThat(approveRecordCount).as("撤回成功时不能同时真实计入一票 APPROVE 记录").isZero();
        } else {
            assertThat(finalInstance.getStatus()).isEqualTo(ProcessInstanceStatus.RUNNING);
            assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(approveRecordCount).as("审批成功时必须真实落一条 APPROVE 记录").isEqualTo(1);
            List<ApprovalTaskEntity> levelTwoTasks = approvalTaskMapper.selectList(
                    new LambdaQueryWrapper<ApprovalTaskEntity>()
                            .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                            .eq(ApprovalTaskEntity::getNodeId, "levelTwo"));
            assertThat(levelTwoTasks).hasSize(1);
        }
    }

    /**
     * 构造一个"到达同步屏障后立即发起撤回，成功返回 {@code true}，业务拒绝返回
     * {@code false}"的并发任务；非业务异常（真正的 bug）不吞掉，让 {@code future.get()} 直接
     * 抛出暴露问题。
     */
    private Callable<Boolean> withdrawAttempt(Long processInstanceId, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                workflowService.withdraw(new WithdrawCommand(
                        processInstanceId, APPLICANT_ID, "并发撤回测试", "withdraw-race-" + UUID.randomUUID()));
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };
    }

    /**
     * 构造一个"到达同步屏障后立即发起 approve，成功返回 {@code true}，业务拒绝返回
     * {@code false}"的并发任务；非业务异常（真正的 bug）不吞掉，让 {@code future.get()} 直接
     * 抛出暴露问题。
     */
    private Callable<Boolean> approveAttempt(Long taskId, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                workflowService.approve(new ApproveCommand(
                        taskId, LEVEL_ONE_ASSIGNEE_ID, "同意", "approve-race-" + UUID.randomUUID()));
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };
    }

    /**
     * 落库一条固定单人审批人规则。
     */
    private void seedRule(Long processDefinitionId, String nodeId, String nodeName, int order,
            String assigneeValue, LocalDateTime now) {
        nodeAssigneeRuleMapper.insert(NodeAssigneeRuleEntity.builder()
                .processDefinitionId(processDefinitionId)
                .nodeId(nodeId)
                .nodeName(nodeName)
                .nodeOrder(order)
                .assigneeType(AssigneeType.USER.name())
                .assigneeValue(assigneeValue)
                .approvalMode(ApprovalMode.SINGLE.name())
                .emptyAssigneeStrategy(EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN.name())
                .allowSelfApproval(false)
                .allowTransfer(false)
                .allowDelegate(false)
                .allowAddSign(false)
                .allowReturn(false)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build());
    }
}
