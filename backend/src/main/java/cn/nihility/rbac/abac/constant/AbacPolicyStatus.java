package cn.nihility.rbac.abac.constant;

/**
 * ABAC 策略规则状态常量。策略只支持启用/停用两种状态，没有逻辑删除语义（删除接口做
 * 物理删除，见 {@code AbacPolicyServiceImpl#delete}），与
 * {@code appaccess.policy.constant.PolicyStatus} 取值相同但单独成类，避免跨领域概念
 * 耦合（abac-policy-engine change design.md Decision 1：本模块只有限制语义，不存在
 * {@code ALLOW} 效果，状态字段只承担"是否参与判定"的开关语义）。
 */
public final class AbacPolicyStatus {

    /** 启用：该策略参与运行时 ABAC 判定，作为候选策略之一。 */
    public static final int ENABLED = 2000;

    /** 停用：该策略 SHALL NOT 作为运行时 ABAC 判定的候选策略，无需清空配置即可即时生效。 */
    public static final int DISABLED = 3000;

    /**
     * 工具类不允许实例化。
     */
    private AbacPolicyStatus() {
    }
}
