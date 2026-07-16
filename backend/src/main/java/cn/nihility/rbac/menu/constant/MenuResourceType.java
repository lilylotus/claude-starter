package cn.nihility.rbac.menu.constant;

/**
 * 资源类型常量：菜单、按钮、API 是固定的结构性类型，不通过通用字典管理模块维护或扩展。
 */
public final class MenuResourceType {

    /** 菜单。 */
    public static final int MENU = 1;

    /** 按钮。 */
    public static final int BUTTON = 2;

    /** API。 */
    public static final int API = 3;

    /**
     * 工具类不允许实例化。
     */
    private MenuResourceType() {
    }

    /**
     * 校验给定的资源类型取值是否合法。
     *
     * @param resourceType 待校验的资源类型
     * @return 合法返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValid(Integer resourceType) {
        return resourceType != null && (resourceType == MENU || resourceType == BUTTON || resourceType == API);
    }
}
