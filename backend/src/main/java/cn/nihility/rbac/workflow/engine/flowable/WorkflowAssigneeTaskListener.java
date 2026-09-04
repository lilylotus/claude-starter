package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.support.NodeAssigneeResolutionService;
import cn.nihility.rbac.workflow.assignee.support.ResolvedAssignees;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.engine.flowable.support.WorkflowSpringContext;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 单人/候选组节点审批人解析监听器，挂在 BPMN {@code userTask} 的
 * {@code flowable:taskListener event="create"} 上。由 Flowable 通过无参构造器反射实例化
 * （见 {@link WorkflowSpringContext} 类注释），先由 {@link DelegateTask#getProcessDefinitionId()}
 * 反查 {@code tab_wf_process_definition.id}，再按 {@code (processDefinitionId,
 * taskDefinitionKey)} 查 {@code tab_wf_node_assignee_rule}，解析审批人并写入 Flowable
 * {@code assignee}/{@code candidateUsers} 与自有的 {@code tab_wf_approval_task}/
 * {@code tab_wf_approval_task_candidate}（workflow-approval-engine change design.md
 * Decision 4）。
 * <p>
 * 找不到匹配的流程定义/规则行时（如流程并非经由 {@link cn.nihility.rbac.workflow.engine.WorkflowService}
 * 启动，缺少本引擎的元数据）按无操作处理，不设置 assignee/candidates，退化为"谁有权限点谁能
 * 处理"的旧行为，兼容尚未接入本引擎的历史调用方；解析过程中的任何异常均被捕获记录日志，
 * SHALL NOT 抛出导致 Flowable 事务回滚（design.md Risks 一节）。
 */
public class WorkflowAssigneeTaskListener implements TaskListener {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(WorkflowAssigneeTaskListener.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        try {
            doNotify(delegateTask);
        } catch (RuntimeException ex) {
            log.error("WorkflowAssigneeTaskListener 处理任务 {} 时发生异常，本次不设置审批人", delegateTask.getId(), ex);
        }
    }

    /**
     * 实际处理逻辑，异常由 {@link #notify(DelegateTask)} 统一兜底捕获。
     */
    private void doNotify(DelegateTask delegateTask) {
        ProcessDefinitionMapper processDefinitionMapper = WorkflowSpringContext.getBean(ProcessDefinitionMapper.class);
        ProcessDefinitionEntity processDefinition = processDefinitionMapper.selectOne(
                new LambdaQueryWrapper<ProcessDefinitionEntity>()
                        .eq(ProcessDefinitionEntity::getFlowableDefinitionId, delegateTask.getProcessDefinitionId())
                        .last("LIMIT 1"));
        if (processDefinition == null) {
            log.warn("未找到 flowableDefinitionId={} 对应的 tab_wf_process_definition 记录，跳过审批人解析",
                    delegateTask.getProcessDefinitionId());
            return;
        }

        NodeAssigneeRuleMapper ruleMapper = WorkflowSpringContext.getBean(NodeAssigneeRuleMapper.class);
        NodeAssigneeRuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<NodeAssigneeRuleEntity>()
                .eq(NodeAssigneeRuleEntity::getProcessDefinitionId, processDefinition.getId())
                .eq(NodeAssigneeRuleEntity::getNodeId, delegateTask.getTaskDefinitionKey())
                .last("LIMIT 1"));
        if (rule == null) {
            log.warn("未找到流程定义 {} 节点 {} 对应的审批人规则，跳过审批人解析",
                    processDefinition.getId(), delegateTask.getTaskDefinitionKey());
            return;
        }

        ProcessInstanceEntity instance = resolveProcessInstance(delegateTask.getProcessInstanceId());
        if (instance != null) {
            instance.setCurrentNodeId(rule.getNodeId());
            instance.setCurrentNodeName(rule.getNodeName());
            instance.setUpdateTime(LocalDateTime.now());
            WorkflowSpringContext.getBean(ProcessInstanceMapper.class).updateById(instance);
        }

        AssigneeResolveContext context = new AssigneeResolveContext(
                instance == null ? null : instance.getId(),
                rule.getNodeId(),
                rule.getAssigneeValue(),
                instance == null ? null : instance.getApplicantId(),
                instance == null ? null : instance.getApplicantOrgId(),
                rule.getAssigneeOrgSource(),
                rule.getTargetOrgId());

        NodeAssigneeResolutionService resolutionService = WorkflowSpringContext.getBean(NodeAssigneeResolutionService.class);
        ResolvedAssignees resolved = resolutionService.resolve(rule, context);

        switch (resolved.kind()) {
            case DIRECT, WORKFLOW_ADMIN -> applyAssignees(delegateTask, rule, resolved, instance);
            case AUTO_SKIP -> autoSkip(delegateTask, rule, instance);
            case REJECT -> autoReject(delegateTask, rule, instance);
            case BLOCKED -> blockPendingAssignment(delegateTask, rule, instance);
        }
    }

    /**
     * 空审批人策略 {@code BLOCK}/{@code FALLBACK_ROLE}（兜底仍为空）：任务照常创建但不设置
     * 处理人/候选人，停在"待分配"状态，不自动通过、不终止流程；流程实例标记
     * {@code exception_code=ASSIGNEE_EMPTY} 供运维异常队列展示，运维重分配后清除该标记
     * （DSL v2 专用，production-approval-lifecycle change design.md Decision 5）。
     */
    private void blockPendingAssignment(DelegateTask delegateTask, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance) {
        persistTask(delegateTask, rule, List.of(), instance, null);
        if (instance != null) {
            instance.setExceptionCode("ASSIGNEE_EMPTY");
            instance.setUpdateTime(LocalDateTime.now());
            WorkflowSpringContext.getBean(ProcessInstanceMapper.class).updateById(instance);
        }
        log.warn("流程定义 {} 节点 {} 空审批人策略 BLOCK 兜底后仍未解析出候选人，任务 {} 停在待分配状态",
                rule.getProcessDefinitionId(), rule.getNodeId(), delegateTask.getId());
    }

    /**
     * 按 businessKey 反查流程实例：{@link cn.nihility.rbac.workflow.engine.flowable.FlowableWorkflowService#start}
     * 发起流程时把自有主键（{@code tab_wf_process_instance.id}）作为 Flowable businessKey 传入，
     * businessKey 在流程实例创建之初即已确定，不像 {@code flowable_instance_id} 列要等
     * {@code start()} 方法执行完毕才回填，因此流程第一个节点创建时同步触发本监听器也能查到
     * 正确的流程实例（此前按 {@code flowable_instance_id} 反查会因该列尚为 {@code NULL} 而查
     * 不到，是已修复的历史缺陷）。businessKey 缺失或无法解析为主键时（如流程并非经由
     * {@link cn.nihility.rbac.workflow.engine.WorkflowService} 发起），回退到按
     * {@code flowable_instance_id} 反查，兼容历史调用方。
     */
    private ProcessInstanceEntity resolveProcessInstance(String flowableProcessInstanceId) {
        ProcessInstanceMapper processInstanceMapper = WorkflowSpringContext.getBean(ProcessInstanceMapper.class);
        RuntimeService runtimeService = WorkflowSpringContext.getBean(RuntimeService.class);
        ProcessInstance flowableInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(flowableProcessInstanceId)
                .singleResult();
        String businessKey = flowableInstance == null ? null : flowableInstance.getBusinessKey();
        if (StringUtils.hasText(businessKey)) {
            try {
                return processInstanceMapper.selectById(Long.valueOf(businessKey));
            } catch (NumberFormatException ex) {
                log.warn("流程实例 {} 的 businessKey={} 无法解析为 tab_wf_process_instance 主键，回退按 flowableInstanceId 反查",
                        flowableProcessInstanceId, businessKey);
            }
        }
        return processInstanceMapper.selectOne(new LambdaQueryWrapper<ProcessInstanceEntity>()
                .eq(ProcessInstanceEntity::getFlowableInstanceId, flowableProcessInstanceId)
                .last("LIMIT 1"));
    }

    /**
     * 将解析结果写入 Flowable 任务与自有业务表。
     */
    private void applyAssignees(
            DelegateTask delegateTask,
            NodeAssigneeRuleEntity rule,
            ResolvedAssignees resolved,
            ProcessInstanceEntity instance) {
        List<Long> userIds = resolved.userIds().stream().toList();
        if (userIds.size() == 1) {
            delegateTask.setAssignee(userIds.get(0).toString());
        } else if (userIds.size() > 1) {
            delegateTask.addCandidateUsers(userIds.stream().map(String::valueOf).toList());
        } else {
            log.error("流程定义 {} 节点 {} 空审批人策略 {} 兜底后仍未解析出候选人，任务暂无处理人",
                    rule.getProcessDefinitionId(), rule.getNodeId(), rule.getEmptyAssigneeStrategy());
        }
        persistTask(delegateTask, rule, userIds, instance, resolved.resolveBasis());
    }

    /**
     * 空审批人策略 {@code AUTO_SKIP}：自动完成该节点并记录说明性审批轨迹。
     */
    private void autoSkip(DelegateTask delegateTask, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance) {
        ApprovalTaskEntity task = persistTask(delegateTask, rule, List.of(), instance, null);
        task.setStatus(TaskStatus.COMPLETED);
        task.setFinishedTime(LocalDateTime.now());
        WorkflowSpringContext.getBean(ApprovalTaskMapper.class).updateById(task);

        recordAction(instance, task.getId(), rule, null, ApprovalAction.APPROVE, "无审批人自动通过");

        TaskService taskService = WorkflowSpringContext.getBean(TaskService.class);
        taskService.complete(delegateTask.getId(), java.util.Map.of("approved", true));
    }

    /**
     * 空审批人策略 {@code REJECT}：终止流程并记录失败原因。
     */
    private void autoReject(DelegateTask delegateTask, NodeAssigneeRuleEntity rule, ProcessInstanceEntity instance) {
        ApprovalTaskEntity task = persistTask(delegateTask, rule, List.of(), instance, null);
        task.setStatus(TaskStatus.COMPLETED);
        task.setFinishedTime(LocalDateTime.now());
        WorkflowSpringContext.getBean(ApprovalTaskMapper.class).updateById(task);

        recordAction(instance, task.getId(), rule, null, ApprovalAction.TERMINATE, "无审批人自动终止");

        if (instance != null) {
            instance.setStatus(ProcessInstanceStatus.TERMINATED);
            instance.setFinishedTime(LocalDateTime.now());
            instance.setUpdateTime(LocalDateTime.now());
            WorkflowSpringContext.getBean(ProcessInstanceMapper.class).updateById(instance);
        }
        RuntimeService runtimeService = WorkflowSpringContext.getBean(RuntimeService.class);
        runtimeService.deleteProcessInstance(delegateTask.getProcessInstanceId(), "无审批人自动终止");
    }

    /**
     * 落库审批任务与候选人明细。
     */
    private ApprovalTaskEntity persistTask(
            DelegateTask delegateTask,
            NodeAssigneeRuleEntity rule,
            List<Long> userIds,
            ProcessInstanceEntity instance,
            String resolveBasis) {
        LocalDateTime now = LocalDateTime.now();
        ApprovalTaskEntity task = ApprovalTaskEntity.builder()
                .flowableTaskId(delegateTask.getId())
                .processInstanceId(instance == null ? null : instance.getId())
                .nodeId(rule.getNodeId())
                .nodeName(rule.getNodeName())
                .assigneeId(userIds.size() == 1 ? userIds.get(0) : null)
                .candidateType(userIds.size() > 1 ? CandidateType.USER : null)
                .status(TaskStatus.PENDING)
                .createTime(now)
                .updateTime(now)
                .build();
        WorkflowSpringContext.getBean(ApprovalTaskMapper.class).insert(task);

        if (userIds.size() > 1) {
            ApprovalTaskCandidateMapper candidateMapper = WorkflowSpringContext.getBean(ApprovalTaskCandidateMapper.class);
            for (Long userId : userIds) {
                candidateMapper.insert(ApprovalTaskCandidateEntity.builder()
                        .taskId(task.getId())
                        .candidateType(CandidateType.USER)
                        .candidateValue(userId.toString())
                        .resolveBasis(resolveBasis)
                        .createTime(now)
                        .updateTime(now)
                        .build());
            }
        }
        return task;
    }

    /**
     * 记录审批轨迹。
     */
    private void recordAction(
            ProcessInstanceEntity instance,
            Long taskId,
            NodeAssigneeRuleEntity rule,
            Long operatorId,
            String action,
            String remark) {
        if (instance == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? "system" : operatorId.toString();
        ApprovalRecordMapper recordMapper = WorkflowSpringContext.getBean(ApprovalRecordMapper.class);
        recordMapper.insert(ApprovalRecordEntity.builder()
                .processInstanceId(instance.getId())
                .taskId(taskId)
                .nodeId(rule.getNodeId())
                .nodeName(rule.getNodeName())
                .operatorId(operatorId)
                .action(action)
                .remark(remark)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build());
    }
}
