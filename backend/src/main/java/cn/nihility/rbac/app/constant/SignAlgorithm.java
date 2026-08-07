package cn.nihility.rbac.app.constant;

/**
 * 应用对外接口签名算法常量，落库为可读字符串值（而非整型码值），供 {@code tab_app_config.sign_algorithm}
 * 列与请求校验复用。
 */
public final class SignAlgorithm {

    /** SHA-256。 */
    public static final String SHA256 = "SHA256";

    /** SM3（国密）。 */
    public static final String SM3 = "SM3";

    /**
     * 工具类不允许实例化。
     */
    private SignAlgorithm() {
    }
}
