package cn.nihility.rbac.ratelimit.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 接口调用频率超限异常（add-captcha-rate-limit change tasks.md 3.3）：继承
 * {@link BusinessException}，交由已有的 {@code GlobalExceptionHandler} 统一转换为
 * {@code {code,message,data}} 响应，不需要新增异常处理器分支（design.md Decision 9）。
 */
public class RateLimitExceededException extends BusinessException {

    /** 限流业务状态码。 */
    public static final int RATE_LIMIT_EXCEEDED_CODE = 42900;

    /**
     * 使用默认提示信息构造限流异常，不暴露精确重试时间，避免帮攻击者调参
     * （design.md Decision 9）。
     */
    public RateLimitExceededException() {
        super(RATE_LIMIT_EXCEEDED_CODE, "请求过于频繁，请稍后再试");
    }
}
