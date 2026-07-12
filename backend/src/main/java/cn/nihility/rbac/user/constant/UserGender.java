package cn.nihility.rbac.user.constant;

/**
 * 用户性别常量。性别取值稳定、几乎不会变化，用后端常量类表达，不通过字典模块维护。
 */
public final class UserGender {

    /** 未知。 */
    public static final int UNKNOWN = 0;

    /** 男。 */
    public static final int MALE = 1;

    /** 女。 */
    public static final int FEMALE = 2;

    /**
     * 工具类不允许实例化。
     */
    private UserGender() {
    }
}
