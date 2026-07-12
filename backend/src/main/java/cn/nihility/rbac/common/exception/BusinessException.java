package cn.nihility.rbac.common.exception;

import lombok.Getter;

/**
 * 通用业务异常，携带一个非 0 的状态码和提示信息，由全局异常处理器统一捕获并转换为
 * {@code { code, message, data } } 结构返回，避免把异常堆栈直接暴露给前端。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 默认业务错误码。 */
    public static final int DEFAULT_CODE = 400;

    /** 状态码。 */
    private final int code;

    /**
     * 使用默认状态码构造业务异常。
     *
     * @param message 提示信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
    }

    /**
     * 使用自定义状态码构造业务异常。
     *
     * @param code    状态码
     * @param message 提示信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
