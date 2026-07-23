package cn.nihility.rbac.common.util;

import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * SM2 国密非对称加密工具类，基于 JDK 标准 KeyPairGenerator / Cipher / Signature API + BC Provider 实现。
 * 提供密钥对生成、加密、解密、签名、验签能力，公私钥及密文/签名均使用 Base64 编码。
 */
public class Sm2JdkUtils {

    private static final Logger log = LoggerFactory.getLogger(Sm2JdkUtils.class);

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

    private static final String ALGORITHM_EC = "EC";
    private static final String SM2_CURVE_NAME = "sm2p256v1";
    private static final String CIPHER_ALGORITHM_SM2 = "SM2";
    private static final String SIGNATURE_ALGORITHM_SM2 = "SM3withSM2";
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    private Sm2JdkUtils() {
    }

    /**
     * 随机生成一对SM2公钥/私钥（Base64编码）
     *
     * @return Pair.left为公钥（X.509 DER + Base64），Pair.right为私钥（PKCS#8 DER + Base64）
     */
    public static Pair<String, String> generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM_EC, PROVIDER_NAME);
            generator.initialize(new ECGenParameterSpec(SM2_CURVE_NAME), new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return Pair.of(publicKeyBase64, privateKeyBase64);
        } catch (Exception e) {
            log.error("生成SM2密钥对失败", e);
            throw new IllegalStateException("生成SM2密钥对失败", e);
        }
    }

    /**
     * 将Base64编码的X.509公钥字符串还原为PublicKey
     */
    public static PublicKey loadPublicKey(String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_EC, PROVIDER_NAME);
            return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("加载SM2公钥失败", e);
        }
    }

    /**
     * 将Base64编码的PKCS#8私钥字符串还原为PrivateKey
     */
    public static PrivateKey loadPrivateKey(String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_EC, PROVIDER_NAME);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("加载SM2私钥失败", e);
        }
    }

    /**
     * SM2公钥加密（密文格式C1C3C2），返回Base64编码密文
     */
    public static String encrypt(String plainText, String publicKeyBase64) {
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_SM2, PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, new SecureRandom());
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("SM2加密失败", e);
            throw new IllegalStateException("SM2加密失败", e);
        }
    }

    /**
     * SM2私钥解密Base64密文（密文格式C1C3C2），返回明文
     */
    public static String decrypt(String cipherTextBase64, String privateKeyBase64) {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_SM2, PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);
            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("SM2解密失败", e);
            throw new IllegalStateException("SM2解密失败", e);
        }
    }

    /**
     * SM2私钥签名（SM3withSM2），返回Base64编码签名值
     */
    public static String sign(String data, String privateKeyBase64) {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyBase64);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_SM2, PROVIDER_NAME);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            log.error("SM2签名失败", e);
            throw new IllegalStateException("SM2签名失败", e);
        }
    }

    /**
     * SM2公钥验签（SM3withSM2）。验签失败（数据被篡改、签名不匹配、密钥不匹配）返回false，不抛出异常
     */
    public static boolean verify(String data, String signatureBase64, String publicKeyBase64) {
        try {
            PublicKey publicKey = loadPublicKey(publicKeyBase64);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM_SM2, PROVIDER_NAME);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            log.warn("SM2验签失败", e);
            return false;
        }
    }

    public static void main(String[] args) {
        Pair<String, String> keyPair = generateKeyPair();
        String publicKey = keyPair.getLeft();
        String privateKey = keyPair.getRight();
        System.out.println("publicKey [" + publicKey + "]");
        System.out.println("privateKey [" + privateKey + "]");

        String msg = "hello 国密SM2";
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
