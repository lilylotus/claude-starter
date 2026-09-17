package cn.nihility.rbac.workflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.constant.ApprovalRequestStatus;
import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.constant.ProcessGraphNodeStatus;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.ConditionItemVO;
import cn.nihility.rbac.workflow.dto.CurrentApproverVO;
import cn.nihility.rbac.workflow.dto.OpenNodeVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphEdgeVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphNodeVO;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.service.WorkflowTaskService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
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

    /** 流程定义（不可变发布版本快照）数据访问接口。 */
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;

    /** 审批任务候选人明细数据访问接口。 */
    @Autowired
    private ApprovalTaskCandidateMapper approvalTaskCandidateMapper;

    /** 用户数据访问接口，用于真实插入测试用户以验证展示名解析。 */
    @Autowired
    private UserMapper userMapper;

    /** 审批申请数据访问接口，用于验证已办查询 {@code operationType} 列与
     *  {@code tab_approval_request.operation_type} 的关联（approval-history-detail-entry
     *  change tasks.md 7）。 */
    @Autowired
    private ApprovalRequestMapper approvalRequestMapper;

    /** 角色数据访问接口，用于真实插入测试角色以验证角色候选人展示名解析。 */
    @Autowired
    private RoleMapper roleMapper;

    /** 测试专用自增序号，避免同一测试类内多个方法之间的唯一键冲突。 */
    private static final AtomicLong SEQ = new AtomicLong();

    /** DSL v1 快照：开始 -&gt; 审批 -&gt; 条件（高/低风险两条分支）-&gt; 结束。 */
    private static final String V1_SNAPSHOT_WITH_CONDITION = """
            {
              "processCode": "TEST_GRAPH_V1",
              "processName": "v1 图测试流程",
              "nodes": [
                {"id": "start", "type": "START"},
                {"id": "approve1", "type": "APPROVAL", "name": "审批一"},
                {"id": "cond", "type": "CONDITION"},
                {"id": "approveHigh", "type": "APPROVAL", "name": "高风险审批"},
                {"id": "approveLow", "type": "APPROVAL", "name": "低风险审批"},
                {"id": "end", "type": "END"}
              ],
              "edges": [
                {"from": "start", "to": "approve1"},
                {"from": "approve1", "to": "cond"},
                {"from": "cond", "to": "approveHigh",
                 "condition": {"fieldBizType": "ORG", "field": "riskLevel", "operator": "EQ", "value": "HIGH"}},
                {"from": "cond", "to": "approveLow"},
                {"from": "approveHigh", "to": "end"},
                {"from": "approveLow", "to": "end"}
              ]
            }
            """;

    /** DSL v2 快照：开始 -&gt; 并行分叉（两条并行审批分支）-&gt; 汇合 -&gt; 条件分支 -&gt; 结束。 */
    private static final String V2_SNAPSHOT_WITH_PARALLEL_AND_CONDITION = """
            {
              "schemaVersion": 2,
              "processCode": "TEST_GRAPH_V2",
              "processName": "v2 图测试流程",
              "nodes": [
                {"id": "start", "type": "START", "position": {"x": 0, "y": 0}},
                {"id": "split", "type": "PARALLEL_SPLIT", "joinNodeId": "join", "position": {"x": 100, "y": 0}},
                {"id": "branchA", "type": "APPROVAL", "name": "分支A审批", "position": {"x": 200, "y": -50}},
                {"id": "branchB", "type": "APPROVAL", "name": "分支B审批", "position": {"x": 200, "y": 50}},
                {"id": "join", "type": "PARALLEL_JOIN", "splitNodeId": "split", "position": {"x": 300, "y": 0}},
                {"id": "cond", "type": "CONDITION", "position": {"x": 400, "y": 0}},
                {"id": "approveHigh", "type": "APPROVAL", "name": "高风险审批", "position": {"x": 500, "y": -50}},
                {"id": "approveLow", "type": "APPROVAL", "name": "低风险审批", "position": {"x": 500, "y": 50}},
                {"id": "end", "type": "END", "position": {"x": 600, "y": 0}}
              ],
              "edges": [
                {"id": "e1", "source": "start", "target": "split"},
                {"id": "e2", "source": "split", "target": "branchA"},
                {"id": "e3", "source": "split", "target": "branchB"},
                {"id": "e4", "source": "branchA", "target": "join"},
                {"id": "e5", "source": "branchB", "target": "join"},
                {"id": "e6", "source": "join", "target": "cond"},
                {"id": "e7", "source": "cond", "target": "approveHigh", "priority": 1,
                 "condition": {"logic": "AND", "items": [{"field": "riskLevel", "op": "EQ", "value": "HIGH"}]}},
                {"id": "e8", "source": "cond", "target": "approveLow", "priority": 2},
                {"id": "e9", "source": "approveHigh", "target": "end"},
                {"id": "e10", "source": "approveLow", "target": "end"}
              ]
            }
            """;

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
     * 又被记录一次转办）时，只取最新一条计入已办列表，不重复出现，且携带的
     * {@code action}/{@code remark} 对应最新一条记录本身，而不是更早那条。
     */
    @Test
    void findDoneTasks_shouldKeepOnlyLatestRecordPerTask() {
        Long userId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_DONE", userId);
        Long taskId = insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, LocalDateTime.now());
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);
        insertRecord(processInstanceId, taskId, userId, ApprovalAction.DELEGATE, t1, null, "委派意见");
        insertRecord(processInstanceId, taskId, userId, ApprovalAction.APPROVE, t2, null, "同意意见");

        PageResult<ApprovalTaskVO> done = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 1, 10));

        assertThat(done.getTotal()).isEqualTo(1L);
        assertThat(done.getRecords()).extracting(ApprovalTaskVO::getId).containsExactly(taskId);
        ApprovalTaskVO record = done.getRecords().get(0);
        assertThat(record.getAction()).isEqualTo(ApprovalAction.APPROVE);
        assertThat(record.getRemark()).isEqualTo("同意意见");
    }

    /**
     * 已办分页应按最新一条命中轨迹的发生时间降序稳定排序、无重复，分页边界正确，且总条数
     * {@code total} 与真实满足条件的记录数一致（跨页验证，不受当页 {@code limit} 影响）。
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

        PageResult<ApprovalTaskVO> firstPage = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 1, 2));
        assertThat(firstPage.getTotal()).isEqualTo(3L);
        assertThat(firstPage.getRecords()).extracting(ApprovalTaskVO::getId).containsExactly(task2, task1);

        PageResult<ApprovalTaskVO> secondPage = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 2, 2));
        assertThat(secondPage.getTotal()).isEqualTo(3L);
        assertThat(secondPage.getRecords()).extracting(ApprovalTaskVO::getId).containsExactly(task3);
    }

    /**
     * 已办查询按业务对象类型过滤应下推到 SQL 层（分页与总条数查询均需保持过滤条件一致）：
     * 候选任务跨多个流程实例时，只返回命中 {@code businessType} 的那些，{@code total} 也
     * 只统计命中的部分。
     */
    @Test
    void findDoneTasks_shouldFilterByBusinessType_inDatabase() {
        Long userId = SEQ.incrementAndGet();
        Long instanceA = insertProcessInstance("TEST_DONE_A", userId);
        Long instanceB = insertProcessInstance("TEST_DONE_B", userId);
        Long taskA = insertTask(instanceA, "node1", userId, TaskStatus.COMPLETED, LocalDateTime.now());
        Long taskB = insertTask(instanceB, "node1", userId, TaskStatus.COMPLETED, LocalDateTime.now());
        insertRecord(instanceA, taskA, userId, ApprovalAction.APPROVE, LocalDateTime.now());
        insertRecord(instanceB, taskB, userId, ApprovalAction.APPROVE, LocalDateTime.now());

        PageResult<ApprovalTaskVO> filtered = workflowTaskService.findDoneTasks(userId, new TaskQuery("TEST_DONE_A", 1, 10));

        assertThat(filtered.getTotal()).isEqualTo(1L);
        assertThat(filtered.getRecords()).extracting(ApprovalTaskVO::getId).containsExactly(taskA);
    }

    /**
     * 当前用户没有任何已办记录时应返回空分页对象（{@code records} 为空数组、{@code total}
     * 为 0），而不是抛异常或返回 {@code null}。
     */
    @Test
    void findDoneTasks_shouldReturnEmptyPage_whenNoRecords() {
        Long userId = SEQ.incrementAndGet();

        PageResult<ApprovalTaskVO> result = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 1, 10));

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isNotNull().isEmpty();
    }

    /**
     * 已办查询应携带该记录关联申请的操作类型：{@code operationType} 通过流程实例的
     * {@code business_id}/{@code business_type} 关联到 {@code tab_approval_request.operation_type}
     * （approval-history-detail-entry change tasks.md 7.1）。
     */
    @Test
    void findDoneTasks_shouldExposeOperationType_whenApprovalRequestMatched() {
        Long userId = SEQ.incrementAndGet();
        Long requestId = insertApprovalRequest("ORG", ApprovalOperationType.UPDATE);
        Long processInstanceId = insertProcessInstance("ORG", userId, 1L, requestId);
        Long taskId = insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, LocalDateTime.now());
        insertRecord(processInstanceId, taskId, userId, ApprovalAction.APPROVE, LocalDateTime.now());

        PageResult<ApprovalTaskVO> done = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 1, 10));

        assertThat(done.getRecords()).extracting(ApprovalTaskVO::getId).containsExactly(taskId);
        assertThat(done.getRecords().get(0).getOperationType()).isEqualTo(ApprovalOperationType.UPDATE);
    }

    /**
     * 已办查询记录关联的流程实例在 {@code tab_approval_request} 里找不到匹配行时（LEFT JOIN
     * 未命中，如 {@code business_id} 不对应任何申请行），该记录不应从结果里消失或重复，
     * 其它字段正常返回，只是 {@code operationType} 为空（approval-history-detail-entry
     * change design.md Decision 4、tasks.md 7.2）。
     */
    @Test
    void findDoneTasks_shouldReturnNullOperationType_andKeepRecord_whenApprovalRequestNotMatched() {
        Long userId = SEQ.incrementAndGet();
        // 负数 business_id 保证不会命中任何 tab_approval_request 自增主键（恒为正）。
        Long processInstanceId = insertProcessInstance("ORG", userId, 1L, -SEQ.incrementAndGet());
        Long taskId = insertTask(processInstanceId, "node1", userId, TaskStatus.COMPLETED, LocalDateTime.now());
        insertRecord(processInstanceId, taskId, userId, ApprovalAction.APPROVE, LocalDateTime.now());

        PageResult<ApprovalTaskVO> done = workflowTaskService.findDoneTasks(userId, new TaskQuery(null, 1, 10));

        assertThat(done.getTotal()).isEqualTo(1L);
        assertThat(done.getRecords()).extracting(ApprovalTaskVO::getId).containsExactly(taskId);
        assertThat(done.getRecords().get(0).getOperationType()).isNull();
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

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, userId);

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

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, userId);

        assertThat(detail.getOpenNodes()).isNotNull().isEmpty();
    }

    /**
     * DSL v1 流程：已处理节点应为 {@code COMPLETED} 并关联对应审批轨迹，当前候选节点为
     * {@code CURRENT}，未到达节点（含条件节点本身、条件分支另一侧）为 {@code PENDING}
     * （add-approval-remark-and-process-flowchart change tasks.md 3.4）。
     */
    @Test
    void getProcessDetail_shouldComputeNodeGraphAndStatus_forV1Process() {
        Long applicantId = SEQ.incrementAndGet();
        Long candidateId = SEQ.incrementAndGet();
        Long definitionId = insertProcessDefinition(1, V1_SNAPSHOT_WITH_CONDITION);
        Long processInstanceId = insertProcessInstance("TEST_GRAPH_V1", applicantId, definitionId);
        insertRecord(processInstanceId, null, applicantId, ApprovalAction.APPROVE,
                LocalDateTime.now().minusMinutes(5), "approve1");
        insertTask(processInstanceId, "approveLow", candidateId, TaskStatus.PENDING, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        Map<String, ProcessGraphNodeVO> nodeById = detail.getNodes().stream()
                .collect(Collectors.toMap(ProcessGraphNodeVO::getId, node -> node));
        assertThat(nodeById.keySet())
                .containsExactlyInAnyOrder("start", "approve1", "cond", "approveHigh", "approveLow", "end");
        assertThat(nodeById.get("approve1").getStatus()).isEqualTo(ProcessGraphNodeStatus.COMPLETED);
        assertThat(nodeById.get("approve1").getRecords()).hasSize(1);
        assertThat(nodeById.get("approve1").getCurrentApprovers()).isNotNull().isEmpty();
        assertThat(nodeById.get("approveLow").getStatus()).isEqualTo(ProcessGraphNodeStatus.CURRENT);
        assertThat(nodeById.get("approveHigh").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("approveHigh").getCurrentApprovers()).isNotNull().isEmpty();
        assertThat(nodeById.get("cond").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("cond").getCurrentApprovers()).isNotNull().isEmpty();
        assertThat(nodeById.get("start").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("start").getCurrentApprovers()).isNotNull().isEmpty();
        assertThat(nodeById.get("end").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("end").getCurrentApprovers()).isNotNull().isEmpty();
        assertThat(detail.getEdges()).hasSize(6);
    }

    /**
     * DSL v1 条件边应产出结构化 {@code conditions} 而非拼接文本，{@code fieldBizType} 直接取自
     * DSL 本身携带的 {@code fieldBizType}，默认分支 {@code conditions} 为空列表（不是
     * {@code null}）（add-approval-remark-and-process-flowchart change tasks.md 3b.3）。
     */
    @Test
    void getProcessDetail_shouldExposeStructuredConditions_forV1Process() {
        Long applicantId = SEQ.incrementAndGet();
        Long definitionId = insertProcessDefinition(1, V1_SNAPSHOT_WITH_CONDITION);
        Long processInstanceId = insertProcessInstance("ORG", applicantId, definitionId);

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        ProcessGraphEdgeVO conditionalEdge = detail.getEdges().stream()
                .filter(edge -> "cond".equals(edge.getSource()) && "approveHigh".equals(edge.getTarget()))
                .findFirst().orElseThrow();
        assertThat(conditionalEdge.getConditions()).hasSize(1);
        ConditionItemVO conditionItem = conditionalEdge.getConditions().get(0);
        assertThat(conditionItem.getFieldBizType()).isEqualTo("ORG");
        assertThat(conditionItem.getField()).isEqualTo("riskLevel");
        assertThat(conditionItem.getOperator()).isEqualTo("EQ");
        assertThat(conditionItem.getValue()).isEqualTo("HIGH");
        assertThat(conditionalEdge.getConditionLogic()).isNull();

        ProcessGraphEdgeVO defaultEdge = detail.getEdges().stream()
                .filter(edge -> "cond".equals(edge.getSource()) && "approveLow".equals(edge.getTarget()))
                .findFirst().orElseThrow();
        assertThat(defaultEdge.getConditions()).isNotNull().isEmpty();
    }

    /**
     * 单人审批节点、任务已被认领：{@code currentApprovers} 只有一条 {@code assigned=true}
     * 记录，展示名解析正确（add-approval-remark-and-process-flowchart change design.md
     * Decision 7、tasks.md 3a.3）。
     */
    @Test
    void getProcessDetail_shouldReturnAssignedApprover_whenCurrentNodeTaskIsClaimed() {
        Long applicantId = SEQ.incrementAndGet();
        Long assigneeId = insertUser("已认领处理人");
        Long definitionId = insertProcessDefinition(1, V1_SNAPSHOT_WITH_CONDITION);
        Long processInstanceId = insertProcessInstance("TEST_CURRENT_ASSIGNED", applicantId, definitionId);
        insertRecord(processInstanceId, null, applicantId, ApprovalAction.APPROVE,
                LocalDateTime.now().minusMinutes(5), "approve1");
        insertTask(processInstanceId, "approveLow", assigneeId, TaskStatus.CLAIMED, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        ProcessGraphNodeVO currentNode = detail.getNodes().stream()
                .filter(node -> "approveLow".equals(node.getId())).findFirst().orElseThrow();
        assertThat(currentNode.getStatus()).isEqualTo(ProcessGraphNodeStatus.CURRENT);
        assertThat(currentNode.getCurrentApprovers()).hasSize(1);
        CurrentApproverVO approver = currentNode.getCurrentApprovers().get(0);
        assertThat(approver.isAssigned()).isTrue();
        assertThat(approver.getUserId()).isEqualTo(assigneeId);
        assertThat(approver.getUserName()).contains("已认领处理人");
        assertThat(approver.getRoleCode()).isNull();
        assertThat(approver.getRoleName()).isNull();
    }

    /**
     * 候选组节点、任务未认领、候选人为 {@code USER} 类型（多个）：{@code currentApprovers}
     * 每条 {@code assigned=false}，展示名正确，{@code roleCode}/{@code roleName} 为空
     * （design.md Decision 7、tasks.md 3a.3）。
     */
    @Test
    void getProcessDetail_shouldReturnUserCandidates_whenCurrentNodeTaskIsUnclaimed() {
        Long applicantId = SEQ.incrementAndGet();
        Long candidateUser1 = insertUser("候选人甲");
        Long candidateUser2 = insertUser("候选人乙");
        Long definitionId = insertProcessDefinition(1, V1_SNAPSHOT_WITH_CONDITION);
        Long processInstanceId = insertProcessInstance("TEST_CURRENT_USER_CANDIDATES", applicantId, definitionId);
        insertRecord(processInstanceId, null, applicantId, ApprovalAction.APPROVE,
                LocalDateTime.now().minusMinutes(5), "approve1");
        Long taskId = insertTask(processInstanceId, "approveLow", null, TaskStatus.PENDING, LocalDateTime.now());
        insertUserCandidate(taskId, candidateUser1);
        insertUserCandidate(taskId, candidateUser2);

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        ProcessGraphNodeVO currentNode = detail.getNodes().stream()
                .filter(node -> "approveLow".equals(node.getId())).findFirst().orElseThrow();
        assertThat(currentNode.getStatus()).isEqualTo(ProcessGraphNodeStatus.CURRENT);
        assertThat(currentNode.getCurrentApprovers()).hasSize(2);
        assertThat(currentNode.getCurrentApprovers()).allMatch(approver -> !approver.isAssigned());
        assertThat(currentNode.getCurrentApprovers()).extracting(CurrentApproverVO::getUserId)
                .containsExactlyInAnyOrder(candidateUser1, candidateUser2);
        assertThat(currentNode.getCurrentApprovers()).extracting(CurrentApproverVO::getUserName)
                .allMatch(name -> name != null && (name.contains("候选人甲") || name.contains("候选人乙")));
        assertThat(currentNode.getCurrentApprovers()).extracting(CurrentApproverVO::getRoleCode)
                .containsOnlyNulls();
        assertThat(currentNode.getCurrentApprovers()).extracting(CurrentApproverVO::getRoleName)
                .containsOnlyNulls();
    }

    /**
     * 候选组节点、任务未认领、候选人为 {@code ROLE} 类型：{@code currentApprovers} 展示角色
     * 名称与编码，{@code userId}/{@code userName} 为空，不展开角色候选人背后的具体人员列表
     * （design.md Decision 7、tasks.md 3a.3）。
     */
    @Test
    void getProcessDetail_shouldReturnRoleCandidate_whenCurrentNodeTaskIsUnclaimed() {
        Long applicantId = SEQ.incrementAndGet();
        String roleCode = "TEST_ROLE_CANDIDATE_" + System.nanoTime();
        insertRole(roleCode, "测试候选角色");
        Long definitionId = insertProcessDefinition(1, V1_SNAPSHOT_WITH_CONDITION);
        Long processInstanceId = insertProcessInstance("TEST_CURRENT_ROLE_CANDIDATE", applicantId, definitionId);
        insertRecord(processInstanceId, null, applicantId, ApprovalAction.APPROVE,
                LocalDateTime.now().minusMinutes(5), "approve1");
        Long taskId = insertTask(processInstanceId, "approveLow", null, TaskStatus.PENDING, LocalDateTime.now());
        insertRoleCandidate(taskId, roleCode);

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        ProcessGraphNodeVO currentNode = detail.getNodes().stream()
                .filter(node -> "approveLow".equals(node.getId())).findFirst().orElseThrow();
        assertThat(currentNode.getStatus()).isEqualTo(ProcessGraphNodeStatus.CURRENT);
        assertThat(currentNode.getCurrentApprovers()).hasSize(1);
        CurrentApproverVO approver = currentNode.getCurrentApprovers().get(0);
        assertThat(approver.isAssigned()).isFalse();
        assertThat(approver.getRoleCode()).isEqualTo(roleCode);
        assertThat(approver.getRoleName()).isEqualTo("测试候选角色");
        assertThat(approver.getUserId()).isNull();
        assertThat(approver.getUserName()).isNull();
    }

    /**
     * DSL v2 流程（含并行分叉/汇合、条件分支）：并行块内一个分支已完成、另一分支仍是当前候选，
     * 两者状态应各自独立计算，其余未到达节点均为 {@code PENDING}（tasks.md 3.4）。
     */
    @Test
    void getProcessDetail_shouldComputeNodeGraphAndStatus_forV2ProcessWithParallelAndCondition() {
        Long applicantId = SEQ.incrementAndGet();
        Long candidateId = SEQ.incrementAndGet();
        Long definitionId = insertProcessDefinition(2, V2_SNAPSHOT_WITH_PARALLEL_AND_CONDITION);
        Long processInstanceId = insertProcessInstance("TEST_GRAPH_V2", applicantId, definitionId);
        insertRecord(processInstanceId, null, applicantId, ApprovalAction.APPROVE,
                LocalDateTime.now().minusMinutes(5), "branchA");
        insertTask(processInstanceId, "branchB", candidateId, TaskStatus.PENDING, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        Map<String, ProcessGraphNodeVO> nodeById = detail.getNodes().stream()
                .collect(Collectors.toMap(ProcessGraphNodeVO::getId, node -> node));
        assertThat(nodeById.get("branchA").getStatus()).isEqualTo(ProcessGraphNodeStatus.COMPLETED);
        assertThat(nodeById.get("branchB").getStatus()).isEqualTo(ProcessGraphNodeStatus.CURRENT);
        assertThat(nodeById.get("split").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("join").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("cond").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("approveHigh").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.get("approveLow").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(detail.getEdges()).hasSize(10);
        // v2 节点携带画布坐标
        assertThat(nodeById.get("branchA").getX()).isEqualTo(200d);
    }

    /**
     * DSL v2 条件边应产出结构化 {@code conditions}/{@code conditionLogic}，多条件项按
     * {@code AND} 正确归并；v2 条件项无原生 {@code fieldBizType}，按流程实例绑定的业务对象
     * 类型（{@code ProcessInstanceEntity.businessType}）兜底（add-approval-remark-and-process
     * -flowchart change tasks.md 3b.3）。
     */
    @Test
    void getProcessDetail_shouldExposeStructuredConditions_forV2Process() {
        Long applicantId = SEQ.incrementAndGet();
        Long definitionId = insertProcessDefinition(2, V2_SNAPSHOT_WITH_PARALLEL_AND_CONDITION);
        Long processInstanceId = insertProcessInstance("USER", applicantId, definitionId);

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        ProcessGraphEdgeVO conditionalEdge = detail.getEdges().stream()
                .filter(edge -> "e7".equals(edge.getId())).findFirst().orElseThrow();
        assertThat(conditionalEdge.getConditionLogic()).isEqualTo("AND");
        assertThat(conditionalEdge.getConditions()).hasSize(1);
        ConditionItemVO conditionItem = conditionalEdge.getConditions().get(0);
        assertThat(conditionItem.getField()).isEqualTo("riskLevel");
        assertThat(conditionItem.getOperator()).isEqualTo("EQ");
        assertThat(conditionItem.getValue()).isEqualTo("HIGH");
        // v2 条件项无原生 fieldBizType，按流程实例绑定的业务对象类型（USER）兜底
        assertThat(conditionItem.getFieldBizType()).isEqualTo("USER");

        ProcessGraphEdgeVO defaultEdge = detail.getEdges().stream()
                .filter(edge -> "e8".equals(edge.getId())).findFirst().orElseThrow();
        assertThat(defaultEdge.getConditions()).isNotNull().isEmpty();
        assertThat(defaultEdge.getConditionLogic()).isNull();
    }

    /**
     * 流程已结束（APPROVED）时，节点状态应与审批轨迹精确一致：已处理节点 COMPLETED，其余
     * PENDING，不应出现 CURRENT（流程已无开放任务）。
     */
    @Test
    void getProcessDetail_shouldMatchNodeStatusWithRecords_whenProcessFinished() {
        Long applicantId = SEQ.incrementAndGet();
        Long definitionId = insertProcessDefinition(1, V1_SNAPSHOT_WITH_CONDITION);
        Long processInstanceId = insertProcessInstance("TEST_GRAPH_FINISHED", applicantId, definitionId);
        insertRecord(processInstanceId, null, applicantId, ApprovalAction.APPROVE,
                LocalDateTime.now().minusMinutes(10), "approve1");
        insertRecord(processInstanceId, null, applicantId, ApprovalAction.APPROVE,
                LocalDateTime.now().minusMinutes(5), "approveLow");
        processInstanceMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProcessInstanceEntity>()
                        .eq(ProcessInstanceEntity::getId, processInstanceId)
                        .set(ProcessInstanceEntity::getStatus, ProcessInstanceStatus.APPROVED)
                        .set(ProcessInstanceEntity::getFinishedTime, LocalDateTime.now()));

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        Map<String, ProcessGraphNodeVO> nodeById = detail.getNodes().stream()
                .collect(Collectors.toMap(ProcessGraphNodeVO::getId, node -> node));
        assertThat(nodeById.get("approve1").getStatus()).isEqualTo(ProcessGraphNodeStatus.COMPLETED);
        assertThat(nodeById.get("approveLow").getStatus()).isEqualTo(ProcessGraphNodeStatus.COMPLETED);
        assertThat(nodeById.get("approveHigh").getStatus()).isEqualTo(ProcessGraphNodeStatus.PENDING);
        assertThat(nodeById.values()).noneMatch(node -> ProcessGraphNodeStatus.CURRENT.equals(node.getStatus()));
    }

    /**
     * 申请人本人查看自己发起的流程实例应放行（design.md Decision 3 条件 1）。
     */
    @Test
    void getProcessDetail_shouldAllow_whenViewerIsApplicant() {
        Long applicantId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_VIEWER_APPLICANT", applicantId);

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, applicantId);

        assertThat(detail.getId()).isEqualTo(processInstanceId);
    }

    /**
     * 当前节点候选人（尚未处理过，未出现在审批轨迹中）查看应放行（design.md Decision 3
     * 条件 3，复用 {@code TaskAuthorizationService}）。
     */
    @Test
    void getProcessDetail_shouldAllow_whenViewerIsCurrentCandidate_notYetProcessed() {
        Long applicantId = SEQ.incrementAndGet();
        Long candidateId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_VIEWER_CANDIDATE", applicantId);
        insertTask(processInstanceId, "node1", candidateId, TaskStatus.PENDING, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, candidateId);

        assertThat(detail.getId()).isEqualTo(processInstanceId);
    }

    /**
     * 历史上已处理过该实例某节点的操作人查看应放行（design.md Decision 3 条件 2）。
     */
    @Test
    void getProcessDetail_shouldAllow_whenViewerIsHistoricalOperator() {
        Long applicantId = SEQ.incrementAndGet();
        Long historicalOperatorId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_VIEWER_HISTORY", applicantId);
        insertRecord(processInstanceId, null, historicalOperatorId, ApprovalAction.APPROVE, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, historicalOperatorId);

        assertThat(detail.getId()).isEqualTo(processInstanceId);
    }

    /**
     * 转办场景的原处理人（{@code fromUserId}）查看应放行，即便其本人从未被记为
     * {@code operatorId}（design.md Decision 3 条件 2 明确覆盖转办来源人）。
     */
    @Test
    void getProcessDetail_shouldAllow_whenViewerIsTransferFromUser() {
        Long applicantId = SEQ.incrementAndGet();
        Long fromUserId = SEQ.incrementAndGet();
        Long toUserId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_VIEWER_TRANSFER", applicantId);
        insertTransferRecord(processInstanceId, fromUserId, toUserId, LocalDateTime.now());

        ProcessInstanceDetailVO detail = workflowTaskService.getProcessDetail(processInstanceId, fromUserId);

        assertThat(detail.getId()).isEqualTo(processInstanceId);
    }

    /**
     * 与该实例完全无关的用户查看应被拒绝（落实 approval-runtime-safety 能力"操作授权和访问
     * 控制"需求）。
     */
    @Test
    void getProcessDetail_shouldReject_whenViewerIsUnrelated() {
        Long applicantId = SEQ.incrementAndGet();
        Long unrelatedUserId = SEQ.incrementAndGet();
        Long processInstanceId = insertProcessInstance("TEST_VIEWER_UNRELATED", applicantId);
        insertTask(processInstanceId, "node1", SEQ.incrementAndGet(), TaskStatus.PENDING, LocalDateTime.now());

        assertThatThrownBy(() -> workflowTaskService.getProcessDetail(processInstanceId, unrelatedUserId))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 落库一条最小化的流程实例种子行，绑定默认流程定义 id（{@code 1L}，V1 Flyway 迁移脚本
     * 预置的种子流程定义），不涉及节点图结构的用例沿用既有行为。
     */
    private Long insertProcessInstance(String businessType, Long applicantId) {
        return insertProcessInstance(businessType, applicantId, 1L);
    }

    /**
     * 落库一条最小化的流程实例种子行，绑定指定的流程定义 id。
     */
    private Long insertProcessInstance(String businessType, Long applicantId, Long processDefinitionId) {
        return insertProcessInstance(businessType, applicantId, processDefinitionId, null);
    }

    /**
     * 落库一条最小化的流程实例种子行，绑定指定的流程定义 id 与业务对象 id（用于验证
     * {@code selectDonePage} 通过 {@code business_id}/{@code business_type} 关联
     * {@code tab_approval_request} 取 {@code operationType}，approval-history-detail-entry
     * change tasks.md 7）。
     */
    private Long insertProcessInstance(String businessType, Long applicantId, Long processDefinitionId, Long businessId) {
        LocalDateTime now = LocalDateTime.now();
        ProcessInstanceEntity instance = ProcessInstanceEntity.builder()
                .processDefinitionId(processDefinitionId)
                .businessType(businessType)
                .businessId(businessId)
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
     * 落库一条最小化的审批申请种子行，返回其自增主键 id（供流程实例的 {@code business_id}
     * 关联使用），用于验证已办查询携带的 {@code operationType} 与本行的
     * {@code operation_type} 一致（approval-history-detail-entry change tasks.md 7.1）。
     */
    private Long insertApprovalRequest(String bizType, String operationType) {
        LocalDateTime now = LocalDateTime.now();
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .bizType(bizType)
                .operationType(operationType)
                .status(ApprovalRequestStatus.APPROVED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        approvalRequestMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 落库一条最小化的流程定义种子行，用于测试完整节点/连线图的解析与状态计算。
     */
    private Long insertProcessDefinition(Integer schemaVersion, String modelJsonSnapshot) {
        LocalDateTime now = LocalDateTime.now();
        long seq = SEQ.incrementAndGet();
        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(seq)
                .processCode("TEST_GRAPH_DEFINITION_" + seq)
                .version(1)
                .schemaVersion(schemaVersion)
                .flowableDefinitionKey("testGraphProcess" + seq)
                .modelJsonSnapshot(modelJsonSnapshot)
                .status("PUBLISHED")
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        processDefinitionMapper.insert(definition);
        return definition.getId();
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
     * 落库一条最小化的审批轨迹种子行，不关联具体节点 id。
     */
    private void insertRecord(
            Long processInstanceId, Long taskId, Long operatorId, String action, LocalDateTime createTime) {
        insertRecord(processInstanceId, taskId, operatorId, action, createTime, null);
    }

    /**
     * 落库一条最小化的审批轨迹种子行，关联指定节点 id（用于验证完整节点图的状态计算）。
     */
    private void insertRecord(
            Long processInstanceId, Long taskId, Long operatorId, String action, LocalDateTime createTime,
            String nodeId) {
        insertRecord(processInstanceId, taskId, operatorId, action, createTime, nodeId, null);
    }

    /**
     * 落库一条审批轨迹种子行，关联指定节点 id 与处理意见（用于验证已办查询携带的
     * {@code action}/{@code remark} 正确对应触发该记录的那次审批操作）。
     */
    private void insertRecord(
            Long processInstanceId, Long taskId, Long operatorId, String action, LocalDateTime createTime,
            String nodeId, String remark) {
        ApprovalRecordEntity record = ApprovalRecordEntity.builder()
                .processInstanceId(processInstanceId)
                .taskId(taskId)
                .nodeId(nodeId)
                .operatorId(operatorId)
                .action(action)
                .remark(remark)
                .createBy("test")
                .createTime(createTime)
                .updateBy("test")
                .updateTime(createTime)
                .build();
        approvalRecordMapper.insert(record);
    }

    /**
     * 插入一条真实的 {@code tab_user} 行（启用状态），返回其自增主键 id，供展示名解析断言
     * 使用（add-approval-remark-and-process-flowchart change tasks.md 3a.3）。
     */
    private Long insertUser(String name) {
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .name(name)
                .code("TEST_CURRENT_APPROVER_" + System.nanoTime())
                .gender("unknown")
                .showOrder(0)
                .status(UserStatus.ENABLED)
                .version(1L)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 插入一条真实的 {@code tab_role} 行（启用状态），供角色候选人展示名解析断言使用。
     */
    private void insertRole(String code, String name) {
        LocalDateTime now = LocalDateTime.now();
        RoleEntity role = RoleEntity.builder()
                .name(name)
                .code(code)
                .showOrder(0)
                .status(RoleStatus.ENABLED)
                .version(1L)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        roleMapper.insert(role);
    }

    /**
     * 落库一条 {@code USER} 类型的审批任务候选人明细种子行。
     */
    private void insertUserCandidate(Long taskId, Long userId) {
        insertCandidate(taskId, CandidateType.USER, String.valueOf(userId));
    }

    /**
     * 落库一条 {@code ROLE} 类型的审批任务候选人明细种子行。
     */
    private void insertRoleCandidate(Long taskId, String roleCode) {
        insertCandidate(taskId, CandidateType.ROLE, roleCode);
    }

    /**
     * 落库一条审批任务候选人明细种子行。
     */
    private void insertCandidate(Long taskId, String candidateType, String candidateValue) {
        LocalDateTime now = LocalDateTime.now();
        ApprovalTaskCandidateEntity candidate = ApprovalTaskCandidateEntity.builder()
                .taskId(taskId)
                .candidateType(candidateType)
                .candidateValue(candidateValue)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        approvalTaskCandidateMapper.insert(candidate);
    }

    /**
     * 落库一条转办审批轨迹种子行：{@code operatorId} 记为新处理人（转办后续动作的归属方），
     * {@code fromUserId} 记为原处理人，专用于验证"转办来源人"参与关系校验分支。
     */
    private void insertTransferRecord(Long processInstanceId, Long fromUserId, Long toUserId, LocalDateTime createTime) {
        ApprovalRecordEntity record = ApprovalRecordEntity.builder()
                .processInstanceId(processInstanceId)
                .operatorId(toUserId)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .action(ApprovalAction.TRANSFER)
                .createBy("test")
                .createTime(createTime)
                .updateBy("test")
                .updateTime(createTime)
                .build();
        approvalRecordMapper.insert(record);
    }
}
