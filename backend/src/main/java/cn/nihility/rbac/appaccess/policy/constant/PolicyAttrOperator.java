package cn.nihility.rbac.appaccess.policy.constant;

import java.util.Set;

/**
 * 策略用户属性条件运算符常量（design.md Decision 1）：仅支持等于/不等于/属于多值三种，
 * {@code EQ}/{@code NE} 比较值为单个值，{@code IN} 比较值为一组值。
 */
public final class PolicyAttrOperator {

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
    private PolicyAttrOperator() {
    }
}
