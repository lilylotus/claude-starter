package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.workflow.constant.ApprovalMode;

/**
 * 会签（Multi-Instance）完成条件判定工具：既提供纯 Java 的边界判定方法（供单元测试覆盖
 * 1 人/全部通过/部分驳回提前终止/比例边界场景），也提供与之语义完全一致的 Flowable
 * {@code completionCondition} UEL 表达式生成方法，供 BPMN（默认两级流程与
 * {@code WorkflowModelCompiler} 未来生成会签节点时）复用同一套判定规则，避免 Java 端与
 * BPMN 表达式两处实现"各写一遍"逐渐漂移（workflow-approval-engine change design.md
 * Decision 4 / Risks）。
 * <p>
 * 会签节点任一候选人驳回时立即终止（"一票否决"，不等待其余候选人处理），本次按
 * design.md Open Question 4 的默认结论实现，与审批模式（{@code AND}/{@code OR}/
 * {@code PERCENT}）无关：无论哪种模式，只要出现一次驳回，节点即刻终止并驱动流程直接进入
 * 拒绝结束事件。
 * <p>
 * 生成的 UEL 表达式约定：{@code miVeto} 是在会签节点（miBody）执行作用域下维护的布尔型
 * 流程变量，默认 {@code false}，任一实例驳回时由
 * {@link WorkflowMultiInstanceTaskListener} 置为 {@code true}；{@code nrOfInstances}/
 * {@code nrOfCompletedInstances} 是 Flowable Multi-Instance 内置变量。
 */
public final class MultiInstanceCompletionEvaluator {

    /**
     * 工具类不允许实例化。
     */
    private MultiInstanceCompletionEvaluator() {
    }

    /**
     * 纯 Java 判定：给定当前进度，本会签节点是否应当完成。
     *
     * @param mode              审批模式，仅 {@code AND}/{@code OR}/{@code PERCENT} 有意义
     * @param approvalPercent   通过比例阈值（0~100 的整数），仅 {@code mode=PERCENT} 使用
     * @param totalInstances    候选人总数（{@code nrOfInstances}）
     * @param completedInstances 已完成实例数（{@code nrOfCompletedInstances}）
     * @param anyRejected       是否已有任一候选人驳回
     * @return 是否应当完成该会签节点
     */
    public static boolean isComplete(
            ApprovalMode mode,
            Integer approvalPercent,
            int totalInstances,
            int completedInstances,
            boolean anyRejected) {
        if (anyRejected) {
            return true;
        }
        if (totalInstances <= 0) {
            return true;
        }
        return switch (mode) {
            case AND, SINGLE -> completedInstances >= totalInstances;
            case OR -> completedInstances >= 1;
            case PERCENT -> {
                int percent = approvalPercent == null ? 100 : approvalPercent;
                yield completedInstances * 100.0 / totalInstances >= percent;
            }
        };
    }

    /**
     * 生成与 {@link #isComplete} 语义一致的 Flowable {@code completionCondition} UEL 表达式。
     *
     * @param mode            审批模式
     * @param approvalPercent 通过比例阈值（0~100 的整数），仅 {@code mode=PERCENT} 使用
     * @return UEL 表达式字符串
     */
    public static String buildCompletionCondition(ApprovalMode mode, Integer approvalPercent) {
        String progressCondition = switch (mode) {
            case AND, SINGLE -> "${nrOfCompletedInstances >= nrOfInstances}";
            case OR -> "${nrOfCompletedInstances >= 1}";
            case PERCENT -> {
                int percent = approvalPercent == null ? 100 : approvalPercent;
                double ratio = percent / 100.0;
                yield "${(nrOfCompletedInstances / nrOfInstances) >= " + ratio + "}";
            }
        };
        String progressExpr = progressCondition.substring(2, progressCondition.length() - 1);
        return "${miVeto == true || (" + progressExpr + ")}";
    }
}
