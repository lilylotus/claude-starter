package cn.nihility.rbac.workflow.dslv2.engine;

import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.support.NodeAssigneeResolutionService;
import cn.nihility.rbac.workflow.assignee.support.ResolvedAssignees;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceExecutionListener;
import cn.nihility.rbac.workflow.engine.flowable.support.WorkflowSpringContext;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.NodeRunEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.NodeRunMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * DSL v2 会签（Multi-Instance）节点候选人集合准备 + 轮次开启监听器，挂在多实例
 * {@code userTask} 的 {@code flowable:executionListener event="start"} 上（v1
 * {@link WorkflowMultiInstanceExecutionListener} 的姊妹实现，新建而非复用/修改，v1 存量流程
 * 行为不受影响，production-approval-lifecycle change design.md Decision 3/7，tasks.md 6.3）。
 * 除了准备候选人集合流程变量（{@code approvers_<nodeId>}，命名约定与 v1 完全一致）外，本监听器
 * 额外：
 * <ol>
 *   <li>按 {@code (instanceId, nodeId)} 现有最大 {@code round_no} 递增新建一行
 *       {@code tab_wf_node_run}（重入节点/退回后重新进入会产生新轮次，计票与上一轮隔离）；</li>
 *   <li>按 {@link VoteThresholdCalculator} 用整数公式计算通过阈值 K，写入 miBody 执行作用域的
 *       局部变量 {@code voteThreshold}，同时初始化 {@code voteAgreeCount=0}，供编译期固化的
 *       完成条件表达式 {@code ${voteAgreeCount >= voteThreshold}} 使用；</li>
 *   <li>写入 {@code voteNodeRunId} 局部变量，供
 *       {@link WorkflowV2MultiInstanceTaskListener} 在每个子任务 {@code create} 事件时读取，
 *       落库 {@code tab_wf_approval_task.node_run_id}。</li>
 * </ol>
 * 反对票计票与节点/流程终止判定不在本监听器完成，统一由
 * {@code FlowableWorkflowService.completeTask} 在调用 {@code taskService.complete} 前后以
 * Java 代码完成（design.md 第7节"变量隔离到节点执行/轮次"，避免在监听器与业务代码两处各写
 * 一遍计票逻辑）。
 */
public class WorkflowV2MultiInstanceExecutionListener implements ExecutionListener {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(WorkflowV2MultiInstanceExecutionListener.class);

    /** 会签轮次状态：运行中。 */
    private static final String RUN_STATUS_RUNNING = "RUNNING";

    /** 空审批人策略解析为空时使用的哨兵用户 id 文本，与 v1
     *  {@link WorkflowMultiInstanceExecutionListener#EMPTY_SENTINEL_USER_ID} 保持完全一致
     *  （同一套运维重分配基础设施复用同一个哨兵约定）。 */
    public static final String EMPTY_SENTINEL_USER_ID = WorkflowMultiInstanceExecutionListener.EMPTY_SENTINEL_USER_ID;

    /**
     * {@inheritDoc}
     */
    @Override
    public void notify(DelegateExecution execution) {
        try {
            doNotify(execution);
        } catch (RuntimeException ex) {
            log.error("WorkflowV2MultiInstanceExecutionListener 处理节点 {} 时发生异常，候选人集合置空",
                    execution.getCurrentActivityId(), ex);
            execution.setVariableLocal(collectionVariableName(execution.getCurrentActivityId()), List.of());
        }
    }

    /**
     * 实际处理逻辑，异常由 {@link #notify(DelegateExecution)} 统一兜底捕获。
     */
    private void doNotify(DelegateExecution execution) {
        // Flowable 对挂在多实例 userTask 上的 event="start" 执行监听器，除了在 miBody
        // 创建、个体实例尚未展开前触发一次外，还会对每个个体子执行各自"进入"同一活动定义再
        // 触发一次（与 v1 {@code WorkflowMultiInstanceExecutionListener} 文档描述的"只触发一次"
        // 不完全一致，但 v1 因重复设置同一份候选人集合值天然幂等而未受影响）。本监听器的轮次
        // 开启逻辑不是幂等操作（重复触发会创建多个 round 并让候选人各自绑定到不同轮次，
        // 已通过真实集成测试复现确认），必须只在 miBody 根执行触发时才处理，个体子执行触发时
        // 直接跳过（production-approval-lifecycle change tasks.md 6.3）。
        if (!execution.isMultiInstanceRoot()) {
            return;
        }
        String nodeId = execution.getCurrentActivityId();

        ProcessDefinitionMapper processDefinitionMapper = WorkflowSpringContext.getBean(ProcessDefinitionMapper.class);
        ProcessDefinitionEntity processDefinition = processDefinitionMapper.selectOne(
                new LambdaQueryWrapper<ProcessDefinitionEntity>()
                        .eq(ProcessDefinitionEntity::getFlowableDefinitionId, execution.getProcessDefinitionId())
                        .last("LIMIT 1"));
        if (processDefinition == null) {
            log.warn("未找到 flowableDefinitionId={} 对应的 tab_wf_process_definition 记录，会签节点 {} 候选人集合置空",
                    execution.getProcessDefinitionId(), nodeId);
            execution.setVariableLocal(collectionVariableName(nodeId), List.of());
            return;
        }

        NodeAssigneeRuleMapper ruleMapper = WorkflowSpringContext.getBean(NodeAssigneeRuleMapper.class);
        NodeAssigneeRuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<NodeAssigneeRuleEntity>()
                .eq(NodeAssigneeRuleEntity::getProcessDefinitionId, processDefinition.getId())
                .eq(NodeAssigneeRuleEntity::getNodeId, nodeId)
                .last("LIMIT 1"));
        if (rule == null) {
            log.warn("未找到流程定义 {} 会签节点 {} 对应的审批人规则，候选人集合置空", processDefinition.getId(), nodeId);
            execution.setVariableLocal(collectionVariableName(nodeId), List.of());
            return;
        }

