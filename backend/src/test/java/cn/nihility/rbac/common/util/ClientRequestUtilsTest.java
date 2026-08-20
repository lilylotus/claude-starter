package cn.nihility.rbac.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link ClientRequestUtils} 的测试（app-access-request-control change tasks.md 8.2），
 * 覆盖 {@code X-Forwarded-For} 请求头存在（取第一个值）/不存在（回退
 * {@code getRemoteAddr()}）两种取值路径，以及请求为空时的兜底行为；不起 Spring 容器
 * （纯 JUnit + {@link MockHttpServletRequest}）。逻辑从
 * {@code cn.nihility.rbac.loginlog.service.impl.LoginLogRecorderImpl} 原有私有实现原样
 * 提炼而来，本类是该逻辑唯一的直接单元测试（该模块当前没有独立的
 * {@code LoginLogRecorderImplTest}）。
 */
class ClientRequestUtilsTest {

    /**
     * 请求头 {@code X-Forwarded-For} 存在且只含一个 IP 时，应原样返回该 IP。
     */
    @Test
    void resolveClientIp_shouldReturnFirstForwardedIp_whenHeaderHasSingleValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(ClientRequestUtils.resolveClientIp(request)).isEqualTo("203.0.113.10");
    }

    /**
     * 请求头 {@code X-Forwarded-For} 存在且含多个以逗号分隔的 IP（经过多层代理）时，应取
     * 第一个值并去除首尾空白。
     */
    @Test
    void resolveClientIp_shouldReturnFirstForwardedIp_whenHeaderHasMultipleValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.20, 192.0.2.30");

        assertThat(ClientRequestUtils.resolveClientIp(request)).isEqualTo("203.0.113.10");
    }

    /**
     * 请求头 {@code X-Forwarded-For} 不存在时，应回退取 {@code request.getRemoteAddr()}。
     */
    @Test
    void resolveClientIp_shouldFallbackToRemoteAddr_whenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        assertThat(ClientRequestUtils.resolveClientIp(request)).isEqualTo("192.168.1.100");
    }

    /**
     * 请求头 {@code X-Forwarded-For} 为空白字符串时视为不存在，应回退取
     * {@code request.getRemoteAddr()}。
     */
    @Test
    void resolveClientIp_shouldFallbackToRemoteAddr_whenHeaderBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.168.1.100");

        assertThat(ClientRequestUtils.resolveClientIp(request)).isEqualTo("192.168.1.100");
    }

    /**
     * 请求为 {@code null}（非 HTTP 上下文，如单元测试/后台任务）时应返回 {@code null}，
     * 不抛出异常。
     */
    @Test
    void resolveClientIp_shouldReturnNull_whenRequestIsNull() {
        assertThat(ClientRequestUtils.resolveClientIp(null)).isNull();
    }
}
