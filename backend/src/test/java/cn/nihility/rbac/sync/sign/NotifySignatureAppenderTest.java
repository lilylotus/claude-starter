package cn.nihility.rbac.sync.sign;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.app.constant.SignAlgorithm;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link NotifySignatureAppender} 的单元测试：验证 {@code needSign=false} 时仅返回
 * {@code appKey} 一项、{@code needSign=true} 时正确返回签名请求头，且携带请求体时签名长度
 * 为 128 位（urlSign + bodySign 拼接，design.md Decision 10；
 * move-sync-sign-params-to-headers change design.md Decision 2/3）。
 */
class NotifySignatureAppenderTest {

    /** 被测实例，使用真实的签名编解码实现。 */
    private final NotifySignatureAppender appender = new NotifySignatureAppender(new SignAlgorithmCodecImpl());

    /**
     * {@code needSign=false} 时应仅返回 {@code appKey} 一项请求头。
     */
    @Test
    void buildSignatureHeaders_shouldOnlyContainAppKeyWhenNeedSignFalse() {
        Map<String, String> headers = appender.buildSignatureHeaders(false, SignAlgorithm.SHA256, "accessKey",
                "secretKey", "{\"a\":1}");

        assertThat(headers).containsExactly(Map.entry(SignConstants.HEADER_APP_KEY, "accessKey"));
    }

    /**
     * {@code needSign=true} 且携带请求体时，应返回 appKey/signMethod/ts/nonce/signature
     * 五个请求头，{@code signature} 长度为 128（urlSign 64 位 + bodySign 64 位）。
     */
    @Test
    void buildSignatureHeaders_shouldReturnSignatureHeadersWithBody() {
        Map<String, String> headers = appender.buildSignatureHeaders(true, SignAlgorithm.SHA256, "accessKey",
                "secretKey", "{\"a\":1}");

        assertThat(headers.get(SignConstants.HEADER_APP_KEY)).isEqualTo("accessKey");
        assertThat(headers.get(SignConstants.HEADER_SIGN_METHOD)).isEqualTo(SignConstants.SIGN_METHOD_HMAC_SHA256);
        assertThat(headers).containsKey(SignConstants.HEADER_TIMESTAMP);
        assertThat(headers).containsKey(SignConstants.HEADER_NONCE);
        assertThat(headers.get(SignConstants.HEADER_SIGNATURE)).hasSize(128);
        assertThat(headers.get(SignConstants.HEADER_SIGNATURE)).matches("^[0-9a-f]{128}$");
    }

    /**
     * 不携带请求体（如 GET）时，{@code signature} 长度应为 64（只有 urlSign）。
     */
    @Test
    void buildSignatureHeaders_shouldOnlyUrlSignWithoutBody() {
        Map<String, String> headers = appender.buildSignatureHeaders(true, SignAlgorithm.SM3, "accessKey",
                "secretKey", null);

        assertThat(headers.get(SignConstants.HEADER_SIGN_METHOD)).isEqualTo(SignConstants.SIGN_METHOD_HMAC_SM3);
        assertThat(headers.get(SignConstants.HEADER_SIGNATURE)).hasSize(64);
    }

    /**
     * 每次调用应生成不同的 nonce（随机数不重复，防重放的前提条件）。
     */
    @Test
    void buildSignatureHeaders_shouldGenerateDifferentNoncePerCall() {
        Map<String, String> headers1 = appender.buildSignatureHeaders(true, SignAlgorithm.SHA256, "accessKey",
                "secretKey", "body");
        Map<String, String> headers2 = appender.buildSignatureHeaders(true, SignAlgorithm.SHA256, "accessKey",
                "secretKey", "body");

        assertThat(headers1.get(SignConstants.HEADER_NONCE)).isNotEqualTo(headers2.get(SignConstants.HEADER_NONCE));
    }
}
