package cn.nihility.rbac.sync.sign;

import cn.nihility.rbac.app.constant.SignAlgorithm;

/**
 * 签名相关常量：请求头名称、时间戳有效窗口、签名方法取值
 * （app-sync-notify-pull-api change design.md Decision 10；move-sync-sign-params-to-headers
 * change design.md Decision 1：5 个签名相关请求头统一不带 {@code X-} 前缀，且与内部字段名
 * 完全同名，不需要额外维护"内部字段名 → 请求头名"的映射）。
 */
public final class SignConstants {

    /** 应用 AccessKey 请求头名称，签名开关无论开关都要传，用于识别调用方。 */
    public static final String HEADER_APP_KEY = "appKey";

    /** 签名方法请求头名称。 */
    public static final String HEADER_SIGN_METHOD = "signMethod";

    /** 13 位毫秒时间戳请求头名称。 */
    public static final String HEADER_TIMESTAMP = "ts";

    /** 随机数请求头名称，用于防重放。 */
    public static final String HEADER_NONCE = "nonce";

    /** 签名结果请求头名称。 */
    public static final String HEADER_SIGNATURE = "signature";

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
