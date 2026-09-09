package cn.nihility.rbac.captcha.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 图形验证码生成接口响应体：仅包含验证码 id 与图片，不包含明文答案（design.md Decision 2）。
 */
@Getter
@Builder
public class CaptchaImageVO {

    /** 验证码 id，后续校验时提交同一 id。 */
    private final String captchaId;

    /** base64 编码的图片数据，供前端直接渲染为 {@code <img>} 的 {@code src}。 */
    private final String imageBase64;
}
