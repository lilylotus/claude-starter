package cn.nihility.rbac.sso.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSO 短信验证码登录相关配置，绑定前缀 {@code rbac.sms}：验证码有效期、发送冷却时长、
 * 每日发送上限、连续校验失败上限（add-sso-login-methods change design.md Decision 4）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.sms")
public class RbacSmsProperties {

    /** 验证码有效期（秒），默认 300 秒（5 分钟）。 */
    private long codeExpireSeconds = 300;

    /** 同一手机号两次发送验证码之间的最小间隔（秒），默认 60 秒。 */
    private long cooldownSeconds = 60;

    /** 同一手机号每日最多可发送验证码次数，默认 10 次。 */
    private int dailyLimit = 10;

    /** 同一手机号连续校验失败的最大次数，达到后当前验证码失效需重新获取，默认 5 次。 */
    private int maxAttempts = 5;
}
