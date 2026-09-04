package cn.nihility.rbac.workflow.dslv2.engine;

import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.AssigneeResolverRegistry;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.engine.flowable.support.WorkflowSpringContext;
import cn.nihility.rbac.workflow.entity.CcRecordEntity;
import cn.nihility.rbac.workflow.entity.NodeRunEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.CcRecordMapper;
import cn.nihility.rbac.workflow.mapper.NodeRunMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Set;
import org.flowable.common.engine.impl.el.FixedValue;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * "抄送"节点服务任务委托：由 {@code WorkflowModelCompilerV2} 编译为 {@code ServiceTask}
 * 挂载，{@code recipientType}/{@code recipientValue} 通过 Flowable 字段注入（BPMN
 * {@code flowable:field}）传入，非 {@code userTask}，同步执行完立即流转，不阻塞流程
 * （production-approval-lifecycle change design.md Decision 3/10）。解析异常按无操作处理，
 * 抄送失败不影响审批主流程。
 */
public class CcServiceTaskDelegate implements JavaDelegate {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(CcServiceTaskDelegate.class);

    /** 抄送接收人来源类型字段，BPMN {@code flowable:field name="recipientType"} 注入。 */
    private Expression recipientType;

    /** 抄送接收人来源取值字段，BPMN {@code flowable:field name="recipientValue"} 注入。 */
    private Expression recipientValue;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch (RuntimeException ex) {
            log.error("CcServiceTaskDelegate 处理节点 {} 时发生异常，本次不生成抄送记录",
                    execution.getCurrentActivityId(), ex);
        }
    }

    private void doExecute(DelegateExecution execution) {
        String typeText = valueOf(recipientType, execution);
        String value = valueOf(recipientValue, execution);
        if (!StringUtils.hasText(typeText)) {
            log.warn("抄送节点 {} 未配置 recipientType，跳过", execution.getCurrentActivityId());
            return;
        }

        ProcessInstanceMapper processInstanceMapper = WorkflowSpringContext.getBean(ProcessInstanceMapper.class);
        ProcessInstanceEntity instance = resolveProcessInstance(execution, processInstanceMapper);
        if (instance == null) {
            log.warn("抄送节点 {} 未能反查到流程实例，跳过", execution.getCurrentActivityId());
            return;
        }

        AssigneeType type;
        try {
            type = AssigneeType.valueOf(typeText);
        } catch (IllegalArgumentException ex) {
            log.warn("抄送节点 {} 的 recipientType={} 不是合法的来源类型，跳过", execution.getCurrentActivityId(), typeText);
            return;
        }

        AssigneeResolveContext context = new AssigneeResolveContext(
                instance.getId(), execution.getCurrentActivityId(), value, instance.getApplicantId(),
                instance.getApplicantOrgId(), null, null);
        Set<Long> recipients = WorkflowSpringContext.getBean(AssigneeResolverRegistry.class).resolve(type, context);
        if (recipients.isEmpty()) {
            log.warn("抄送节点 {} 未解析出任何接收人，跳过", execution.getCurrentActivityId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        NodeRunEntity nodeRun = NodeRunEntity.builder()
                .instanceId(instance.getId())
                .nodeId(execution.getCurrentActivityId())
                .executionId(execution.getId())
                .roundNo(1)
                .totalCount(recipients.size())
                .agreeCount(0)
                .rejectCount(0)
                .runStatus("COMPLETED")
                .revision(1L)
                .createTime(now)
                .updateTime(now)
                .build();
        WorkflowSpringContext.getBean(NodeRunMapper.class).insert(nodeRun);

        CcRecordMapper ccRecordMapper = WorkflowSpringContext.getBean(CcRecordMapper.class);
        for (Long recipientId : recipients) {
            ccRecordMapper.insert(CcRecordEntity.builder()
                    .instanceId(instance.getId())
                    .nodeRunId(nodeRun.getId())
                    .recipientId(recipientId)
                    .createTime(now)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 按 businessKey 反查流程实例，与 {@code WorkflowAssigneeTaskListener} 同一约定。
     */
    private ProcessInstanceEntity resolveProcessInstance(DelegateExecution execution, ProcessInstanceMapper mapper) {
        String businessKey = execution.getProcessInstanceBusinessKey();
        if (StringUtils.hasText(businessKey)) {
            try {
                return mapper.selectById(Long.valueOf(businessKey));
            } catch (NumberFormatException ignored) {
                // 回退按 flowableInstanceId 反查
            }
        }
        return mapper.selectOne(new LambdaQueryWrapper<ProcessInstanceEntity>()
                .eq(ProcessInstanceEntity::getFlowableInstanceId, execution.getProcessInstanceId())
                .last("LIMIT 1"));
    }

    /**
     * 读取字段注入表达式的值，兼容 {@link FixedValue}（静态字符串）与真正的 UEL
     * {@link Expression}。
     */
    private String valueOf(Expression expression, DelegateExecution execution) {
        if (expression == null) {
            return null;
        }
        Object value = expression instanceof FixedValue fixed ? fixed.getExpressionText() : expression.getValue(execution);
        return value == null ? null : value.toString();
    }
}
