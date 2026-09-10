package cn.nihility.rbac.captcha.controller;

import cn.nihility.rbac.captcha.dto.CaptchaImageVO;
import cn.nihility.rbac.captcha.service.ImageCaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码接口（add-captcha-rate-limit change tasks.md 1.7）：只提供生成能力，一次性
 * 校验以 {@link ImageCaptchaService#verify} Java 方法形式供其他后端模块调用，本次不接入
 * 任何现有登录接口。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "图形验证码", description = "图形验证码生成接口")
public class ImageCaptchaController {

    /** 图形验证码业务逻辑接口。 */
    private final ImageCaptchaService imageCaptchaService;

    /**
     * 生成一个图形验证码，无需身份校验即可访问。
     *
     * @return 验证码 id 与 base64 图片
     */
    @Operation(summary = "生成图形验证码",
            description = "响应仅包含验证码 id 与图片，不包含明文答案；图片字段为带 data:image/png;base64, "
                    + "前缀的完整 data URI，前端可直接作为 <img> 的 src")
    @GetMapping("/api/captcha/image")
    public CaptchaImageVO generate() {
        return imageCaptchaService.generate();
    }
}
