package cn.nihility.rbac.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密码摘要工具类（password-login-auth change design.md Decision 2）：盐值使用
 * {@link SecureRandom} 生成 16 字节并十六进制编码，摘要固定为
 * {@code SHA-256(明文密码 + 盐值)} 的十六进制小写字符串，不落明文密码。
 */
public final class PasswordDigestUtils {

    /** 摘要算法。 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /** 盐值字节长度。 */
    private static final int SALT_BYTE_LENGTH = 16;

    /** 安全随机数生成器，供盐值生成复用。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 工具类不允许实例化。
     */
    private PasswordDigestUtils() {
    }

    /**
     * 生成一个随机盐值。
     *
     * @return 32 位十六进制小写字符串（16 字节）
     */
    public static String randomSalt() {
        byte[] salt = new byte[SALT_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * 计算 {@code SHA-256(明文密码 + 盐值)} 摘要。
     *
     * @param plainPassword 明文密码
     * @param salt          盐值（十六进制字符串）
     * @return 64 位十六进制小写摘要字符串
     */
    public static String digest(String plainPassword, String salt) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hash = messageDigest.digest((plainPassword + salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("计算密码摘要失败", e);
        }
    }
}
