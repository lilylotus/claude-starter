package cn.nihility.rbac.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.common.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * {@link GlobalExceptionHandler} 的单元测试，覆盖业务异常、必填参数缺失、参数类型不匹配、
 * 兜底未预期异常四类场景的响应结构（fix-app-sync-pull-live-data change tasks.md 1.3）。
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
}
