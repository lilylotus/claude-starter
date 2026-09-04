package cn.nihility.rbac.workflow.dslv2.engine;

import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceTaskListener;
import cn.nihility.rbac.workflow.engine.flowable.support.WorkflowSpringContext;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * DSL v2 会签（Multi-Instance）节点单个实例任务监听器，只挂
 * {@code flowable:taskListener event="create"}（v1
 * {@link WorkflowMultiInstanceTaskListener} 的姊妹实现，新建而非复用/修改，v1 存量流程行为
 * 不受影响，production-approval-lifecycle change tasks.md 6.3）。只负责持久化本实例任务行，
 * 并把
 * {@link WorkflowV2MultiInstanceExecutionListener} 在轮次开启时写入 miBody 执行作用域的
 * {@code voteNodeRunId} 局部变量（非本地读取，沿执行作用域链向上查找即可命中）落到
 * {@code tab_wf_approval_task.node_run_id}，供后续计票关联。
 * <p>
 * 不挂 {@code complete} 事件：计票、完成/终止判定统一在
 * {@code FlowableWorkflowService.completeTask} 调用 {@code taskService.complete} 前后以
 * Java 代码完成（design.md 第7节"变量隔离到节点执行/轮次"），避免监听器与业务代码两处各写
 * 一遍计票逻辑、避免在监听器内部调用 {@code runtimeService.deleteProcessInstance} 造成的
 * 引擎命令重入风险。
 */
public class WorkflowV2MultiInstanceTaskListener implements TaskListener {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(WorkflowV2MultiInstanceTaskListener.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        try {
            onCreate(delegateTask);
        } catch (RuntimeException ex) {
            log.error("WorkflowV2MultiInstanceTaskListener 处理任务 {} 时发生异常", delegateTask.getId(), ex);
        }
    }

    /**
     * {@code create} 事件：持久化本实例的审批任务记录，含所属节点轮次 id。
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
        Object nodeRunIdVar = delegateTask.getVariable("voteNodeRunId");
        Long nodeRunId = nodeRunIdVar instanceof Number number ? number.longValue() : null;

        ApprovalTaskEntity task = ApprovalTaskEntity.builder()
                .flowableTaskId(delegateTask.getId())
                .processInstanceId(instance == null ? null : instance.getId())
                .nodeRunId(nodeRunId)
                .nodeId(delegateTask.getTaskDefinitionKey())
                .nodeName(delegateTask.getName())
                .assigneeId(assigneeId)
                .status(TaskStatus.PENDING)
                .revision(1L)
                .createTime(now)
                .updateTime(now)
                .build();
        WorkflowSpringContext.getBean(ApprovalTaskMapper.class).insert(task);
    }

    /**
     * 按 businessKey 反查流程实例，与 v1 {@link WorkflowMultiInstanceTaskListener} 完全一致的
     * 反查策略。
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
}
