package cn.nihility.rbac.captcha.constant;

/**
 * 图形验证码类型（add-captcha-rate-limit change proposal.md），由 {@code rbac.captcha.type}
 * 配置决定当前启用哪一种，调用方不通过请求参数指定类型。
 */
public enum CaptchaType {

    /**
     * 字符模式：随机生成大小写字母与数字混合的字符串，候选字符集固定剔除易混淆字符
     * （数字 {@code 0}、{@code 1}，字母 {@code O}/{@code o}、{@code I}/{@code i}、{@code l}）。
     */
    CHARACTER,

    /**
     * 算式模式：随机生成一个加、减、乘、除算式，算式的正确计算结果作为答案，除法保证能整除。
     */
    ARITHMETIC
}
