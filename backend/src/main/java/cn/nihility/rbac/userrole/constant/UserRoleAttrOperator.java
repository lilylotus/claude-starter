package cn.nihility.rbac.userrole.constant;

import java.util.Set;

/**
 * 用户角色批量添加能力的用户属性条件运算符常量，语义与
 * {@code cn.nihility.rbac.appaccess.policy.constant.PolicyAttrOperator} 完全一致（仅支持
 * 等于/不等于/属于多值三种），但独立成类不复用后者，避免 {@code user-role} 能力依赖
 * {@code app-access-authorization} 能力的内部常量（add-user-role-batch-assignment change
 * design.md Decision 2）。
 */
public final class UserRoleAttrOperator {

    /** 等于。 */
    public static final String EQ = "EQ";

    /** 不等于。 */
    public static final String NE = "NE";

    /** 属于一组值。 */
    public static final String IN = "IN";

    /** 全部合法取值，供校验请求参数使用。 */
    public static final Set<String> ALL_OPERATORS = Set.of(EQ, NE, IN);

    /**
     * 工具类不允许实例化。
     */
    private UserRoleAttrOperator() {
    }
}
