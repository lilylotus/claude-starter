package cn.nihility.rbac.captcha.config;

import cn.nihility.rbac.captcha.constant.CaptchaType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图形验证码相关配置，绑定前缀 {@code rbac.captcha}：验证码类型、字符模式的字符个数、
 * 图片宽高、有效期秒数（add-captcha-rate-limit change tasks.md 1.1），全部为默认值，
 * 运维可按环境在 {@code application.yml} 中覆盖。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.captcha")
public class RbacCaptchaProperties {

    /** 验证码类型（字符/算式），默认字符模式。 */
    private CaptchaType type = CaptchaType.CHARACTER;

    /** 字符模式下的字符个数，默认 4 个。 */
    private int characterLength = 4;

    /** 验证码图片宽度（像素），默认 120。 */
    private int imageWidth = 120;

    /** 验证码图片高度（像素），默认 40。 */
    private int imageHeight = 40;

    /** 验证码有效期（秒），默认 300 秒（5 分钟）。 */
    private long expireSeconds = 300;
}
