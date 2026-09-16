package cn.nihility.rbac.workflow.constant;

/**
 * 业务绑定状态常量（add-process-binding-delete change design.md Decision 1）。与
 * {@code AdminStatus}/{@code RoleStatus} 等项目里其它状态常量类同一套编码风格，值相同但
 * 单独成类，避免跨领域概念耦合。
 */
public final class BindingStatus {

    /** 启用。 */
    public static final int ENABLED = 2000;

    /** 停用。 */
    public static final int DISABLED = 3000;

    /** 已删除（逻辑删除）。 */
    public static final int DELETED = -1000;

    /** 工具类不允许实例化。 */
    private BindingStatus() {
    }
}