        ProcessInstanceEntity instance = resolveProcessInstance(execution);

        AssigneeResolveContext context = new AssigneeResolveContext(
                instance == null ? null : instance.getId(),
                nodeId,
                rule.getAssigneeValue(),
                instance == null ? null : instance.getApplicantId(),
                instance == null ? null : instance.getApplicantOrgId(),
                rule.getAssigneeOrgSource(),
                rule.getTargetOrgId());
        NodeAssigneeResolutionService resolutionService = WorkflowSpringContext.getBean(NodeAssigneeResolutionService.class);
        ResolvedAssignees resolved = resolutionService.resolve(rule, context);

        switch (resolved.kind()) {
            case DIRECT, WORKFLOW_ADMIN -> applyCollectionAndOpenRound(execution, rule, resolved, instance);
            case AUTO_SKIP -> autoSkip(execution, rule, instance);
            case REJECT -> autoReject(execution, rule, instance);
            case BLOCKED -> blockPendingAssignment(execution, rule, instance);
        }
    }

    /**
     * 正常解析出候选人（或转流程管理员兜底命中）：写入候选人集合变量，并开启新一轮
     * {@code tab_wf_node_run}。
     */
    private void applyCollectionAndOpenRound(
            DelegateExecution execution,
            NodeAssigneeRuleEntity rule,
            ResolvedAssignees resolved,
            ProcessInstanceEntity instance) {
        if (!resolved.hasAssignees()) {
            log.error("会签节点 {} 空审批人策略 {} 兜底后仍未解析出候选人，集合置空",
                    rule.getNodeId(), resolved.kind());
        }
        List<String> userIdTexts = resolved.userIds().stream().map(String::valueOf).toList();
        execution.setVariableLocal(collectionVariableName(rule.getNodeId()), userIdTexts);
        openRound(execution, rule, instance, userIdTexts.size());
    }

    /**
     * 空审批人策略 {@code BLOCK}/{@code FALLBACK_ROLE}（兜底仍为空）：候选人集合置为单元素
     * 哨兵集合，避免 Flowable 对空集合多实例节点"立即自动完成"的已知行为；轮次仍按哨兵数量
     * （1）开启，运维重分配为真实候选人后由
     * {@link WorkflowV2ReassignmentService} 同步更新轮次总票数与阈值。
     */
    private void blockPendingAssignment(DelegateExecution execution, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance) {
        execution.setVariableLocal(collectionVariableName(rule.getNodeId()), List.of(EMPTY_SENTINEL_USER_ID));
        if (instance != null) {
            instance.setExceptionCode("ASSIGNEE_EMPTY");
            instance.setUpdateTime(LocalDateTime.now());
            WorkflowSpringContext.getBean(ProcessInstanceMapper.class).updateById(instance);
        }
        openRound(execution, rule, instance, 1);
        log.warn("会签节点 {} 空审批人策略 BLOCK 兜底后仍未解析出候选人，使用哨兵候选人占位，等待运维重分配", rule.getNodeId());
    }

    /**
     * 新建本轮 {@code tab_wf_node_run} 行（{@code round_no} 在同一 {@code (instanceId,
     * nodeId)} 下递增，重入节点计票与历史轮次隔离），按整数公式计算通过阈值 K，写入 miBody
     * 执行作用域局部变量供完成条件表达式与
     * {@code FlowableWorkflowService.completeTask} 计票读取。
     */
    private void openRound(DelegateExecution execution, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance, int totalCount) {
        NodeRunMapper nodeRunMapper = WorkflowSpringContext.getBean(NodeRunMapper.class);
        Long instanceId = instance == null ? null : instance.getId();
        NodeRunEntity latest = instanceId == null ? null : nodeRunMapper.selectOne(
                new LambdaQueryWrapper<NodeRunEntity>()
                        .eq(NodeRunEntity::getInstanceId, instanceId)
                        .eq(NodeRunEntity::getNodeId, rule.getNodeId())
                        .orderByDesc(NodeRunEntity::getRoundNo)
                        .last("LIMIT 1"));
        int roundNo = latest == null ? 1 : latest.getRoundNo() + 1;

        LocalDateTime now = LocalDateTime.now();
        NodeRunEntity nodeRun = NodeRunEntity.builder()
                .instanceId(instanceId)
                .nodeId(rule.getNodeId())
                .executionId(execution.getId())
                .roundNo(roundNo)
                .totalCount(totalCount)
                .agreeCount(0)
                .rejectCount(0)
                .runStatus(RUN_STATUS_RUNNING)
                .revision(1L)
                .createBy("system")
                .createTime(now)
                .updateBy("system")
                .updateTime(now)
                .build();
        nodeRunMapper.insert(nodeRun);

        ApprovalMode mode = ApprovalMode.valueOf(rule.getApprovalMode());
        int threshold = VoteThresholdCalculator.threshold(mode, rule.getApprovalPercent(), totalCount);
        execution.setVariableLocal("voteAgreeCount", 0);
        execution.setVariableLocal("voteThreshold", threshold);
        execution.setVariableLocal("voteNodeRunId", nodeRun.getId());
    }

    /**
     * 按 businessKey 反查流程实例，与 v1 {@link WorkflowMultiInstanceExecutionListener} 完全
     * 一致的反查策略（流程刚发起、第一个节点即为会签节点时也能查到正确的流程实例）。
     */
    private ProcessInstanceEntity resolveProcessInstance(DelegateExecution execution) {
        ProcessInstanceMapper processInstanceMapper = WorkflowSpringContext.getBean(ProcessInstanceMapper.class);
        String businessKey = execution.getProcessInstanceBusinessKey();
        if (StringUtils.hasText(businessKey)) {
            try {
                return processInstanceMapper.selectById(Long.valueOf(businessKey));
            } catch (NumberFormatException ex) {
                log.warn("流程实例 {} 的 businessKey={} 无法解析为 tab_wf_process_instance 主键，回退按 flowableInstanceId 反查",
                        execution.getProcessInstanceId(), businessKey);
            }
        }
        return processInstanceMapper.selectOne(new LambdaQueryWrapper<ProcessInstanceEntity>()
                .eq(ProcessInstanceEntity::getFlowableInstanceId, execution.getProcessInstanceId())
                .last("LIMIT 1"));
    }

    /**
     * 空审批人策略 {@code AUTO_SKIP}：DSL v2 当前不会产生该取值（{@code EmptyPolicy} 只有
     * {@code BLOCK}/{@code FALLBACK_ROLE}），此处仅作防御性兜底，行为与 v1 一致。
     */
    private void autoSkip(DelegateExecution execution, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance) {
        execution.setVariableLocal(collectionVariableName(rule.getNodeId()), List.of());
        execution.setVariable("approved", true);
        recordAction(instance, rule, ApprovalAction.APPROVE, "无审批人自动通过");
    }

    /**
     * 空审批人策略 {@code REJECT}：DSL v2 当前不会产生该取值，此处仅作防御性兜底，行为与 v1
     * 一致——终止流程并记录失败原因。
     */
    private void autoReject(DelegateExecution execution, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance) {
        execution.setVariableLocal(collectionVariableName(rule.getNodeId()), List.of());
        recordAction(instance, rule, ApprovalAction.TERMINATE, "无审批人自动终止");
        if (instance != null) {
            instance.setStatus(ProcessInstanceStatus.TERMINATED);
            instance.setFinishedTime(LocalDateTime.now());
            instance.setUpdateTime(LocalDateTime.now());
            WorkflowSpringContext.getBean(ProcessInstanceMapper.class).updateById(instance);
        }
        RuntimeService runtimeService = WorkflowSpringContext.getBean(RuntimeService.class);
        runtimeService.deleteProcessInstance(execution.getProcessInstanceId(), "无审批人自动终止");
    }

    /**
     * 记录审批轨迹。
     */
    private void recordAction(ProcessInstanceEntity instance, NodeAssigneeRuleEntity rule, String action, String remark) {
        if (instance == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ApprovalRecordMapper recordMapper = WorkflowSpringContext.getBean(ApprovalRecordMapper.class);
        recordMapper.insert(ApprovalRecordEntity.builder()
                .processInstanceId(instance.getId())
                .nodeId(rule.getNodeId())
                .nodeName(rule.getNodeName())
                .action(action)
                .remark(remark)
                .createBy("system")
                .createTime(now)
                .updateBy("system")
                .updateTime(now)
                .build());
    }

    /**
     * 按节点 id 生成候选人集合流程变量名，须与 v1
     * {@link WorkflowMultiInstanceExecutionListener#collectionVariableName} 及
     * {@code WorkflowModelCompilerV2.collectionVariableName} 完全一致。
     *
     * @param nodeId 节点 id
     * @return 变量名
     */
    static String collectionVariableName(String nodeId) {
        return "approvers_" + nodeId;
    }
}
