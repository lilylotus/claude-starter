package cn.nihility.rbac.dict.constant;

/**
 * 字典状态常量。状态值同时承担"启用/停用"与"逻辑删除"两种语义，与
 * {@code cn.nihility.rbac.org.constant.OrgStatus} 取值一致，但按模块独立维护，
 * 避免跨模块耦合。
 */
public final class DictStatus {

    /** 启用。 */
    public static final int ENABLED = 2000;

    /** 停用。 */
    public static final int DISABLED = 3000;

    /** 已删除（逻辑删除）。 */
    public static final int DELETED = -1000;

    /**
     * 工具类不允许实例化。
     */
    private DictStatus() {
    }
}
