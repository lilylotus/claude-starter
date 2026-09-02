package cn.nihility.rbac.sso.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSO 扫码登录相关配置，绑定前缀 {@code rbac.qrcode}：二维码登录会话有效期（add-sso-login-methods
 * change design.md Decision 5）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.qrcode")
public class RbacQrcodeProperties {

    /** 二维码登录会话有效期（秒），默认 300 秒（5 分钟），过期后前端需刷新二维码重新获取会话。 */
    private long sessionExpireSeconds = 300;
}
