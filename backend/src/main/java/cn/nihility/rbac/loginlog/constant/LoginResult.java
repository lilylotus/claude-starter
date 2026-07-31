package cn.nihility.rbac.loginlog.constant;

/**
 * 登录结果常量：成功/失败，供 {@code LoginLogRecorder} 写入日志及查询接口的
 * {@code loginResult} 筛选参数复用。
 */
public final class LoginResult {

    /** 登录成功。 */
    public static final int SUCCESS = 1;

    /** 登录失败。 */
    public static final int FAILED = 2;

    /**
     * 工具类不允许实例化。
     */
    private LoginResult() {
    }

    /**
     * 将登录结果码值转换为中文文案。
     *
     * @param loginResult 登录结果码值
     * @return 中文文案，取值不合法时返回 {@code null}
     */
    public static String label(Integer loginResult) {
        if (loginResult == null) {
            return null;
        }
        return switch (loginResult) {
            case SUCCESS -> "成功";
            case FAILED -> "失败";
            default -> null;
        };
    }
}
