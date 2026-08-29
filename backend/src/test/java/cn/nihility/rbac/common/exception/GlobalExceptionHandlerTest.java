package cn.nihility.rbac.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.sync.openapi.support.SyncRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * {@link GlobalExceptionHandler} 的单元测试，覆盖业务异常、必填参数缺失、参数类型不匹配、
 * 未匹配路由/静态资源、兜底未预期异常五类场景的响应结构（fix-app-sync-pull-live-data
 * change tasks.md 1.3；fix-no-resource-found-returns-404 change tasks.md 2.1）。
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    /** 被测的全局异常处理器，不依赖任何协作者，直接 new 即可。 */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * 业务异常应原样透传自身携带的状态码和提示信息（既有行为，回归保护）。
     */
    @Test
    void handleBusinessException_shouldReturnExceptionCodeAndMessage() {
        BusinessException ex = new BusinessException(422, "非法的数据类型：DICT");

        Result<Void> result = handler.handleBusinessException(ex);

        assertThat(result.getCode()).isEqualTo(422);
        assertThat(result.getMessage()).isEqualTo("非法的数据类型：DICT");
    }

    /** 限流错误保持 HTTP 200，并在响应头携带建议重试秒数。 */
    @Test
    void handleRateLimitedException_shouldKeepHttp200AndSetRetryAfter() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<Void> result = handler.handleRateLimitedException(
                new SyncRateLimiter.RateLimitedException(2L), response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(result.getCode()).isEqualTo(SyncRateLimiter.RATE_LIMITED_CODE);
        assertThat(result.getMessage()).contains("请求过于频繁");
    }

    /**
     * 缺少必填查询参数时应返回 400，且提示信息明确指出具体参数名。
     */
    @Test
    void handleMissingParam_shouldReturn400WithParameterName() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("fromSequence", "Long");

        Result<Void> result = handler.handleMissingParam(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("缺少必填参数：fromSequence");
    }

    /**
     * 查询参数类型不匹配（如非数字字符串绑定到 {@code Long} 参数）时应返回 400，
     * 且提示信息明确指出具体参数名。
     */
    @Test
    void handleTypeMismatch_shouldReturn400WithParameterName() {
        Exception cause = new IllegalArgumentException("abc");
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Long.class, "fromSequence", null, cause);

        Result<Void> result = handler.handleTypeMismatch(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("参数 fromSequence 格式不正确");
    }

    /**
     * 未预期的系统异常仍应走兜底处理器，返回 500 + 通用提示，不泄露异常堆栈细节。
     */
    @Test
    void handleException_shouldReturn500WithGenericMessage() {
        RuntimeException ex = new RuntimeException("unexpected sql error");

        Result<Void> result = handler.handleException(ex);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("服务器内部错误");
    }

    /**
     * 未匹配路由/静态资源时应返回 404，且提示信息明确指出具体请求路径
     * （fix-no-resource-found-returns-404 change tasks.md 2.1）。
     */
    @Test
    void handleNoResourceFound_shouldReturn404WithResourcePath() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/not-exist-xyz");

        Result<Void> result = handler.handleNoResourceFound(ex);

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).isEqualTo("请求的资源不存在：/not-exist-xyz");
    }
}
