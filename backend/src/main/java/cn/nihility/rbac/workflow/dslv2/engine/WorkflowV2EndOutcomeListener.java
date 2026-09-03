package cn.nihility.rbac.workflow.dslv2.engine;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DSL v2 结束节点终态监听器，挂在 {@code endEvent} 的 {@code flowable:executionListener
 * event="end"} 上，把该结束节点编译期固化的 {@code outcome}（{@code APPROVED}/
 * {@code REJECTED}）写入既有的 {@code approved} 流程变量。v1 与 v2 共用的
 * {@code FlowableWorkflowService.finalizeInstanceIfEnded} 正是通过历史 {@code approved}
 * 变量推断终态——v1 由 {@code WorkflowMultiInstanceTaskListener} 在驳回分支写入
 * {@code approved=false}，本监听器把这一约定扩展到 v2 显式声明结果的结束事件，不需要改动
 * 共用的终态判定逻辑，也不改变 v1 已有行为（production-approval-lifecycle change
 * design.md Decision 3"普通 END 等待所有正常 token 完成；全流程 REJECT 使用根流程范围的
 * 终止结束语义"）。
 * <p>
 * 这只解决"多个结束事件、需要区分具体读到了哪个 outcome"这一具体问题；design.md Decision 6
 * 提出的"最终同步节点与定时器/异步节点都接入同一终态协调器"是更完整的重构，本轮不实现，
 * 仍然只在 {@code approve()}/{@code reject()} 等写操作返回、检测到流程实例已无剩余执行时
 * 才回写 {@code tab_wf_process_instance} 终态。
 */
public class WorkflowV2EndOutcomeListener implements ExecutionListener {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(WorkflowV2EndOutcomeListener.class);

    /** 结束结果字段，BPMN {@code flowable:field name="outcome"} 注入。 */
    private org.flowable.common.engine.api.delegate.Expression outcome;

    /**
     * {@inheritDoc}
     */
    @Override
    public void notify(DelegateExecution execution) {
        try {
            Object value = outcome == null ? null : outcome.getValue(execution);
            String outcomeText = value == null ? null : value.toString();
            execution.setVariable("approved", !"REJECTED".equals(outcomeText));
        } catch (RuntimeException ex) {
            log.error("WorkflowV2EndOutcomeListener 处理结束节点 {} 时发生异常", execution.getCurrentActivityId(), ex);
        }
    }
}
