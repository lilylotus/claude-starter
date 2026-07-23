package cn.nihility.rbac.common.util;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 非对称加密工具类，基于 JDK 标准 KeyPairGenerator / Cipher / Signature API 实现。
 * 提供密钥对生成、加密、解密、签名、验签能力，公私钥及密文/签名均使用 Base64 编码。
 * <p>
 * 加密算法：RSA/ECB/OAEPWithSHA-256AndMGF1Padding（MGF1 哈希显式指定为 SHA-256）。
 * 签名算法：SHA256withRSA。
 */
public class RsaJdkUtils {

    private static final Logger log = LoggerFactory.getLogger(RsaJdkUtils.class);

    private static final String ALGORITHM_RSA = "RSA";
    private static final String CIPHER_ALGORITHM_RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String SIGNATURE_ALGORITHM_RSA = "SHA256withRSA";
    private static final int DEFAULT_KEY_SIZE_BIT = 2048;

    private static final OAEPParameterSpec OAEP_PARAMS = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private RsaJdkUtils() {
    }

    /**
     * 随机生成一对默认2048位的RSA公钥/私钥（Base64编码）
     *
     * @return Pair.left为公钥（X.509 DER + Base64），Pair.right为私钥（PKCS#8 DER + Base64）
     */
    public static Pair<String, String> generateKeyPair() {
        return generateKeyPair(DEFAULT_KEY_SIZE_BIT);
    }

    /**
     * 随机生成一对指定位长的RSA公钥/私钥（Base64编码）
     *
     * @param keySize 密钥位长
     * @return Pair.left为公钥（X.509 DER + Base64），Pair.right为私钥（PKCS#8 DER + Base64）
     */
    public static Pair<String, String> generateKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM_RSA);
            generator.initialize(keySize, new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return Pair.of(publicKeyBase64, privateKeyBase64);
        } catch (Exception e) {
            log.error("生成RSA密钥对失败", e);
            throw new IllegalStateException("生成RSA密钥对失败", e);
        }
    }

    /**
     * 将Base64编码的X.509公钥字符串还原为PublicKey
     */
    public static PublicKey loadPublicKey(String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
            return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("加载RSA公钥失败", e);
        }
    }

    /**
     * 将Base64编码的PKCS#8私钥字符串还原为PrivateKey
     */
    public static PrivateKey loadPrivateKey(String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("加载RSA私钥失败", e);
        }
    }

    /**
     * RSA公钥加密，返回Base64编码密文
     */
    public static String encrypt(String plainText, String publicKeyBase64) {
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_RSA);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_PARAMS);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("RSA加密失败", e);
            throw new IllegalStateException("RSA加密失败", e);
        }
    }

    /**
     * RSA私钥解密Base64密文，返回明文
     */
    public static String decrypt(String cipherTextBase64, String privateKeyBase64) {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_RSA);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMS);
            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);
            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("RSA解密失败", e);
            throw new IllegalStateException("RSA解密失败", e);
        }
    }

    /**
     * RSA私钥签名（SHA256withRSA），返回Base64编码签名值
     */
    public static String sign(String data, String privateKeyBase64) {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_RSA);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            log.error("RSA签名失败", e);
            throw new IllegalStateException("RSA签名失败", e);
        }
    }

    /**
     * RSA公钥验签（SHA256withRSA）。验签失败（数据被篡改、签名不匹配、密钥不匹配）返回false，不抛出异常
     */
    public static boolean verify(String data, String signatureBase64, String publicKeyBase64) {
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_RSA);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            log.warn("RSA验签失败", e);
            return false;
        }
    }

    public static void main(String[] args) {
        Pair<String, String> keyPair = generateKeyPair();
        String publicKey = keyPair.getLeft();
        String privateKey = keyPair.getRight();
        System.out.println("publicKey [" + publicKey + "]");
        System.out.println("privateKey [" + privateKey + "]");

        String msg = "hello 你好 RSA";
        String encrypted = encrypt(msg, publicKey);
        String decrypted = decrypt(encrypted, privateKey);
        System.out.println("encrypted [" + encrypted + "]");
        System.out.println("decrypted [" + decrypted + "]");

        String sign = sign(msg, privateKey);
        boolean verify = verify(msg, sign, publicKey);
        System.out.println("sign [" + sign + "]");
        System.out.println("verify [" + verify + "]");
    }

}
