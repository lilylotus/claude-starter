package cn.nihility.rbac.loginlog.constant;

/**
 * 登录失败原因中文文案常量，供 {@code LoginLogRecorder#recordFailure} 与
 * {@code AuthServiceImpl#login} 复用。作为内部审计信息保留比对外提示更细的粒度，
 * 不参与前端筛选下拉，直接存字符串文案，不再额外做一层码值到文案的映射。
 */
public final class LoginFailReason {

    /** 账号不存在。 */
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";

    /** 账号已删除。 */
    public static final String ACCOUNT_DELETED = "账号已删除";

    /** 账号已停用。 */
    public static final String ACCOUNT_DISABLED = "账号已停用";

    /** 密码不正确。 */
    public static final String PASSWORD_MISMATCH = "密码不正确";

    /** 账号解密失败。 */
    public static final String DECRYPT_FAILED = "账号解密失败";

    /** 短信验证码不正确或已过期（add-sso-login-methods change design.md Decision 4）。 */
    public static final String SMS_CODE_MISMATCH = "短信验证码不正确或已过期";

    /**
     * 短信验证码正确，但按提交手机号此刻查询不到恰好一个启用状态账号（0 个或多个），属于
     * 验证码发出后账号状态发生变化的边界情况（add-sso-login-methods change design.md
     * Decision 4）。
     */
    public static final String MOBILE_NOT_MATCHED = "手机号未匹配到唯一可登录账号";

    /**
     * 工具类不允许实例化。
     */
    private LoginFailReason() {
    }
}
