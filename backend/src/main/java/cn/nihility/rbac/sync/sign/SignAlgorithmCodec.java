package cn.nihility.rbac.sync.sign;

/**
 * 签名算法编解码接口，统一封装 HMAC-SHA256（对齐 {@code SignAlgorithm.SHA256}）与
 * HMAC-SM3（对齐 {@code SignAlgorithm.SM3}）两种签名方式的计算（app-sync-notify-pull-api
 * change design.md Decision 10）。出站通知请求签名与入站拉取请求验签均复用同一实现，
 * 保证生成方与校验方计算规则一致。
 */
public interface SignAlgorithmCodec {

    /**
     * 按给定签名算法计算 HMAC 摘要。
     *
     * @param signAlgorithm 签名算法：{@code SHA256} 或 {@code SM3}（{@code SignAlgorithm} 常量）
     * @param secretKey     参与运算的密钥明文
     * @param content       待签名内容
     * @return 小写十六进制摘要字符串
     */
    String hmac(String signAlgorithm, String secretKey, String content);
}
