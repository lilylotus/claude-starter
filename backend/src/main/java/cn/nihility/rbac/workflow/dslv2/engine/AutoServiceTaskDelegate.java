package cn.nihility.rbac.workflow.dslv2.engine;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "自动任务"节点服务任务委托占位实现：本轮 {@code AutoActionRegistry} 未注册任何
 * {@code actionCode}，{@code ProcessModelDslV2Validator} 已在发布前拒绝任何引用未注册
 * 动作的模型，正常情况下本类不会被真实调用到；保留空实现仅为让编译产物在结构上完整、
 * 避免遗留悬空的 {@code flowable:class} 引用（production-approval-lifecycle change
 * design.md Decision 3/10，白名单动作的真正执行逻辑属于第 7 节可靠执行范畴，本轮范围外）。
 * <p>
 * "正常不会被调用到"不等于"被调用到时可以安全地什么都不做"：若发布前校验存在遗漏或
 * BPMN 被绕过校验直接部署，本委托一旦真的被执行，唯一安全的行为是让流程停在这里、
 * 事务回滚，而不是仅打一条错误日志后当作该节点已成功完成继续往下走——后者会让"自动任务
 * 其实什么也没做"这一事实被流程正常推进的表象掩盖，属于静默失败（production-approval-lifecycle
 * change tasks.md 6.1）。
 */
public class AutoServiceTaskDelegate implements JavaDelegate {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(AutoServiceTaskDelegate.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        log.error("自动任务节点 {} 被实际执行，但本轮未注册任何 actionCode 实现，流程配置不应通过发布前校验，"
                + "中止流程推进并回滚，避免静默当作已成功完成", activityId);
        throw new IllegalStateException(
                "自动任务节点 " + activityId + " 未注册任何 actionCode 实现，不能继续执行");
    }
}
