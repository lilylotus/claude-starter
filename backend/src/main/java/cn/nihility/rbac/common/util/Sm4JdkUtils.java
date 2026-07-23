package cn.nihility.rbac.common.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

/**
 * SM4 国密对称加密工具类，基于 JDK 标准 KeyGenerator / Cipher API + BC Provider 实现。
 * 提供对称密钥生成、明文对称加密、密文对称解密能力，密钥及密文均使用 Base64 编码。
 * <p>
 * 加密模式：SM4/CBC/PKCS7Padding，每次加密使用随机 16 字节 IV，密文格式为 IV + 密文 后整体 Base64 编码。
 */
public class Sm4JdkUtils {

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

    private static final String ALGORITHM_SM4 = "SM4";
    private static final String CIPHER_ALGORITHM_SM4 = "SM4/CBC/PKCS7Padding";
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    private static final int KEY_SIZE_BIT = 128;
    private static final int IV_LENGTH_BYTE = 16;

    private Sm4JdkUtils() {
    }

    /**
     * 随机生成SM4对称密钥（Base64编码）
     *
     * @return Base64编码的128位SM4密钥
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM_SM4, PROVIDER_NAME);
            keyGenerator.init(KEY_SIZE_BIT, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("生成SM4密钥失败", e);
        }
    }

    /**
     * 使用SM4密钥对明文进行对称加密，返回Base64编码密文（IV + 密文）
     *
     * @param plainText 待加密明文
     * @param keyBase64 Base64编码的SM4密钥
     * @return Base64编码密文
     */
    public static String encrypt(String plainText, String keyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM_SM4);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_SM4, PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("SM4加密失败", e);
        }
    }

    /**
     * 使用SM4密钥对Base64密文（IV + 密文）进行对称解密，返回明文
     *
     * @param cipherTextBase64 Base64编码密文（encrypt方法返回值）
     * @param keyBase64        Base64编码的SM4密钥
     * @return 解密后的明文
     */
    public static String decrypt(String cipherTextBase64, String keyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM_SM4);

            byte[] combined = Base64.getDecoder().decode(cipherTextBase64);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byte[] encrypted = new byte[combined.length - IV_LENGTH_BYTE];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE);
            System.arraycopy(combined, IV_LENGTH_BYTE, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_SM4, PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM4解密失败", e);
        }
    }

    public static void main(String[] args) {
        String key = generateKey();
        System.out.println("key [" + key + "]");

        String msg = "hello 国密SM4";
        String encrypted = encrypt(msg, key);
        System.out.println("encrypted [" + encrypted + "]");

        String decrypted = decrypt(encrypted, key);
        System.out.println("decrypted [" + decrypted + "]");
    }

}
