package cn.nihility.rbac.auth.constant;

/**
 * 认证相关的专用业务错误码。前端会依据这两个具体数值做不同的跳转逻辑（未登录跳登录页、
 * 首登跳强制改密页），新增/调整业务错误时不要改动这两个既有取值，也不要在别处使用魔法数字。
 */
public final class AuthErrorCode {

    /**
     * 未登录/身份校验失败：缺失 {@code identity-token} 请求头、access-key 无效或已过期、
     * {@code menu} 请求头缺失或格式不合法、refresh-key 无效或已过期均使用该错误码。
     */
    public static final int UNAUTHORIZED = 401;

    /** 首次登录强制改密拦截：该用户当前处于待首次登录强制改密状态，且访问路径不在改密白名单内。 */
    public static final int FIRST_LOGIN_REQUIRED = 4010;

    /**
     * 工具类不允许实例化。
     */
    private AuthErrorCode() {
    }
}
