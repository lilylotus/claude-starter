package cn.nihility.rbac.common.exception;

import cn.nihility.rbac.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /** 未匹配路由/静态资源的状态码。 */
    private static final int NOT_FOUND_CODE = 404;

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
     * 处理 {@code @RequestParam} 必填查询参数缺失的异常，返回 400 且在提示信息中明确指出
     * 具体是哪个参数缺失，避免调用方拿到不知所云的"服务器内部错误"（fix-app-sync-pull-live-data
     * change proposal.md 问题 1）。
     *
     * @param ex 缺少必填参数异常
     * @return 携带具体参数名的响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return Result.error(VALIDATION_ERROR_CODE, "缺少必填参数：" + ex.getParameterName());
    }

    /**
     * 处理 {@code @RequestParam} 查询参数类型不匹配的异常（如非数字字符串绑定到
     * {@code Long} 参数），返回 400 且在提示信息中明确指出具体是哪个参数格式不对
     * （fix-app-sync-pull-live-data change proposal.md 问题 1）。
     *
     * @param ex 参数类型不匹配异常
     * @return 携带具体参数名的响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Result.error(VALIDATION_ERROR_CODE, "参数 " + ex.getName() + " 格式不正确");
    }

    /**
     * 处理请求未命中任何 Controller 路由、也不是已注册静态资源的异常，返回真实的
     * HTTP 404 状态码（本处理器是本类里唯一一个改变 HTTP 状态码的方法：其余异常类型都是
     * "接口命中了、但这次调用有问题"，统一用 HTTP 200 + {@code Result.code} 表达；本异常
     * 是"根本没有命中任何接口"，继续返回 HTTP 200 会让只看 HTTP 状态码的调用方（监控探针、
     * 反向代理健康检查、API 调试工具）误判为请求成功，fix-no-resource-found-returns-404
     * change design.md Decision 1）。不当作未预期的系统异常记录 ERROR 日志——大概率是浏览器
     * 自动请求 favicon、爬虫探测路径等常规噪音，而不是系统真的出了问题。
     *
     * @param ex 未匹配路由/静态资源异常
     * @return 携带具体请求路径的 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex) {
        return Result.error(NOT_FOUND_CODE, "请求的资源不存在：" + ex.getResourcePath());
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
