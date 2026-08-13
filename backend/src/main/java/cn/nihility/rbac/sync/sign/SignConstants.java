package cn.nihility.rbac.sync.sign;

import cn.nihility.rbac.app.constant.SignAlgorithm;

/**
 * 签名相关常量：查询参数键名、请求头名称、时间戳有效窗口、签名方法取值
 * （app-sync-notify-pull-api change design.md Decision 10）。
 */
public final class SignConstants {

    /** 应用 AccessKey 请求头名称，签名开关无论开关都要传，用于识别调用方。 */
    public static final String HEADER_APP_KEY = "X-App-Key";

    /** 签名 query 参数：应用 AccessKey（与请求头 {@link #HEADER_APP_KEY} 同值，一并参与签名）。 */
    public static final String QUERY_KEY_APP_KEY = "appKey";

    /** 签名 query 参数：签名方法。 */
    public static final String QUERY_KEY_SIGN_METHOD = "signMethod";

    /** 签名 query 参数：13 位毫秒时间戳。 */
    public static final String QUERY_KEY_TS = "ts";

    /** 签名 query 参数：随机数，用于防重放。 */
    public static final String QUERY_KEY_NONCE = "nonce";

    /** 签名 query 参数：签名结果。 */
    public static final String QUERY_KEY_SIGNATURE = "signature";

    /** 签名方法取值：HMAC-SHA256。 */
    public static final String SIGN_METHOD_HMAC_SHA256 = "HMAC_SHA256";

    /** 签名方法取值：HMAC-SM3。 */
    public static final String SIGN_METHOD_HMAC_SM3 = "HMAC_SM3";

    /** 时间戳有效窗口（毫秒），超出视为过期，默认 5 分钟。 */
    public static final long TIMESTAMP_WINDOW_MILLIS = 5 * 60 * 1000L;

    /**
     * 工具类不允许实例化。
     */
    private SignConstants() {
    }

    /**
     * 按应用当前配置的签名算法解析对应的签名方法取值。
     *
     * @param signAlgorithm 签名算法：{@code SHA256} 或 {@code SM3}
     * @return 签名方法取值
     */
    public static String signMethodOf(String signAlgorithm) {
        return SignAlgorithm.SM3.equals(signAlgorithm) ? SIGN_METHOD_HMAC_SM3 : SIGN_METHOD_HMAC_SHA256;
    }
}
