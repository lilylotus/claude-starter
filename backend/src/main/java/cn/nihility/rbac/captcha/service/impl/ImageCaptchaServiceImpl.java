package cn.nihility.rbac.captcha.service.impl;

import cn.nihility.rbac.captcha.config.RbacCaptchaProperties;
import cn.nihility.rbac.captcha.constant.CaptchaType;
import cn.nihility.rbac.captcha.dto.CaptchaImageVO;
import cn.nihility.rbac.captcha.service.ImageCaptchaService;
import cn.nihility.rbac.captcha.support.CaptchaAnswerPayload;
import cn.nihility.rbac.captcha.support.CaptchaContentGenerator;
import cn.nihility.rbac.captcha.support.CaptchaGenerationResult;
import cn.nihility.rbac.captcha.support.CaptchaImageRenderer;
import cn.nihility.rbac.common.util.RedisUtils;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link ImageCaptchaService} 的默认实现（add-captcha-rate-limit change tasks.md 1.6/2.1）。
 */
@Service
@RequiredArgsConstructor
public class ImageCaptchaServiceImpl implements ImageCaptchaService {

    /** 验证码答案 Redis key 前缀，完整 key 为该前缀 + 验证码 id。 */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:image:";

    /** 图形验证码相关配置。 */
    private final RbacCaptchaProperties captchaProperties;

    /** 验证码内容生成器。 */
    private final CaptchaContentGenerator captchaContentGenerator;

    /** 验证码图片渲染器。 */
    private final CaptchaImageRenderer captchaImageRenderer;

    /**
     * {@inheritDoc}
     */
    @Override
    public CaptchaImageVO generate() {
        CaptchaGenerationResult generationResult = captchaContentGenerator.generate();
        String captchaId = UUID.randomUUID().toString().replace("-", "");

        CaptchaAnswerPayload payload = new CaptchaAnswerPayload(captchaProperties.getType(), generationResult.answer());
        RedisUtils.setObject(buildKey(captchaId), payload, captchaProperties.getExpireSeconds(), TimeUnit.SECONDS);

        String imageBase64 = captchaImageRenderer.renderToBase64(generationResult.displayText(),
                captchaProperties.getImageWidth(), captchaProperties.getImageHeight());
        return CaptchaImageVO.builder().captchaId(captchaId).imageBase64(imageBase64).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verify(String captchaId, String answer) {
        String key = buildKey(captchaId);
        Optional<CaptchaAnswerPayload> payloadOpt = RedisUtils.getObject(key, CaptchaAnswerPayload.class);
        if (payloadOpt.isEmpty()) {
            return false;
        }
        // 无论校验成功或失败，都立即删除，保证一次性、不可重复提交同一 id 校验。
        RedisUtils.delete(key);

        CaptchaAnswerPayload payload = payloadOpt.get();
        if (payload.type() == CaptchaType.CHARACTER) {
            return payload.answer().equalsIgnoreCase(answer);
        }
        return matchesArithmeticAnswer(payload.answer(), answer);
    }

    /**
     * 按数值精确匹配算式模式的答案。
     *
     * @param storedAnswer    Redis 中存储的正确答案
     * @param submittedAnswer 用户提交的答案
     * @return 数值是否相等；提交内容无法解析为整数时视为不匹配
     */
    private boolean matchesArithmeticAnswer(String storedAnswer, String submittedAnswer) {
        if (submittedAnswer == null) {
            return false;
        }
        try {
            return Integer.parseInt(storedAnswer.trim()) == Integer.parseInt(submittedAnswer.trim());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 拼接验证码 Redis key。
     *
     * @param captchaId 验证码 id
     * @return 完整 Redis key
     */
    private String buildKey(String captchaId) {
        return CAPTCHA_KEY_PREFIX + captchaId;
    }
}
