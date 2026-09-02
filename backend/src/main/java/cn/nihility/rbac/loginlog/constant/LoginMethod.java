package cn.nihility.rbac.loginlog.constant;

import java.util.Set;

/**
 * 登录方式常量：口令/短信验证码/扫码，供 {@code LoginLogRecorder} 写入日志、登录日志查询
 * 接口的 {@code loginMethod} 筛选参数，以及 {@code app/authconfig} 模块校验应用允许的登录
 * 认证方式取值范围复用（add-sso-login-methods change design.md Decision 1/7）。
 */
public final class LoginMethod {

    /** 口令登录，任意应用恒定允许，服务端不允许移除。 */
    public static final String PASSWORD = "PASSWORD";

    /** 短信验证码登录。 */
    public static final String SMS = "SMS";

    /** 扫码登录。 */
    public static final String QRCODE = "QRCODE";

    /** 全部合法取值集合，供入参校验使用。 */
    public static final Set<String> ALL_VALUES = Set.of(PASSWORD, SMS, QRCODE);

    /**
     * 工具类不允许实例化。
     */
    private LoginMethod() {
    }

    /**
     * 将登录方式取值转换为中文文案。
     *
     * @param loginMethod 登录方式取值
     * @return 中文文案，取值不合法时返回 {@code null}
     */
    public static String label(String loginMethod) {
        if (loginMethod == null) {
            return null;
        }
        return switch (loginMethod) {
            case PASSWORD -> "口令";
            case SMS -> "短信验证码";
            case QRCODE -> "扫码";
            default -> null;
        };
    }
}
