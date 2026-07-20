package cn.nihility.rbac.operationlog.constant;

import java.util.Map;

/**
 * 操作日志资源类型常量：覆盖组织、用户、任职、应用、角色、权限点、管理员、菜单、
 * 字典类型、字典项共 10 类资源，用字符串编码（而非数字）表达，避免与各模块自身的
 * 数字状态常量混淆。每个编码同时维护"所属模块中文名"与"资源中文名"两个映射——
 * 字典类型与字典项同属"字典管理"模块，模块粒度是 9 个。
 */
public final class OperationLogResourceType {

    /** 组织。 */
    public static final String ORG = "org";

    /** 用户。 */
    public static final String USER = "user";

    /** 任职。 */
    public static final String POSITION = "position";

    /** 应用。 */
    public static final String APP = "app";

    /** 角色。 */
    public static final String ROLE = "role";

    /** 权限点。 */
    public static final String PERMISSION = "permission";

    /** 管理员。 */
    public static final String ADMIN = "admin";

    /** 菜单（资源）。 */
    public static final String MENU = "menu";

    /** 字典类型。 */
    public static final String DICT_TYPE = "dictType";

    /** 字典项。 */
    public static final String DICT_ITEM = "dictItem";

    /** 资源类型编码 -> 所属模块中文名。 */
    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry(ORG, "组织管理"),
            Map.entry(USER, "用户管理"),
            Map.entry(POSITION, "任职管理"),
            Map.entry(APP, "应用管理"),
            Map.entry(ROLE, "角色管理"),
            Map.entry(PERMISSION, "权限管理"),
            Map.entry(ADMIN, "管理员管理"),
            Map.entry(MENU, "菜单管理"),
            Map.entry(DICT_TYPE, "字典管理"),
            Map.entry(DICT_ITEM, "字典管理"));

    /** 资源类型编码 -> 资源中文名。 */
    private static final Map<String, String> RESOURCE_NAMES = Map.ofEntries(
            Map.entry(ORG, "组织"),
            Map.entry(USER, "用户"),
            Map.entry(POSITION, "任职"),
            Map.entry(APP, "应用"),
            Map.entry(ROLE, "角色"),
            Map.entry(PERMISSION, "权限点"),
            Map.entry(ADMIN, "管理员"),
            Map.entry(MENU, "菜单"),
            Map.entry(DICT_TYPE, "字典类型"),
            Map.entry(DICT_ITEM, "字典项"));

    /**
     * 工具类不允许实例化。
     */
    private OperationLogResourceType() {
    }

    /**
     * 按资源类型编码查询所属模块中文名。
     *
     * @param resourceType 资源类型编码
     * @return 所属模块中文名，编码不合法时返回 {@code null}
     */
    public static String module(String resourceType) {
        return MODULE_NAMES.get(resourceType);
    }

    /**
     * 按资源类型编码查询资源中文名。
     *
     * @param resourceType 资源类型编码
     * @return 资源中文名，编码不合法时返回 {@code null}
     */
    public static String resourceName(String resourceType) {
        return RESOURCE_NAMES.get(resourceType);
    }
}
