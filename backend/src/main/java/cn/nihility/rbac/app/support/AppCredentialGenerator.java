package cn.nihility.rbac.app.support;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 应用对外接口凭证生成工具类（app-api-credentials-config change design.md Decision 4）：
 * 复用 {@link cn.nihility.rbac.auth.util.PasswordDigestUtils#randomSalt()} 同款
 * {@link SecureRandom} + 十六进制编码模式，AppId/AccessKey/SecretKey 均不附加前缀，
 * 纯随机十六进制字符串。
 */
public final class AppCredentialGenerator {

    /** AppId 随机部分字节长度，对应 24 位十六进制字符（12 字节熵）。 */
    private static final int OPEN_APP_ID_BYTE_LENGTH = 12;

    /** AccessKey 随机部分字节长度，对应 32 位十六进制字符（16 字节熵）。 */
    private static final int ACCESS_KEY_BYTE_LENGTH = 16;

    /** SecretKey 字节长度，对应 48 位十六进制字符（24 字节熵）。 */
    private static final int SECRET_KEY_BYTE_LENGTH = 24;

    /** 安全随机数生成器，供凭证生成复用。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 工具类不允许实例化。
     */
    private AppCredentialGenerator() {
    }

    /**
     * 生成一个对外应用标识（AppId）。
     *
     * @return 24 位随机十六进制小写字符串
     */
    public static String generateOpenAppId() {
        return randomHex(OPEN_APP_ID_BYTE_LENGTH);
    }

    /**
     * 生成一个 AccessKey。
     *
     * @return 32 位随机十六进制小写字符串
     */
    public static String generateAccessKey() {
        return randomHex(ACCESS_KEY_BYTE_LENGTH);
    }

    /**
     * 生成一个 SecretKey。
     *
     * @return 48 位随机十六进制小写字符串
     */
    public static String generateSecretKey() {
        return randomHex(SECRET_KEY_BYTE_LENGTH);
    }

    /**
     * 生成指定字节长度的随机十六进制小写字符串。
     *
     * @param byteLength 随机字节长度
     * @return 十六进制小写字符串，长度为 {@code byteLength * 2}
     */
    private static String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
