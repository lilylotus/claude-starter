package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.engine.flowable.support.WorkflowSpringContext;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 会签（Multi-Instance）节点单个实例任务监听器，同一个类分别挂
 * {@code flowable:taskListener event="create"} 与 {@code event="complete"}
 * 两个事件：{@code create} 负责为该实例任务落一条 {@code tab_wf_approval_task} 记录
 * （审批人已由 {@code multiInstanceLoopCharacteristics} 的循环变量表达式设置，本监听器只做
 * 持久化，不重新解析）；{@code complete} 负责回写完成状态、记录审批轨迹，并在该实例被驳回
 * （{@code approved=false}）时把"一票否决"标记 {@code miVeto} 置为 {@code true} 到所属
 * 多实例包装执行（miBody）的作用域，驱动 {@link MultiInstanceCompletionEvaluator} 生成的
 * {@code completionCondition} 表达式立即判定该会签节点终止（workflow-approval-engine
 * change design.md Decision 4/Open Question 4）。
 */
public class WorkflowMultiInstanceTaskListener implements TaskListener {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(WorkflowMultiInstanceTaskListener.class);

    /** Flowable 任务完成时读取的"是否通过"局部变量名，与 {@code approve}/{@code reject} 写入的
     *  变量名保持一致。 */
    private static final String APPROVED_VARIABLE = "approved";

    /**
     * {@inheritDoc}
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        try {
            if ("create".equals(delegateTask.getEventName())) {
                onCreate(delegateTask);
            } else if ("complete".equals(delegateTask.getEventName())) {
                onComplete(delegateTask);
            }
        } catch (RuntimeException ex) {
            log.error("WorkflowMultiInstanceTaskListener 处理任务 {}（事件 {}）时发生异常",
                    delegateTask.getId(), delegateTask.getEventName(), ex);
        }
    }

    /**
     * {@code create} 事件：持久化本实例的审批任务记录。
     */
    private void onCreate(DelegateTask delegateTask) {
        ProcessInstanceEntity instance = resolveProcessInstance(delegateTask.getProcessInstanceId());
        LocalDateTime now = LocalDateTime.now();
        Long assigneeId = null;
        if (StringUtils.hasText(delegateTask.getAssignee())) {
            try {
                assigneeId = Long.valueOf(delegateTask.getAssignee());
            } catch (NumberFormatException ex) {
                log.warn("会签任务 {} 的 assignee={} 无法解析为用户 id", delegateTask.getId(), delegateTask.getAssignee());
            }
        }
        ApprovalTaskEntity task = ApprovalTaskEntity.builder()
                .flowableTaskId(delegateTask.getId())
                .processInstanceId(instance == null ? null : instance.getId())
                .nodeId(delegateTask.getTaskDefinitionKey())
                .nodeName(delegateTask.getName())
                .assigneeId(assigneeId)
                .status(TaskStatus.PENDING)
                .createTime(now)
                .updateTime(now)
                .build();
        WorkflowSpringContext.getBean(ApprovalTaskMapper.class).insert(task);
    }

    /**
     * 按 businessKey 反查流程实例：{@link cn.nihility.rbac.workflow.engine.flowable.FlowableWorkflowService#start}
     * 发起流程时把自有主键（{@code tab_wf_process_instance.id}）作为 Flowable businessKey 传入，
     * businessKey 在流程实例创建之初即已确定，不像 {@code flowable_instance_id} 列要等
     * {@code start()} 方法执行完毕才回填，因此流程刚发起、第一个节点即为会签节点的单个任务
     * 实例创建时也能查到正确的流程实例（此前按 {@code flowable_instance_id} 反查会因该列尚为
     * {@code NULL} 而查不到，是已修复的历史缺陷）。businessKey 缺失或无法解析为主键时（如流程
     * 并非经由 {@link cn.nihility.rbac.workflow.engine.WorkflowService} 发起），回退到按
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
     * {@code complete} 事件：回写完成状态、记录轨迹，驳回时设置一票否决标记。
     */
    private void onComplete(DelegateTask delegateTask) {
        ApprovalTaskMapper taskMapper = WorkflowSpringContext.getBean(ApprovalTaskMapper.class);
        ApprovalTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getFlowableTaskId, delegateTask.getId())
                .last("LIMIT 1"));
        Object approvedVariable = delegateTask.getVariable(APPROVED_VARIABLE);
        boolean approved = !(approvedVariable instanceof Boolean bool) || bool;

        if (task != null) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setFinishedTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }

        Long operatorId = null;
        if (StringUtils.hasText(delegateTask.getAssignee())) {
            try {
                operatorId = Long.valueOf(delegateTask.getAssignee());
            } catch (NumberFormatException ignored) {
                // assignee 非数字 id 时不影响轨迹记录，operatorId 保持为空
            }
        }
        recordAction(delegateTask, task, operatorId, approved);

        if (!approved) {
            RuntimeService runtimeService = WorkflowSpringContext.getBean(RuntimeService.class);
            Execution execution = runtimeService.createExecutionQuery()
                    .executionId(delegateTask.getExecutionId())
                    .singleResult();
            String miBodyExecutionId = execution == null ? null : execution.getParentId();
            if (miBodyExecutionId != null) {
                runtimeService.setVariable(miBodyExecutionId, "miVeto", true);
            }
        }
    }

    /**
     * 记录审批轨迹。
     */
    private void recordAction(DelegateTask delegateTask, ApprovalTaskEntity task, Long operatorId, boolean approved) {
        if (task == null || task.getProcessInstanceId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? "system" : operatorId.toString();
        ApprovalRecordMapper recordMapper = WorkflowSpringContext.getBean(ApprovalRecordMapper.class);
        recordMapper.insert(ApprovalRecordEntity.builder()
                .processInstanceId(task.getProcessInstanceId())
                .taskId(task.getId())
                .nodeId(delegateTask.getTaskDefinitionKey())
                .nodeName(delegateTask.getName())
                .operatorId(operatorId)
                .action(approved ? ApprovalAction.APPROVE : ApprovalAction.REJECT)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build());
    }
}
