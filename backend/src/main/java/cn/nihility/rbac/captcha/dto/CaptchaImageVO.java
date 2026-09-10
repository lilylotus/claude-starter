package cn.nihility.rbac.captcha.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 图形验证码生成接口响应体：仅包含验证码 id 与图片，不包含明文答案（design.md Decision 2）。
 */
@Getter
@Builder
@Schema(description = "图形验证码生成响应")
public class CaptchaImageVO {

    /** 验证码 id，后续校验时提交同一 id。 */
    @Schema(description = "验证码 id，后续校验时提交同一 id", example = "3f2a1b9c4d5e6f7a8b9c0d1e2f3a4b5c")
    private final String captchaId;

    /** 带 {@code data:image/png;base64,} 前缀的完整 data URI，前端可直接作为 {@code <img>} 的 {@code src}。 */
    @Schema(description = "带 data:image/png;base64, 前缀的完整 data URI，前端可直接作为 <img> 的 src",
            example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
    private final String imageBase64;
}
