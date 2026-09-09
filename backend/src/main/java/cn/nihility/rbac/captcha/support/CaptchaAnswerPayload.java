package cn.nihility.rbac.captcha.support;

import cn.nihility.rbac.captcha.constant.CaptchaType;

/**
 * 写入 Redis {@code captcha:image:{captchaId}} key 的内部存储载荷（design.md Decision 2），
 * 含验证码类型与正确答案，仅供 {@code ImageCaptchaServiceImpl} 内部读写，不对外暴露。
 *
 * @param type   验证码类型，决定 {@link cn.nihility.rbac.captcha.service.ImageCaptchaService
 *               #verify} 时的比较规则
 * @param answer 正确答案
 */
public record CaptchaAnswerPayload(CaptchaType type, String answer) {
}
