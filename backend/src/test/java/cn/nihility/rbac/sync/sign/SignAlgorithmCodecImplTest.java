package cn.nihility.rbac.sync.sign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * {@link SignAlgorithmCodecImpl} 的单元测试，重点覆盖 HMAC-SHA256/HMAC-SM3 的计算正确性
 * （同输入同输出、不同输入不同输出、固定长度十六进制）与非法算法拒绝分支。
 */
class SignAlgorithmCodecImplTest {

    /** 被测实例。 */
    private final SignAlgorithmCodecImpl codec = new SignAlgorithmCodecImpl();

    /**
     * HMAC-SHA256 对同样的密钥与内容应始终产生同样的摘要，且是 64 位小写十六进制。
     */
    @Test
    void hmac_sha256_shouldBeDeterministicAndHex64() {
        String digest1 = codec.hmac(SignAlgorithm.SHA256, "secret", "hello world");
        String digest2 = codec.hmac(SignAlgorithm.SHA256, "secret", "hello world");

        assertThat(digest1).isEqualTo(digest2);
        assertThat(digest1).hasSize(64);
        assertThat(digest1).matches("^[0-9a-f]{64}$");
    }

    /**
     * HMAC-SM3 对同样的密钥与内容应始终产生同样的摘要，且是 64 位小写十六进制（SM3 摘要
     * 长度同为 32 字节）。
     */
    @Test
    void hmac_sm3_shouldBeDeterministicAndHex64() {
        String digest1 = codec.hmac(SignAlgorithm.SM3, "secret", "hello world");
        String digest2 = codec.hmac(SignAlgorithm.SM3, "secret", "hello world");

        assertThat(digest1).isEqualTo(digest2);
        assertThat(digest1).hasSize(64);
        assertThat(digest1).matches("^[0-9a-f]{64}$");
    }

    /**
     * 两种算法对同一内容应产生不同的摘要结果。
     */
    @Test
    void hmac_sha256AndSm3_shouldProduceDifferentDigests() {
        String sha256Digest = codec.hmac(SignAlgorithm.SHA256, "secret", "hello world");
        String sm3Digest = codec.hmac(SignAlgorithm.SM3, "secret", "hello world");

        assertThat(sha256Digest).isNotEqualTo(sm3Digest);
    }

    /**
     * 内容或密钥变化时，摘要结果应随之变化（篡改可被检测出来的前提条件）。
     */
    @Test
    void hmac_shouldChangeWhenContentOrKeyChanges() {
        String original = codec.hmac(SignAlgorithm.SHA256, "secret", "a=1&b=2");
        String tamperedContent = codec.hmac(SignAlgorithm.SHA256, "secret", "a=1&b=3");
        String tamperedKey = codec.hmac(SignAlgorithm.SHA256, "other-secret", "a=1&b=2");

        assertThat(original).isNotEqualTo(tamperedContent);
        assertThat(original).isNotEqualTo(tamperedKey);
    }

    /**
     * 非法的签名算法应拒绝并抛出业务异常。
     */
    @Test
    void hmac_shouldRejectUnsupportedAlgorithm() {
        assertThatThrownBy(() -> codec.hmac("MD5", "secret", "content"))
                .isInstanceOf(BusinessException.class);
    }
}
