package cn.nihility.rbac.sync.sign;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 出站通知请求签名参数构造工具（app-sync-notify-pull-api change design.md Decision 8/10）：
 * {@code needSign} 开启时，给定基础 URL、签名算法、密钥明文与请求体，生成
 * {@code signMethod}/{@code ts}/{@code appKey}/{@code nonce}/{@code signature} 并拼接到
 * URL query 上，供 {@code AppNotifyService} 发起通知请求前调用。
 */
@Component
@RequiredArgsConstructor
public class NotifySignatureAppender {

    /** 签名算法编解码接口。 */
    private final SignAlgorithmCodec signAlgorithmCodec;

    /**
     * 按 {@code needSign} 开关决定是否为通知 URL 追加签名 query 参数。
     *
     * @param baseUrl       通知回调接口地址
     * @param needSign      是否需要签名
     * @param signAlgorithm 签名算法：{@code SHA256} 或 {@code SM3}
     * @param accessKey     应用 AccessKey
     * @param secretKey     应用 SecretKey 明文
     * @param requestBody   请求体原始 JSON 字符串，POST 请求恒非空
     * @return 追加签名参数后的完整 URL；{@code needSign=false} 时原样返回 {@code baseUrl}
     */
    public String appendSignatureIfNeeded(String baseUrl, boolean needSign, String signAlgorithm, String accessKey,
            String secretKey, String requestBody) {
        if (!needSign) {
            return baseUrl;
        }

        Map<String, String> signParams = new LinkedHashMap<>();
        signParams.put(SignConstants.QUERY_KEY_APP_KEY, accessKey);
        signParams.put(SignConstants.QUERY_KEY_SIGN_METHOD, SignConstants.signMethodOf(signAlgorithm));
        signParams.put(SignConstants.QUERY_KEY_TS, String.valueOf(System.currentTimeMillis()));
        signParams.put(SignConstants.QUERY_KEY_NONCE, UUID.randomUUID().toString().replace("-", ""));

        String urlSign = signAlgorithmCodec.hmac(signAlgorithm, secretKey, SignCanonicalizer.canonicalize(signParams));
        String signature = urlSign;
        if (StringUtils.hasText(requestBody)) {
            signature += signAlgorithmCodec.hmac(signAlgorithm, secretKey, requestBody);
        }
        signParams.put(SignConstants.QUERY_KEY_SIGNATURE, signature);

        StringBuilder url = new StringBuilder(baseUrl);
        url.append(baseUrl.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            if (!first) {
                url.append('&');
            }
            url.append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return url.toString();
    }
}
