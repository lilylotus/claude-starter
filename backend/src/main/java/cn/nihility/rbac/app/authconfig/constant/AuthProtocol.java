package cn.nihility.rbac.app.authconfig.constant;

import java.util.Set;

/**
 * 应用单点登录协议类型常量，落库为可读字符串值（而非整型码值），供
 * {@code tab_app_auth_config.auth_protocol} 列与请求校验复用。
 */
public final class AuthProtocol {

    /** 无：不接入单点登录。 */
    public static final String NONE = "NONE";

    /** CAS 协议。 */
    public static final String CAS = "CAS";

    /** OAuth2.0 协议。 */
    public static final String OAUTH2 = "OAUTH2";

    /** 全部协议类型取值，供请求参数合法性校验。 */
    public static final Set<String> ALL = Set.of(NONE, CAS, OAUTH2);

    /**
     * 工具类不允许实例化。
     */
    private AuthProtocol() {
    }
}
