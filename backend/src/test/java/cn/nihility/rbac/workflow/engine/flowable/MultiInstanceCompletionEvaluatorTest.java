package cn.nihility.rbac.workflow.engine.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import org.junit.jupiter.api.Test;

/**
 * {@link MultiInstanceCompletionEvaluator} 单元测试，覆盖 AND/OR/PERCENT 三种会签模式的
 * 边界场景（workflow-approval-engine change tasks.md 5.1）。
 */
class MultiInstanceCompletionEvaluatorTest {

    /** AND 模式：3 人中 2 人通过、1 人未处理时尚未完成。 */
    @Test
    void isComplete_and_shouldNotCompleteWhenNotAllFinished() {
        boolean complete = MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.AND, null, 3, 2, false);

        assertThat(complete).isFalse();
    }

    /** AND 模式：全部通过时完成。 */
    @Test
    void isComplete_and_shouldCompleteWhenAllFinished() {
        boolean complete = MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.AND, null, 3, 3, false);

        assertThat(complete).isTrue();
    }

    /** 1 人的会签节点（退化为单人审批）：完成一次即算完成。 */
    @Test
    void isComplete_and_shouldCompleteWithSingleInstance() {
        boolean complete = MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.AND, null, 1, 1, false);

        assertThat(complete).isTrue();
    }

    /** OR 模式：3 人中 1 人通过即完成。 */
    @Test
    void isComplete_or_shouldCompleteWithSingleApproval() {
        boolean complete = MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.OR, null, 3, 1, false);

        assertThat(complete).isTrue();
    }

    /** OR 模式：无人处理时未完成。 */
    @Test
    void isComplete_or_shouldNotCompleteWithoutAnyApproval() {
        boolean complete = MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.OR, null, 3, 0, false);

        assertThat(complete).isFalse();
    }

    /** PERCENT 模式：5 人中 3 人通过（60%）达到 60% 阈值，完成。 */
    @Test
    void isComplete_percent_shouldCompleteAtExactThreshold() {
        boolean complete = MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.PERCENT, 60, 5, 3, false);

        assertThat(complete).isTrue();
    }

    /** PERCENT 模式：5 人中 2 人通过（40%）未达到 60% 阈值，未完成。 */
    @Test
    void isComplete_percent_shouldNotCompleteBelowThreshold() {
        boolean complete = MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.PERCENT, 60, 5, 2, false);

        assertThat(complete).isFalse();
    }

    /** 任一候选人驳回时，无论审批模式为何，均立即终止（一票否决）。 */
    @Test
    void isComplete_shouldTerminateImmediatelyOnAnyRejection() {
        assertThat(MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.AND, null, 5, 1, true)).isTrue();
        assertThat(MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.OR, null, 5, 0, true)).isTrue();
        assertThat(MultiInstanceCompletionEvaluator.isComplete(ApprovalMode.PERCENT, 80, 5, 1, true)).isTrue();
    }

    /** completionCondition 表达式生成：AND 模式包含一票否决与全部完成判定。 */
    @Test
    void buildCompletionCondition_and_shouldContainVetoAndFullCompletionCheck() {
        String condition = MultiInstanceCompletionEvaluator.buildCompletionCondition(ApprovalMode.AND, null);

        assertThat(condition)
                .contains("miVeto")
                .contains("nrOfCompletedInstances >= nrOfInstances");
    }

    /** completionCondition 表达式生成：OR 模式判定至少一个完成。 */
    @Test
    void buildCompletionCondition_or_shouldCheckAtLeastOneCompletion() {
        String condition = MultiInstanceCompletionEvaluator.buildCompletionCondition(ApprovalMode.OR, null);

        assertThat(condition).contains("nrOfCompletedInstances >= 1");
    }

    /** completionCondition 表达式生成：PERCENT 模式按比例换算阈值。 */
    @Test
    void buildCompletionCondition_percent_shouldConvertPercentToRatio() {
        String condition = MultiInstanceCompletionEvaluator.buildCompletionCondition(ApprovalMode.PERCENT, 60);

        assertThat(condition).contains("0.6");
    }
}
