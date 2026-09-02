package cn.nihility.rbac.sso.sms.service;

/**
 * 短信验证码生命周期管理：发送（含冷却/每日上限限流与防枚举）、校验（含连续失败计数）。
 * 只负责验证码本身的 Redis 状态机，不感知"手机号是否能唯一定位到一个可登录账号之外"的
 * 登录相关逻辑（如会话签发、登录日志记录），由调用方（Controller）编排
 * （add-sso-login-methods change design.md Decision 4）。
 */
public interface SmsCodeService {

    /**
     * 发送一次短信验证码：先做冷却/每日上限限流校验（不通过则抛出 {@code BusinessException}），
     * 通过后无论该手机号是否能唯一定位到一个启用状态用户，均返回成功；只有内部查到"恰为 1 条"
     * 命中记录时才真正生成验证码并调用 {@link cn.nihility.rbac.sso.sms.SmsSender} 发送，
     * 0 条或多条都静默跳过发送，防止调用方从响应差异探测手机号是否已注册
     * （design.md Decision 4"防枚举优先"）。
     *
     * @param mobile 手机号
     */
    void sendCode(String mobile);

    /**
     * 校验提交的验证码是否与该手机号当前有效的验证码一致：一致时清除验证码与失败计数并返回
     * {@code true}；不一致（含验证码不存在/已过期）时失败计数自增并返回 {@code false}，
     * 达到连续失败上限后立即使当前验证码失效（要求重新获取）。
     *
     * @param mobile 手机号
     * @param code   提交的验证码明文
     * @return 是否校验通过
     */
    boolean verifyCode(String mobile, String code);
}
