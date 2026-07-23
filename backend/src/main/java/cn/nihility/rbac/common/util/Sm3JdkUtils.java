package cn.nihility.rbac.common.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.Base64;

/**
 * SM3 国密摘要签名工具类，基于 JDK 标准 MessageDigest API + BC Provider 实现。
 * 对明文进行 SM3 摘要计算，返回 Base64 编码结果。
 */
public class Sm3JdkUtils {

    private static final double MIN_REQUIRED_VERSION = 1.59;

    static {
        if (null != Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)) {
            double version = Double.parseDouble(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME).getVersionStr());
            if (version < MIN_REQUIRED_VERSION) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
                Security.addProvider(new BouncyCastleProvider());
            }
        } else {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String ALGORITHM_SM3 = "SM3";
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    private Sm3JdkUtils() {
    }

    /**
     * 对明文进行SM3摘要签名，返回Base64编码结果
     *
     * @param plainText 明文字符串（UTF-8）
     * @return Base64编码的SM3摘要值
     */
    public static String sign(String plainText) {
        return sign(plainText.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 对明文字节数组进行SM3摘要签名，返回Base64编码结果
     *
     * @param plainData 明文字节数组
     * @return Base64编码的SM3摘要值
     */
    public static String sign(byte[] plainData) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM_SM3, PROVIDER_NAME);
            byte[] hash = digest.digest(plainData);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("SM3签名失败", e);
        }
    }

    public static void main(String[] args) {
        String msg = "hello 国密SM3";
        String sign = sign(msg);
        System.out.println("sign [" + sign + "]");
    }

}
