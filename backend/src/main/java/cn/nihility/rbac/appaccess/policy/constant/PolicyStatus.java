package cn.nihility.rbac.appaccess.policy.constant;

/**
 * 应用访问授权策略规则状态常量。策略只支持启用/停用两种状态，没有逻辑删除语义
 * （删除接口做物理删除，见 {@code PolicyServiceImpl#delete}），与
 * {@code AppStatus}/{@code OrgStatus} 等取值相同但单独成类，避免跨领域概念耦合。
 */
public final class PolicyStatus {

    /** 启用：该策略产生的策略授权记录计入最终生效权限。 */
    public static final int ENABLED = 2000;

    /** 停用：该策略产生的策略授权记录 SHALL NOT 计入最终生效权限，无需清空记录即可即时生效。 */
    public static final int DISABLED = 3000;

    /**
     * 工具类不允许实例化。
     */
    private PolicyStatus() {
    }
}
