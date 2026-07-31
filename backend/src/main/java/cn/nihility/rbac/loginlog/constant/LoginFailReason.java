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

    /**
     * 工具类不允许实例化。
     */
    private LoginFailReason() {
    }
}
