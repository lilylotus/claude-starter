package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.support.NodeAssigneeResolutionService;
import cn.nihility.rbac.workflow.assignee.support.ResolvedAssignees;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.engine.flowable.support.WorkflowSpringContext;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
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
 * 会签（Multi-Instance）节点候选人集合准备监听器，挂在多实例 {@code userTask} 的
 * {@code flowable:executionListener event="start"} 上，在该活动的多实例包装执行
 * （miBody）创建、个体实例尚未展开前触发。解析候选人后写入按节点命名的集合流程变量
 * （{@code approvers_<nodeId>}）供 {@code multiInstanceLoopCharacteristics} 的
 * {@code flowable:collection} 引用，并初始化"一票否决"标记变量 {@code miVeto=false}
 * （workflow-approval-engine change design.md Decision 4）。
 */
public class WorkflowMultiInstanceExecutionListener implements ExecutionListener {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(WorkflowMultiInstanceExecutionListener.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void notify(DelegateExecution execution) {
        try {
            doNotify(execution);
        } catch (RuntimeException ex) {
            log.error("WorkflowMultiInstanceExecutionListener 处理节点 {} 时发生异常，候选人集合置空",
                    execution.getCurrentActivityId(), ex);
            execution.setVariableLocal(collectionVariableName(execution.getCurrentActivityId()), List.of());
        }
    }

    /**
     * 实际处理逻辑，异常由 {@link #notify(DelegateExecution)} 统一兜底捕获。
     */
    private void doNotify(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        execution.setVariableLocal("miVeto", false);

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
                instance == null ? null : instance.getApplicantOrgId());
        NodeAssigneeResolutionService resolutionService = WorkflowSpringContext.getBean(NodeAssigneeResolutionService.class);
        ResolvedAssignees resolved = resolutionService.resolve(rule, context);

        switch (resolved.kind()) {
            case DIRECT, WORKFLOW_ADMIN -> applyCollection(execution, nodeId, resolved);
            case AUTO_SKIP -> autoSkip(execution, nodeId, rule, instance);
            case REJECT -> autoReject(execution, rule, instance);
            case BLOCKED -> blockPendingAssignment(execution, nodeId, instance);
        }
    }

    /** 空审批人策略解析为空时使用的哨兵用户 id 文本，真实用户 id 从 1 开始自增，此值永不
     *  对应真实账号，纯粹用于避免 Flowable 对空集合多实例节点"立即自动完成"这一已知行为
     *  （design.md Decision 3/5"避免空集合 MI 直接跳过"）。{@link cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2ReassignmentService}
     *  据此识别待重分配的哨兵分支，故声明为 {@code public}。 */
    public static final String EMPTY_SENTINEL_USER_ID = "0";

    /**
     * 空审批人策略 {@code BLOCK}/{@code FALLBACK_ROLE}（兜底仍为空）：会签节点候选人集合置为
     * 单元素哨兵集合而非真正的空集合——Flowable 对 {@code nrOfInstances=0} 的多实例节点会在
     * 活动开始阶段就直接跳过、根本不给 completionCondition 判定的机会，导致"零候选人却被当成
     * 已完成"；哨兵产生的唯一任务无法被任何真实用户认领，流程实例标记
     * {@code exception_code=ASSIGNEE_EMPTY}。运维重分配时通过
     * {@code runtimeService.addMultiInstanceExecution} 为每个真实候选人新增实例、再删除哨兵
     * 实例，使会签的 N/K 计算按补充后的真实候选人数量进行，不遗留哨兵占位票
     * （DSL v2 专用，production-approval-lifecycle change design.md Decision 5/7）。
     */
    private void blockPendingAssignment(DelegateExecution execution, String nodeId, ProcessInstanceEntity instance) {
        execution.setVariableLocal(collectionVariableName(nodeId), List.of(EMPTY_SENTINEL_USER_ID));
        if (instance != null) {
            instance.setExceptionCode("ASSIGNEE_EMPTY");
            instance.setUpdateTime(LocalDateTime.now());
            WorkflowSpringContext.getBean(ProcessInstanceMapper.class).updateById(instance);
        }
        log.warn("会签节点 {} 空审批人策略 BLOCK 兜底后仍未解析出候选人，使用哨兵候选人占位，等待运维重分配", nodeId);
    }

    /**
     * 按 businessKey 反查流程实例：{@code DelegateExecution#getProcessInstanceBusinessKey()}
     * 在流程实例创建之初即已确定（{@link cn.nihility.rbac.workflow.engine.flowable.FlowableWorkflowService#start}
     * 把自有主键作为 businessKey 传入），不像 {@code flowable_instance_id} 列要等 {@code start()}
     * 方法执行完毕才回填，因此会签节点在流程刚发起、第一个节点即为会签节点时也能查到正确的
     * 流程实例（此前按 {@code flowable_instance_id} 反查会因该列尚为 {@code NULL} 而查不到，
     * 是已修复的历史缺陷）。businessKey 缺失或无法解析为主键时（如流程并非经由
     * {@link cn.nihility.rbac.workflow.engine.WorkflowService} 发起），回退到按
     * {@code flowable_instance_id} 反查，兼容历史调用方。
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
     * 写入候选人集合流程变量。
     */
    private void applyCollection(DelegateExecution execution, String nodeId, ResolvedAssignees resolved) {
        if (!resolved.hasAssignees()) {
            log.error("会签节点 {} 空审批人策略 {} 兜底后仍未解析出候选人，集合置空",
                    nodeId, resolved.kind());
        }
        List<String> userIdTexts = resolved.userIds().stream().map(String::valueOf).toList();
        execution.setVariableLocal(collectionVariableName(nodeId), userIdTexts);
    }

    /**
     * 空审批人策略 {@code AUTO_SKIP}：候选人集合置空，Flowable 对空集合的多实例节点会自动
     * 立即完成（{@code nrOfInstances=0}），额外记录一条说明性审批轨迹并设置
     * {@code approved=true} 变量，供该节点恰好是排他网关前最后一个审批节点时正确分流。
     */
    private void autoSkip(
            DelegateExecution execution,
            String nodeId,
            NodeAssigneeRuleEntity rule,
            ProcessInstanceEntity instance) {
        execution.setVariableLocal(collectionVariableName(nodeId), List.of());
        execution.setVariable("approved", true);
        recordAction(instance, rule, ApprovalAction.APPROVE, "无审批人自动通过");
    }

    /**
     * 空审批人策略 {@code REJECT}：终止流程并记录失败原因。
     */
    private void autoReject(DelegateExecution execution, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance) {
        execution.setVariableLocal(collectionVariableName(execution.getCurrentActivityId()), List.of());
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
     * 按节点 id 生成候选人集合流程变量名，避免多个会签节点相互覆盖。
     *
     * @param nodeId 节点 id
     * @return 变量名
     */
    static String collectionVariableName(String nodeId) {
        return "approvers_" + nodeId;
    }
}
