package cn.nihility.rbac.workflow.integration;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会签（Multi-Instance）与转办/委派/加签/退回操作对着真实 Flowable 引擎的集成测试公共基类
 * （workflow-approval-engine change tasks.md 13.3）。每个测试方法在自己的 Spring 测试事务内
 * 动态部署一个测试专用 BPMN 资源、落库对应的 {@code tab_wf_process_model}/
 * {@code tab_wf_process_definition}/{@code tab_wf_node_assignee_rule} 种子数据、驱动
 * {@link WorkflowService} 完成场景操作，方法结束后由 Spring 测试框架整体回滚事务
 * （类上 {@link Transactional}，默认 rollback），Flowable 引擎的部署/运行时数据与本项目自有
 * 的 {@code tab_wf_*} 业务表共用同一个数据源与事务管理器，回滚后不会在共享的开发数据库
 * （见 {@code application.yml} 中的 {@code spring.datasource.url}）留下任何测试脏数据，
 * 因此不需要额外的手工清理逻辑。
 */
@SpringBootTest
@Transactional
abstract class AbstractWorkflowEngineIntegrationTest {

    /** Workflow 引擎抽象服务，测试驱动业务操作的唯一入口。 */
    @Autowired
    protected WorkflowService workflowService;

    /** Flowable 流程仓库服务，用于测试动态部署 BPMN 资源。 */
    @Autowired
    protected RepositoryService repositoryService;

    /** Flowable 运行时服务，用于测试直接查询执行/任务状态做断言。 */
    @Autowired
    protected RuntimeService runtimeService;

    /** Flowable 用户任务服务，用于测试直接查询任务状态做断言。 */
    @Autowired
    protected TaskService taskService;

    /** 流程模型数据访问接口。 */
    @Autowired
    protected ProcessModelMapper processModelMapper;

    /** 流程定义数据访问接口。 */
    @Autowired
    protected ProcessDefinitionMapper processDefinitionMapper;

    /** 节点审批人规则数据访问接口。 */
    @Autowired
    protected NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    /** 流程实例数据访问接口。 */
    @Autowired
    protected ProcessInstanceMapper processInstanceMapper;

    /** 审批任务数据访问接口。 */
    @Autowired
    protected ApprovalTaskMapper approvalTaskMapper;

    /** 审批任务候选人明细数据访问接口。 */
    @Autowired
    protected ApprovalTaskCandidateMapper approvalTaskCandidateMapper;

    /** 审批轨迹数据访问接口。 */
    @Autowired
    protected ApprovalRecordMapper approvalRecordMapper;

    /** 测试专用流程编码自增序号，避免同一测试类内多个方法之间的流程编码冲突。 */
    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    /**
     * 单个节点的种子数据描述，{@code assigneeType} 固定为 {@link AssigneeType#USER}
     * （固定候选人列表，不依赖 {@code AssigneeResolver} 动态解析组织/角色数据，聚焦验证
     * 会签完成条件判定与任务处理类操作本身）。
     *
     * @param nodeId        BPMN 用户任务节点 id，须与测试 BPMN 资源中的 {@code userTask} id 一致
     * @param nodeName      节点名称
     * @param mode          审批模式
     * @param percent       会签通过比例，仅 {@code mode=PERCENT} 使用
     * @param assigneeValue 固定候选人用户 id（逗号分隔）
     * @param allowTransfer 是否允许转办
     * @param allowDelegate 是否允许委派
     * @param allowAddSign  是否允许加签
     * @param allowReturn   是否允许退回到该节点
     */
    protected record NodeSeed(
            String nodeId,
            String nodeName,
            ApprovalMode mode,
            Integer percent,
            String assigneeValue,
            boolean allowTransfer,
            boolean allowDelegate,
            boolean allowAddSign,
            boolean allowReturn) {

        /**
         * 构造一个不允许转办/委派/加签/退回的节点种子（多数会签模式测试只关心完成条件判定）。
         */
        static NodeSeed simple(String nodeId, String nodeName, ApprovalMode mode, Integer percent, String assigneeValue) {
            return new NodeSeed(nodeId, nodeName, mode, percent, assigneeValue, false, false, false, false);
        }
    }

    /**
     * 部署 + 落库结果。
     *
     * @param processModelId      {@code tab_wf_process_model.id}
     * @param processDefinitionId {@code tab_wf_process_definition.id}
     * @param processCode         本次测试分配的业务流程编码
     */
    protected record ProcessFixture(Long processModelId, Long processDefinitionId, String processCode) {
    }

    /**
     * 部署测试 BPMN 资源，并落库对应的流程模型/流程定义/节点审批人规则种子数据。
     *
     * @param classpathResource 测试 BPMN 资源的 classpath 路径
     * @param nodes             节点种子数据，顺序即节点顺序
     * @return 部署 + 落库结果
     */
    protected ProcessFixture deployAndSeed(String classpathResource, List<NodeSeed> nodes) {
        Deployment deployment = repositoryService.createDeployment()
                .name("workflow-engine-integration-test")
                .addClasspathResource(classpathResource)
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        String processCode = "TEST_WF_ENGINE_" + PROCESS_CODE_SEQ.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("集成测试流程-" + flowableDefinition.getKey())
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        processModelMapper.insert(model);

        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(model.getId())
                .processCode(processCode)
                .version(1)
                .flowableDefinitionKey(flowableDefinition.getKey())
                .flowableDefinitionId(flowableDefinition.getId())
                .modelJsonSnapshot("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .publishedBy("test")
                .publishedTime(now)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        processDefinitionMapper.insert(definition);

        model.setCurrentDefinitionId(definition.getId());
        model.setUpdateTime(LocalDateTime.now());
        processModelMapper.updateById(model);

        int order = 0;
        for (NodeSeed node : nodes) {
            order++;
            NodeAssigneeRuleEntity rule = NodeAssigneeRuleEntity.builder()
                    .processDefinitionId(definition.getId())
                    .nodeId(node.nodeId())
                    .nodeName(node.nodeName())
                    .nodeOrder(order)
                    .assigneeType(AssigneeType.USER.name())
                    .assigneeValue(node.assigneeValue())
                    .approvalMode(node.mode().name())
                    .approvalPercent(node.percent())
                    .emptyAssigneeStrategy(EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN.name())
                    .allowSelfApproval(false)
                    .allowTransfer(node.allowTransfer())
                    .allowDelegate(node.allowDelegate())
                    .allowAddSign(node.allowAddSign())
                    .allowReturn(node.allowReturn())
                    .createBy("test")
                    .createTime(now)
                    .updateBy("test")
                    .updateTime(now)
                    .build();
            nodeAssigneeRuleMapper.insert(rule);
        }

        return new ProcessFixture(model.getId(), definition.getId(), processCode);
    }

    /**
     * 查询指定流程实例在指定节点下的全部审批任务行，按主键升序排列。
     */
    protected List<ApprovalTaskEntity> tasksOf(Long processInstanceId, String nodeId) {
        return approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .eq(ApprovalTaskEntity::getNodeId, nodeId)
                .orderByAsc(ApprovalTaskEntity::getId));
    }

    /**
     * 重新查询流程实例最新状态。
     */
    protected ProcessInstanceEntity instanceOf(Long processInstanceId) {
        return processInstanceMapper.selectById(processInstanceId);
    }

    /**
     * 查询指定流程实例的完整审批轨迹，按主键升序排列。
     */
    protected List<ApprovalRecordEntity> recordsOf(Long processInstanceId) {
        return approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                .orderByAsc(ApprovalRecordEntity::getId));
    }
}
