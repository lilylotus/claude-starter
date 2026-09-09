package cn.nihility.rbac.captcha.support;

/**
 * {@link CaptchaContentGenerator} 单次生成结果：渲染到图片上的展示文本与用于校验的正确答案
 * （字符模式下两者相同；算式模式下展示文本是算式本身，答案是计算结果）。
 *
 * @param displayText 渲染到图片上的文本内容
 * @param answer      用于校验的正确答案
 */
public record CaptchaGenerationResult(String displayText, String answer) {
}
