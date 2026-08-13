package cn.nihility.rbac.sync.sign;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 签名规范化字符串拼接工具（app-sync-notify-pull-api change design.md Decision 10 步骤
 * 1-3）：把除 {@code signature} 外的全部参与签名的 query 参数按 key 的 ASCII 码升序排列，
 * 以 {@code k1=v1&k2=v2&...} 拼接，值不做二次编码，用原始字符串值。生成方（出站通知）与
 * 校验方（入站拉取验签）共用同一份逻辑，保证计算规则一致。
 */
public final class SignCanonicalizer {

    /**
     * 工具类不允许实例化。
     */
    private SignCanonicalizer() {
    }

    /**
     * 把参数 Map 规范化拼接为签名原文。
     *
     * @param params 参与签名的参数（不含 {@code signature}）
     * @return 规范化拼接后的字符串
     */
    public static String canonicalize(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }
}
