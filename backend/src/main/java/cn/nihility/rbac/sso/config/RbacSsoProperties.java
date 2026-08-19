package cn.nihility.rbac.sso.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSO 单点登录运行时相关配置，绑定前缀 {@code rbac.sso}：浏览器会话 Cookie 是否要求
 * {@code Secure} 属性、SSO 会话与各类协议凭证（CAS 票据、OAuth2 授权码/access token/
 * refresh token）的有效期（app-sso-protocol-runtime change design.md Decision 2/5/6）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.sso")
public class RbacSsoProperties {

    /**
     * SSO 会话 Cookie 是否追加 {@code Secure} 属性，默认 {@code false}（本地 http 开发环境）；
     * 生产环境部署在 https 之后应设为 {@code true}。
     */
    private boolean cookieSecure = false;

    /** SSO 会话有效期（秒），默认 28800 秒（8 小时），固定过期，不做滑动续期。 */
    private long sessionExpireSeconds = 28800;

    /** CAS 服务票据（ST）有效期（秒），默认 120 秒，一次性短期凭证。 */
    private long casTicketExpireSeconds = 120;

    /** OAuth2 授权码有效期（秒），默认 300 秒（5 分钟），对齐 RFC 6749 建议的短有效期。 */
    private long oauthCodeExpireSeconds = 300;

    /** OAuth2 access token 有效期（秒），默认 7200 秒（2 小时）。 */
    private long oauthTokenExpireSeconds = 7200;

    /**
     * OAuth2 refresh token 有效期（秒），默认 86400 秒（1 天，add-sso-single-logout change
     * design.md Decision 6，原 1209600 秒/14 天）。刷新时按轮转模式签发新值，接入方须保证
     * 至少每天刷新一次，否则需重新走一遍完整授权流程。
     */
    private long oauthRefreshTokenExpireSeconds = 86400;
}
