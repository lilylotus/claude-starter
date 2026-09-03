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
 */
public class AutoServiceTaskDelegate implements JavaDelegate {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(AutoServiceTaskDelegate.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(DelegateExecution execution) {
        log.error("自动任务节点 {} 被实际执行，但本轮未注册任何 actionCode 实现，流程配置不应通过发布前校验",
                execution.getCurrentActivityId());
    }
}
