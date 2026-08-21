package cn.nihility.rbac.ssoprotocollog.constant;

/**
 * SSO 协议调用记录事件类型常量（add-sso-protocol-access-log change design.md Decision 1），
 * 对应 CAS/OAuth2.0 协议全部运行时端点（含全局单点登出接口，登出场景统一计为
 * {@link #LOGOUT} 一种事件类型，不区分 CAS 登出/全局登出两个路由）。
 */
public final class SsoProtocolLogEventType {

    /** CAS 单点登录（服务票据签发），对应 {@code CasController#login}。 */
    public static final String LOGIN = "LOGIN";

    /** CAS 票据验证，对应 {@code CasController#serviceValidate}。 */
    public static final String SERVICE_VALIDATE = "SERVICE_VALIDATE";

    /** 单点登出，对应 {@code CasController#logout} 与 {@code SsoLogoutController#logout}。 */
    public static final String LOGOUT = "LOGOUT";

    /** OAuth2 授权（授权码签发），对应 {@code OAuthController#authorize}。 */
    public static final String AUTHORIZE = "AUTHORIZE";

    /** OAuth2 令牌签发/刷新，对应 {@code OAuthController#token}。 */
    public static final String TOKEN = "TOKEN";

    /** OAuth2 用户信息查询，对应 {@code OAuthController#userinfo}。 */
    public static final String USERINFO = "USERINFO";

    /**
     * 工具类不允许实例化。
     */
    private SsoProtocolLogEventType() {
    }
}
