package cn.nihility.rbac.common.exception;

import cn.nihility.rbac.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，把业务异常和参数校验异常统一转换为 {@link Result} 结构返回，
 * 供所有业务模块复用，不与具体业务模块耦合。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数校验失败时的默认状态码。 */
    private static final int VALIDATION_ERROR_CODE = 400;

    /** 未预期的系统异常状态码。 */
    private static final int SYSTEM_ERROR_CODE = 500;

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 携带业务状态码和提示信息的响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理请求体 {@code @Valid} 校验失败的异常，取第一条字段错误信息返回。
     *
     * @param ex 参数校验异常
     * @return 携带校验失败提示的响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(VALIDATION_ERROR_CODE, message);
    }

    /**
     * 兜底处理未预期的系统异常，避免异常堆栈直接返回给前端。
     *
     * @param ex 未预期的异常
     * @return 携带通用错误提示的响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.error(SYSTEM_ERROR_CODE, "服务器内部错误");
    }
}
