package cn.nihility.rbac.workflow.dslv2.engine;

import cn.nihility.rbac.workflow.constant.ApprovalMode;

/**
 * DSL v2 会签节点通过阈值 K 的整数计算工具（production-approval-lifecycle change design.md
 * 第7节："阈值 K：ALL=N，ANY=1，PERCENT=ceil(N×百分比/100)，整数计算"）。全程只使用整数运算
 * （向上取整改写为 {@code (N * percent + 99) / 100} 的整数除法），不使用浮点比较，避免
 * {@code 2/3} 这类比例在二进制浮点下产生的舍入误差导致边界判定错误。
 * <p>
 * v1 {@link cn.nihility.rbac.workflow.engine.flowable.MultiInstanceCompletionEvaluator}
 * 的 {@code isComplete}/{@code buildCompletionCondition} 用双精度浮点比较，是 v1 遗留实现，
 * 本类不复用也不修改它，只服务于 v2 专用的计票路径（tasks.md 6.3）。
 */
public final class VoteThresholdCalculator {

    /** 工具类不允许实例化。 */
    private VoteThresholdCalculator() {
    }

    /**
     * 按审批模式与候选人总数计算通过阈值 K。
     *
     * @param mode       审批模式，{@code ALL}/{@code ANY}/{@code PERCENT} 对应 v1 共享的
     *                   {@link ApprovalMode#AND}/{@link ApprovalMode#OR}/
     *                   {@link ApprovalMode#PERCENT}（{@link ApprovalMode#SINGLE} 不会出现在
     *                   会签节点，按 {@code ALL} 语义兜底处理）
     * @param percent    通过比例（1~100 的整数），仅 {@code mode=PERCENT} 使用
     * @param totalCount 候选人总数 N
     * @return 通过阈值 K（整数）
     */
    public static int threshold(ApprovalMode mode, Integer percent, int totalCount) {
        if (totalCount <= 0) {
            return 0;
        }
        return switch (mode) {
            case AND, SINGLE -> totalCount;
            case OR -> 1;
            case PERCENT -> {
                int p = percent == null ? 100 : percent;
                yield (totalCount * p + 99) / 100;
            }
        };
    }
}
