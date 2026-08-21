package cn.nihility.rbac.ssoprotocollog.constant;

/**
 * SSO 协议调用结果常量：成功/失败，数值约定与
 * {@code cn.nihility.rbac.loginlog.constant.LoginResult} 保持一致（add-sso-protocol-access-log
 * change design.md Decision 1），供 {@code SsoProtocolLogRecorder} 写入日志及查询接口的
 * {@code result} 筛选参数复用。
 */
public final class SsoProtocolLogResult {

    /** 调用成功。 */
    public static final int SUCCESS = 1;

    /** 调用失败。 */
    public static final int FAILED = 2;

    /**
     * 工具类不允许实例化。
     */
    private SsoProtocolLogResult() {
    }

    /**
     * 将调用结果码值转换为中文文案。
     *
     * @param result 调用结果码值
     * @return 中文文案，取值不合法时返回 {@code null}
     */
    public static String label(Integer result) {
        if (result == null) {
            return null;
        }
        return switch (result) {
            case SUCCESS -> "成功";
            case FAILED -> "失败";
            default -> null;
        };
    }
}
