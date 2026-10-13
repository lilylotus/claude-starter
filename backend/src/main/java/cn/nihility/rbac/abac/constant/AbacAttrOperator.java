package cn.nihility.rbac.abac.constant;

import java.util.Set;

/**
 * ABAC 策略用户属性条件运算符常量（abac-policy-engine change design.md Decision 3）：
 * 仅支持等于/不等于/属于多值三种，{@code EQ}/{@code NE} 比较值为单个值，{@code IN} 比较值
 * 为一组值。与 {@code appaccess.policy.constant.PolicyAttrOperator}/
 * {@code userrole.constant.UserRoleAttrOperator} 语义一致但独立声明，不跨模块引用。
 */
public final class AbacAttrOperator {

    /** 等于。 */
    public static final String EQ = "EQ";

    /** 不等于。 */
    public static final String NE = "NE";

    /** 属于一组值。 */
    public static final String IN = "IN";

    /** 全部合法取值，供保存时校验请求参数使用。 */
    public static final Set<String> ALL_OPERATORS = Set.of(EQ, NE, IN);

    /**
     * 工具类不允许实例化。
     */
    private AbacAttrOperator() {
    }
}
