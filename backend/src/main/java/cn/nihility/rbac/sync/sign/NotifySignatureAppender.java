package cn.nihility.rbac.sync.sign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 出站通知请求签名请求头构造工具（app-sync-notify-pull-api change design.md Decision 8/10；
 * move-sync-sign-params-to-headers change design.md Decision 2/3）：{@code needSign} 开启时，
 * 给定签名算法、密钥明文与请求体，生成 {@code appKey}/{@code signMethod}/{@code ts}/
 * {@code nonce}/{@code signature} 请求头，供 {@code AppNotifyService} 发起通知请求前调用；
 * {@code needSign} 关闭时仅返回 {@code appKey} 一项。
 */
@Component
@RequiredArgsConstructor
public class NotifySignatureAppender {

    /** 签名算法编解码接口。 */
    private final SignAlgorithmCodec signAlgorithmCodec;

    /**
     * 构造出站通知请求需要携带的签名相关请求头。
     *
     * @param needSign      是否需要签名
     * @param signAlgorithm 签名算法：{@code SHA256} 或 {@code SM3}
     * @param accessKey     应用 AccessKey
     * @param secretKey     应用 SecretKey 明文
     * @param requestBody   请求体原始 JSON 字符串，POST 请求恒非空
     * @return 待设置到 HTTP 请求上的请求头 Map；{@code needSign=false} 时仅含 {@code appKey} 一项
     */
    public Map<String, String> buildSignatureHeaders(boolean needSign, String signAlgorithm, String accessKey,
            String secretKey, String requestBody) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SignConstants.HEADER_APP_KEY, accessKey);
        if (!needSign) {
            return headers;
        }

        String signMethod = SignConstants.signMethodOf(signAlgorithm);
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");

        Map<String, String> signParams = new LinkedHashMap<>();
        signParams.put(SignConstants.HEADER_APP_KEY, accessKey);
        signParams.put(SignConstants.HEADER_SIGN_METHOD, signMethod);
        signParams.put(SignConstants.HEADER_TIMESTAMP, ts);
        signParams.put(SignConstants.HEADER_NONCE, nonce);

        String urlSign = signAlgorithmCodec.hmac(signAlgorithm, secretKey, SignCanonicalizer.canonicalize(signParams));
        String signature = urlSign;
        if (StringUtils.hasText(requestBody)) {
            signature += signAlgorithmCodec.hmac(signAlgorithm, secretKey, requestBody);
        }

        headers.put(SignConstants.HEADER_SIGN_METHOD, signMethod);
        headers.put(SignConstants.HEADER_TIMESTAMP, ts);
        headers.put(SignConstants.HEADER_NONCE, nonce);
        headers.put(SignConstants.HEADER_SIGNATURE, signature);
        return headers;
    }
}
