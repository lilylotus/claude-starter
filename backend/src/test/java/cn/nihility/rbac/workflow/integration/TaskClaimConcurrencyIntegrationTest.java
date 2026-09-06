package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 候选池任务原子 claim 的真实并发集成测试（design.md 第7节"claim/approve | 未分配候选任务原子
 * claim 后审批"，第8节"候选人只对未分配任务有权；认领后原候选人不能抢着完成"，tasks.md 6.5——
 * 此前只有假设、未有真实并发测试验证）。本类不使用测试专用回滚事务（不同于
 * {@link AbstractWorkflowEngineIntegrationTest} 及其子类）：两个线程需要在各自真实独立、真正
 * 提交的物理事务里同时抢同一个任务，用测试事务把整个方法包起来会让部署/落库数据对其余线程
 * 不可见（与 {@code IdempotencyServiceImplConcurrencyIntegrationTest} 同样的理由）。
 * {@link WorkflowService} 各方法自身已声明 {@code @Transactional}，测试线程直接调用即可各自
 * 拥有独立的物理事务，无需手工 {@code TransactionTemplate}。测试结束后手工清理部署与自有业务
 * 表数据，避免在共享开发库残留。
 */
@SpringBootTest
class TaskClaimConcurrencyIntegrationTest {

    /** 转办/委派/退回测试流程资源路径：levelOne 为固定候选人集合（此测试改造为 2 人候选池）。 */
    private static final String TRANSFER_DELEGATE_RETURN_BPMN = "processes/test-transfer-delegate-return.bpmn20.xml";

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
    }

    /**
     * 两个候选人几乎同时对同一个未分配候选池任务发起 approve：真实并发下必须只有一人真正
     * 认领并完成任务，另一人应收到明确的业务拒绝（而不是各自都成功、或抛出未处理的引擎异常/
     * 空指针），流程也必须只真正往下推进一次。
     */
    @Test
    void concurrentApprove_onUnclaimedCandidatePoolTask_onlyOneSucceeds() throws Exception {
        String processCode = "TEST_CLAIM_RACE_" + UUID.randomUUID();
        Deployment deployment = repositoryService.createDeployment()
                .name("task-claim-concurrency-integration-test")
                .addClasspathResource(TRANSFER_DELEGATE_RETURN_BPMN)
                .deploy();
        deploymentIdToCleanup = deployment.getId();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("候选池并发抢办测试")
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

        // levelOne：2 人候选池（未分配）；levelTwo/levelThree 随便给固定单人，跑不到也无妨
        seedRule(definition.getId(), "levelOne", "第一级审批(候选池)", 1, "881001,881002", now);
        seedRule(definition.getId(), "levelTwo", "第二级审批", 2, "882001", now);
        seedRule(definition.getId(), "levelThree", "第三级审批", 3, "883001", now);

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "候选池并发抢办测试", 889999L, null, null, null,
                definition.getId(), null, null, ExecutionMode.LEGACY_SYNC));
        processInstanceIdToCleanup = started.processInstanceId();

        List<ApprovalTaskEntity> levelOneTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId())
                .eq(ApprovalTaskEntity::getNodeId, "levelOne"));
        assertThat(levelOneTasks).hasSize(1);
        Long taskId = levelOneTasks.get(0).getId();
        assertThat(levelOneTasks.get(0).getAssigneeId()).isNull();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Long> claimAs881001 = claimAttempt(taskId, 881001L, barrier);
        Callable<Long> claimAs881002 = claimAttempt(taskId, 881002L, barrier);

        Future<Long> future1 = executor.submit(claimAs881001);
        Future<Long> future2 = executor.submit(claimAs881002);
        Long result1 = future1.get(30, TimeUnit.SECONDS);
        Long result2 = future2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = Stream.of(result1, result2).filter(Objects::nonNull).count();
        assertThat(successCount).as("两个并发 approve 中应恰好一个真正成功").isEqualTo(1);
        Long winner = result1 != null ? result1 : result2;

        ApprovalTaskEntity finalTask = approvalTaskMapper.selectById(taskId);
        assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(finalTask.getAssigneeId()).isEqualTo(winner);

        // 流程只真正推进一次，不会因为两次 approve 都生效而产生重复的下一节点任务
        List<ApprovalTaskEntity> levelTwoTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId())
                .eq(ApprovalTaskEntity::getNodeId, "levelTwo"));
        assertThat(levelTwoTasks).hasSize(1);
    }

    /**
     * 构造一个"到达同步屏障后立即发起 approve，成功则返回操作人 id，业务拒绝则返回
     * {@code null}"的并发任务；非业务异常（真正的 bug）不吞掉，让 {@code future.get()} 直接
     * 抛出暴露问题。
     */
    private Callable<Long> claimAttempt(Long taskId, Long operatorId, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                workflowService.approve(new ApproveCommand(
                        taskId, operatorId, "同意", "claim-race-" + operatorId + "-" + UUID.randomUUID()));
                return operatorId;
            } catch (BusinessException ex) {
                return null;
            }
        };
    }

    /**
     * 落库一条固定单人/候选池节点审批人规则。
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
