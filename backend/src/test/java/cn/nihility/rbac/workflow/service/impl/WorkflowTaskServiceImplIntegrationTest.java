package cn.nihility.rbac.workflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.OpenNodeVO;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.service.WorkflowTaskService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link WorkflowTaskServiceImpl} 待办/已办分页查询、流程实例详情开放节点聚合的真实数据库
 * 集成测试（production-approval-lifecycle change tasks.md 6.9）：验证排序/去重/分页确实下推
 * 到 {@code ApprovalTaskMapper#selectTodoPage}/{@code #selectDonePage} 的 SQL 层，而不是
 * 加载全量到 Java 后内存过滤分页。每个测试方法在独立事务内直接落库最小化的
 * {@code tab_wf_process_instance}/{@code tab_wf_approval_task}/{@code tab_wf_approval_record}
 * 种子行，不依赖真实 Flowable 部署（不涉及流程推进，只验证查询层）。
 */
@SpringBootTest
@Transactional
class WorkflowTaskServiceImplIntegrationTest {

    /** 待办/已办服务。 */
    @Autowired
    private WorkflowTaskService workflowTaskService;

    /** 审批任务数据访问接口。 */
    @Autowired
    private ApprovalTaskMapper approvalTaskMapper;

    /** 审批轨迹数据访问接口。 */
    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    /** 流程实例数据访问接口。 */
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;

    /** 测试专用自增序号，避免同一测试类内多个方法之间的唯一键冲突。 */
    private static final AtomicLong SEQ = new AtomicLong();

    /**
     * 待办分页应按 {@code create_time DESC, id DESC} 稳定排序、无重复，且分页边界正确
     * （SQL 层完成，不是 Java 内存排序分页）。
     */
    @Test
    void findTodoTasks_shouldSortByTimeAndIdDesc_andPaginateFromDatabase() {
        Long userId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_TODO", userId);
        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        Long task1 = insertTask(processInstanceId, "node1", userId, TaskStatus.PENDING, base);
        Long task2 = insertTask(processInstanceId, "node1", userId, TaskStatus.PENDING, base.plusMinutes(1));
        Long task3 = insertTask(processInstanceId, "node1", userId, TaskStatus.PENDING, base.plusMinutes(2));

        List<ApprovalTaskVO> firstPage = workflowTaskService.findTodoTasks(userId, new TaskQuery(null, 1, 2));
        assertThat(firstPage).extracting(ApprovalTaskVO::getId).containsExactly(task3, task2);

        List<ApprovalTaskVO> secondPage = workflowTaskService.findTodoTasks(userId, new TaskQuery(null, 2, 2));
        assertThat(secondPage).extracting(ApprovalTaskVO::getId).containsExactly(task1);

        // 两页合计恰好 3 条，互不重复
        assertThat(firstPage.size() + secondPage.size()).isEqualTo(3);
    }

    /**
     * 待办分页的业务对象类型过滤应下推到 SQL 层：候选任务 id 集合跨多个流程实例时，只返回
     * 命中 {@code businessType} 的那些。
     */
    @Test
    void findTodoTasks_shouldFilterByBusinessType_inDatabase() {
        Long userId = SEQ.incrementAndGet();
        Long instanceA = insertProcessInstance("TEST_TODO_A", userId);
        Long instanceB = insertProcessInstance("TEST_TODO_B", userId);
        Long taskA = insertTask(instanceA, "node1", userId, TaskStatus.PENDING, LocalDateTime.now());
        insertTask(instanceB, "node1", userId, TaskStatus.PENDING, LocalDateTime.now());

        List<ApprovalTaskVO> filtered = workflowTaskService.findTodoTasks(userId, new TaskQuery("TEST_TODO_A", 1, 10));

        assertThat(filtered).extracting(ApprovalTaskVO::getId).containsExactly(taskA);
    }

    /**
     * 已办查询"每组最新一条记录"语义：同一任务存在多条不同动作的审批轨迹记录（如先委派后
     * 又被记录一次转办）时，只取最新一条计入已办列表，不重复出现。
     */
    @Test
    void findDoneTasks_shouldKeepOnlyLatestRecordPerTask() {
        Long userId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_DONE", userId);
        Long taskId = insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, LocalDateTime.now());
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);
        insertRecord(processInstanceId, taskId, userId, ApprovalAction.DELEGATE, t1);
        insertRecord(processInstanceId, taskId, userId, ApprovalAction.APPROVE, t2);

        List<ApprovalTaskVO> done = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 1, 10));

        assertThat(done).extracting(ApprovalTaskVO::getId).containsExactly(taskId);
    }

    /**
     * 已办分页应按最新一条命中轨迹的发生时间降序稳定排序、无重复，且分页边界正确。
     */
    @Test
    void findDoneTasks_shouldSortByLatestRecordTime_andPaginateFromDatabase() {
        Long userId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_DONE_SORT", userId);
        LocalDateTime base = LocalDateTime.now().minusMinutes(30);

        // task1：两条记录，最新一条（APPROVE）落在 base+10 分钟
        Long task1 = insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, base);
        insertRecord(processInstanceId, task1, userId, ApprovalAction.DELEGATE, base.plusMinutes(1));
        insertRecord(processInstanceId, task1, userId, ApprovalAction.APPROVE, base.plusMinutes(10));

        // task2：单条记录，落在 base+20 分钟，晚于 task1 的最新记录
        Long task2 = insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, base);
        insertRecord(processInstanceId, task2, userId, ApprovalAction.APPROVE, base.plusMinutes(20));

        // task3：单条记录，落在 base+5 分钟，早于 task1 的最新记录
        Long task3 = insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, base);
        insertRecord(processInstanceId, task3, userId, ApprovalAction.REJECT, base.plusMinutes(5));

        List<ApprovalTaskVO> firstPage = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 1, 2));
        assertThat(firstPage).extracting(ApprovalTaskVO::getId).containsExactly(task2, task1);

        List<ApprovalTaskVO> secondPage = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 2, 2));
        assertThat(secondPage).extracting(ApprovalTaskVO::getId).containsExactly(task3);
    }

    /**
     * 并行分叉场景下，流程实例详情的 {@code openNodes} 应聚合出全部当前开放节点，而不是只
     * 反映单一 {@code currentNodeId}。
     */
    @Test
    void getProcessDetail_shouldAggregateMultipleOpenNodes_inParallelScenario() {
        Long userId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_PARALLEL", userId);
        insertTask(processInstanceId, "branchA", userId, TaskStatus.PENDING, LocalDateTime.now());
        insertTask(processInstanceId, "branchB", userId + 1, TaskStatus.CLAIMED, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId);

        assertThat(detail.getOpenNodes()).extracting(OpenNodeVO::getNodeId)
                .containsExactlyInAnyOrder("branchA", "branchB");
    }

    /**
     * 流程已结束时开放节点集合应为空列表，而不是 {@code null}。
     */
    @Test
    void getProcessDetail_shouldReturnEmptyOpenNodes_whenProcessFinished() {
        Long userId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_FINISHED", userId);
        processInstanceMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProcessInstanceEntity>()
                        .eq(ProcessInstanceEntity::getId, processInstanceId)
                        .set(ProcessInstanceEntity::getStatus, ProcessInstanceStatus.APPROVED)
                        .set(ProcessInstanceEntity::getFinishedTime, LocalDateTime.now()));
        insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId);

        assertThat(detail.getOpenNodes()).isNotNull().isEmpty();
    }

    /**
     * 落库一条最小化的流程实例种子行。
     */
    private Long insertProcessInstance(String businessType, Long applicantId) {
        LocalDateTime now = LocalDateTime.now();
        ProcessInstanceEntity instance = ProcessInstanceEntity.builder()
                .processDefinitionId(1L)
                .businessType(businessType)
                .applicantId(applicantId)
                .status(ProcessInstanceStatus.RUNNING)
                .startedTime(now)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        processInstanceMapper.insert(instance);
        return instance.getId();
    }

    /**
     * 落库一条最小化的审批任务种子行，直接指定处理人 {@code assigneeId}。
     */
    private Long insertTask(Long processInstanceId, String nodeId, Long assigneeId, String status, LocalDateTime createTime) {
        ApprovalTaskEntity task = ApprovalTaskEntity.builder()
                .flowableTaskId("TEST_TASK_" + SEQ.incrementAndGet() + "_" + System.nanoTime())
                .processInstanceId(processInstanceId)
                .nodeId(nodeId)
                .nodeName(nodeId)
                .assigneeId(assigneeId)
                .status(status)
                .createBy("test")
                .createTime(createTime)
                .updateBy("test")
                .updateTime(createTime)
                .build();
        approvalTaskMapper.insert(task);
        return task.getId();
    }

    /**
     * 落库一条最小化的审批轨迹种子行。
     */
    private void insertRecord(
            Long processInstanceId, Long taskId, Long operatorId, String action, LocalDateTime createTime) {
        ApprovalRecordEntity record = ApprovalRecordEntity.builder()
                .processInstanceId(processInstanceId)
                .taskId(taskId)
                .operatorId(operatorId)
                .action(action)
                .createBy("test")
                .createTime(createTime)
                .updateBy("test")
                .updateTime(createTime)
                .build();
        approvalRecordMapper.insert(record);
    }
}
