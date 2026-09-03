package cn.nihility.rbac.workflow.dslv2.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文本摘要工具，供发布产物/审核记录/试运行报告比对是否失效使用（production-approval-lifecycle
 * change design.md Decision 4）。
 */
public final class DigestUtils {

    /** 工具类不允许实例化。 */
    private DigestUtils() {
    }

    /**
     * 计算文本的 SHA-256 摘要（十六进制小写）。
     *
     * @param text 待摘要文本
     * @return 摘要十六进制字符串
     */
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }
}
