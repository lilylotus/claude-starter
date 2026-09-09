package cn.nihility.rbac.captcha.service;

import cn.nihility.rbac.captcha.dto.CaptchaImageVO;

/**
 * 图形验证码生成与一次性校验业务逻辑接口（add-captcha-rate-limit change proposal.md）。
 * 校验能力以 Java 方法形式暴露供其他后端模块直接调用，不要求必须暴露为独立 HTTP 接口。
 */
public interface ImageCaptchaService {

    /**
     * 按当前配置随机生成一个图形验证码：生成展示内容与正确答案、渲染图片、分配唯一 id，
     * 并把正确答案连同过期时间写入 Redis。
     *
     * @return 验证码 id 与图片
     */
    CaptchaImageVO generate();

    /**
     * 按验证码 id 校验用户提交的答案，无论结果如何都会立即使该 id 失效（一次性）。
     *
     * @param captchaId 验证码 id
     * @param answer    用户提交的答案
     * @return 校验是否通过：验证码 id 不存在（未生成、已过期、已被消费）时返回 {@code false}
     */
    boolean verify(String captchaId, String answer);
}
