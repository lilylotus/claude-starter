package cn.nihility.rbac.sync.sign;

import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.stereotype.Component;

/**
 * {@link SignAlgorithmCodec} 默认实现：HMAC-SHA256 基于 JDK {@link Mac}，HMAC-SM3 基于
 * 已引入的 {@code bcprov-jdk18on}（{@link HMac} + {@link SM3Digest}），两者均返回小写
 * 十六进制摘要字符串（app-sync-notify-pull-api change design.md Decision 10）。
 */
@Component
public class SignAlgorithmCodecImpl implements SignAlgorithmCodec {

    /** JDK {@code Mac} HMAC-SHA256 算法名。 */
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * {@inheritDoc}
     */
    @Override
    public String hmac(String signAlgorithm, String secretKey, String content) {
        if (SignAlgorithm.SM3.equals(signAlgorithm)) {
            return hmacSm3(secretKey, content);
        }
        if (SignAlgorithm.SHA256.equals(signAlgorithm)) {
            return hmacSha256(secretKey, content);
        }
        throw new BusinessException("不支持的签名算法：" + signAlgorithm);
    }

    /**
     * 计算 HMAC-SHA256 摘要。
     *
     * @param secretKey 密钥明文
     * @param content   待签名内容
     * @return 小写十六进制摘要字符串
     */
    private String hmacSha256(String secretKey, String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    /**
     * 计算 HMAC-SM3 摘要。
     *
     * @param secretKey 密钥明文
     * @param content   待签名内容
     * @return 小写十六进制摘要字符串
     */
    private String hmacSm3(String secretKey, String content) {
        HMac hMac = new HMac(new SM3Digest());
        hMac.init(new KeyParameter(secretKey.getBytes(StandardCharsets.UTF_8)));
        byte[] input = content.getBytes(StandardCharsets.UTF_8);
        hMac.update(input, 0, input.length);
        byte[] digest = new byte[hMac.getMacSize()];
        hMac.doFinal(digest, 0);
        return HexFormat.of().formatHex(digest);
    }
}
