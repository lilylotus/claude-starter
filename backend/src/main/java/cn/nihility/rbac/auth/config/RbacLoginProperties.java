package cn.nihility.rbac.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录相关配置，绑定前缀 {@code rbac.user.login}：RSA 公私钥（Base64 编码的 X.509/PKCS8 DER）
 * 与 access-key/refresh-key 有效期（password-login-auth change design.md Decision 3/4）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.user.login")
public class RbacLoginProperties {

    /** RSA 公钥（X.509 DER + Base64），供 {@code GET /api/auth/public-key} 返回给客户端加密登录信息。 */
    private String publicKey;

    /** RSA 私钥（PKCS8 DER + Base64），仅后端持有，用于解密登录请求中的账号、密码密文。 */
    private String privateKey;

    /** access-key 有效期（秒），默认 7200 秒（2 小时）。 */
    private long accessTokenExpireSeconds = 7200;

    /** refresh-key 有效期（秒），默认 604800 秒（7 天）。 */
    private long refreshTokenExpireSeconds = 604800;
}
