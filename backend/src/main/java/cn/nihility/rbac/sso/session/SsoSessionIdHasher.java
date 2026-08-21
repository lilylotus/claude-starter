package cn.nihility.rbac.sso.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.util.StringUtils;

/**
 * SSO 会话标识哈希工具类（add-sso-protocol-access-log change design.md Decision 6）：
 * 把 SSO 会话令牌转换为可安全落库、用于日志关联查询的会话标识。SSO 会话令牌本质是一个
 * 持有期内可直接冒充该用户完成 SSO 免密登录的 bearer 凭据（{@link SsoSessionCookieUtils}/
 * {@code ssoSessionService.verify} 只看这个令牌本身，不做二次身份校验），如果直接落存
 * 明文令牌到只读查询接口暴露的日志表（{@code tab_login_log}/{@code tab_sso_protocol_log}），
 * 等于把一个仍在有效期内的可用凭据摆在了日志页面上。
 *
 * <p>本工具固定使用无盐的 SHA-256 摘要，不能像 {@link cn.nihility.rbac.auth.util.PasswordDigestUtils}
 * 那样加随机盐——加盐会导致同一个原始令牌每次计算出的摘要都不同，会话/日志的关联查询
 * （同一个令牌产生同一个标识）就失去意义了。令牌本身是高熵随机值（32 位随机十六进制或
 * 等价长度），不加盐的 SHA-256 摘要已经足够抵御彩虹表反查，安全性上是可接受的取舍。
 *
 * <p>展示/落库的会话标识只取 SHA-256 摘要的前 16 字节（128 位，32 位十六进制字符串），
 * 不使用完整 64 位摘要——128 位仍远超日志表可能出现的会话数量级，碰撞概率可忽略不计，
 * 缩短后在登录日志/协议调用记录表格中展示更友好，不影响关联查询的唯一性保证。
 */
public final class SsoSessionIdHasher {

    /** 摘要算法。 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /** 会话标识截取的摘要字节数（128 位），对应 32 位十六进制字符串。 */
    private static final int SESSION_ID_BYTES = 16;

    /**
     * 工具类不允许实例化。
     */
    private SsoSessionIdHasher() {
    }

    /**
     * 计算 SSO 会话令牌的 SHA-256 摘要并截取前 128 位，作为可安全落库、便于展示的会话标识。
     *
     * @param sessionToken 原始 SSO 会话令牌
     * @return 32 位十六进制小写摘要字符串，{@code sessionToken} 为 {@code null}/空白时返回 {@code null}
     */
    public static String hash(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            return null;
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hash = messageDigest.digest(sessionToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, SESSION_ID_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("计算 SSO 会话标识摘要失败", e);
        }
    }
}
