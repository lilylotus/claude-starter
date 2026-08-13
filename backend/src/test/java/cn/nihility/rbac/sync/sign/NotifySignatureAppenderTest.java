package cn.nihility.rbac.sync.sign;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.app.constant.SignAlgorithm;
import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@link NotifySignatureAppender} 的单元测试：验证 {@code needSign=false} 时原样返回、
 * {@code needSign=true} 时正确追加签名 query 参数，且携带请求体时签名长度为 128 位
 * （urlSign + bodySign 拼接，design.md Decision 10）。
 */
class NotifySignatureAppenderTest {

    /** 被测实例，使用真实的签名编解码实现。 */
    private final NotifySignatureAppender appender = new NotifySignatureAppender(new SignAlgorithmCodecImpl());

    /**
     * {@code needSign=false} 时应原样返回 URL，不追加任何签名参数。
     */
    @Test
    void appendSignatureIfNeeded_shouldReturnOriginalUrlWhenNeedSignFalse() {
        String url = appender.appendSignatureIfNeeded("https://example.com/notify", false, SignAlgorithm.SHA256,
                "accessKey", "secretKey", "{\"a\":1}");

        assertThat(url).isEqualTo("https://example.com/notify");
    }

    /**
     * {@code needSign=true} 且携带请求体时，应追加 appKey/signMethod/ts/nonce/signature
     * 五个 query 参数，{@code signature} 长度为 128（urlSign 64 位 + bodySign 64 位）。
     */
    @Test
    void appendSignatureIfNeeded_shouldAppendSignatureParamsWithBody() throws Exception {
        String url = appender.appendSignatureIfNeeded("https://example.com/notify", true, SignAlgorithm.SHA256,
                "accessKey", "secretKey", "{\"a\":1}");

        Map<String, String> params = parseQuery(url);
        assertThat(params.get(SignConstants.QUERY_KEY_APP_KEY)).isEqualTo("accessKey");
        assertThat(params.get(SignConstants.QUERY_KEY_SIGN_METHOD))
                .isEqualTo(SignConstants.SIGN_METHOD_HMAC_SHA256);
        assertThat(params).containsKey(SignConstants.QUERY_KEY_TS);
        assertThat(params).containsKey(SignConstants.QUERY_KEY_NONCE);
        assertThat(params.get(SignConstants.QUERY_KEY_SIGNATURE)).hasSize(128);
        assertThat(params.get(SignConstants.QUERY_KEY_SIGNATURE)).matches("^[0-9a-f]{128}$");
    }

    /**
     * 不携带请求体（如 GET）时，{@code signature} 长度应为 64（只有 urlSign）。
     */
    @Test
    void appendSignatureIfNeeded_shouldOnlyUrlSignWithoutBody() {
        String url = appender.appendSignatureIfNeeded("https://example.com/notify", true, SignAlgorithm.SM3,
                "accessKey", "secretKey", null);

        Map<String, String> params = parseQuery(url);
        assertThat(params.get(SignConstants.QUERY_KEY_SIGN_METHOD)).isEqualTo(SignConstants.SIGN_METHOD_HMAC_SM3);
        assertThat(params.get(SignConstants.QUERY_KEY_SIGNATURE)).hasSize(64);
    }

    /**
     * 每次调用应生成不同的 nonce（随机数不重复，防重放的前提条件）。
     */
    @Test
    void appendSignatureIfNeeded_shouldGenerateDifferentNoncePerCall() {
        String url1 = appender.appendSignatureIfNeeded("https://example.com/notify", true, SignAlgorithm.SHA256,
                "accessKey", "secretKey", "body");
        String url2 = appender.appendSignatureIfNeeded("https://example.com/notify", true, SignAlgorithm.SHA256,
                "accessKey", "secretKey", "body");

        String nonce1 = parseQuery(url1).get(SignConstants.QUERY_KEY_NONCE);
        String nonce2 = parseQuery(url2).get(SignConstants.QUERY_KEY_NONCE);
        assertThat(nonce1).isNotEqualTo(nonce2);
    }

    /**
     * 把 URL 的查询字符串解析为 Map，供断言使用。
     *
     * @param url 完整 URL
     * @return 查询参数 Map（值已 URL 解码）
     */
    private Map<String, String> parseQuery(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> result = new java.util.LinkedHashMap<>();
        Pattern pairPattern = Pattern.compile("([^&=]+)=([^&]*)");
        Matcher matcher = pairPattern.matcher(query);
        while (matcher.find()) {
            String key = java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(matcher.group(2), java.nio.charset.StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
