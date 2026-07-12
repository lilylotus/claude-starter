package cn.nihility.rbac.user.constant;

/**
 * 用户状态常量。状态值同时承担"启用/停用"与"逻辑删除"两种语义。
 */
public final class UserStatus {

    /** 启用。 */
    public static final int ENABLED = 2000;

    /** 停用。 */
    public static final int DISABLED = 3000;

    /** 已删除（逻辑删除）。 */
    public static final int DELETED = -1000;

    /**
     * 工具类不允许实例化。
     */
    private UserStatus() {
    }
}
