package cn.nihility.rbac.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link HttpClientUtils} 的单元测试：本地起一个内嵌 {@link HttpServer}（JDK 自带，无需
 * 额外依赖）验证 GET/POST JSON/form/multipart/binary、响应超时覆盖全局默认值
 * （backend-common-utilities change design.md Decision 11 / tasks.md 4.5）。
 *
 * <p>跳过 HTTPS 证书校验（{@code skipSslVerify}）依赖自签名证书测试服务器，搭建成本较高，
 * 本测试类不覆盖，已知限制。
 */
class HttpClientUtilsTest {

    /** 内嵌测试 HTTP 服务器。 */
    private static HttpServer server;

    /** 测试服务器基础 URL。 */
    private static String baseUrl;

    /** 每个用例最近一次收到的请求方法+路径，供断言。 */
    private static volatile String lastRequestInfo;

    /** 每个用例最近一次收到的请求体字节。 */
    private static volatile byte[] lastRequestBody;

    /** 每个用例最近一次收到的请求头。 */
    private static volatile Map<String, String> lastRequestHeaders;

    /**
     * 启动内嵌测试服务器：{@code /echo} 路径原样回显请求方法、请求体、部分请求头；
     * {@code /slow} 路径故意延迟 1 秒响应，用于验证响应超时。
     */
    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", HttpClientUtilsTest::handleEcho);
        server.createContext("/slow", HttpClientUtilsTest::handleSlow);
        server.setExecutor(null);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 测试结束后关闭内嵌测试服务器。
     */
    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 每个用例结束后清空共享状态，避免用例间互相污染。
     */
    @AfterEach
    void resetState() {
        lastRequestInfo = null;
        lastRequestBody = null;
        lastRequestHeaders = null;
    }

    /**
     * GET 请求应正确携带查询参数与请求头，且响应可正常解析。
     */
    @Test
    void get_shouldSendQueryParamsAndHeaders() {
        Map<String, String> headers = Map.of("X-Test", "1");
        Map<String, String> queryParams = Map.of("a", "1", "b", "2");

        HttpClientUtils.HttpResult result = HttpClientUtils.get(baseUrl + "/echo", headers, queryParams, null);

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(lastRequestInfo).startsWith("GET /echo?");
        assertThat(lastRequestInfo).contains("a=1").contains("b=2");
        assertThat(headerValue("X-Test")).isEqualTo("1");
    }

    /**
     * {@code postJson} 应以 {@code application/json} 序列化请求体对象并正确发送。
     */
    @Test
    void postJson_shouldSerializeBodyAsJson() {
        Map<String, Object> body = Map.of("name", "org-a", "code", "ORG001");

        HttpClientUtils.HttpResult result = HttpClientUtils.postJson(baseUrl + "/echo", null, body, null);

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(lastRequestInfo).startsWith("POST /echo");
        assertThat(headerValue("Content-Type")).contains("application/json");
        Map<String, Object> parsed = JacksonUtils.toObj(new String(lastRequestBody, StandardCharsets.UTF_8),
                JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(parsed).containsEntry("name", "org-a").containsEntry("code", "ORG001");
    }

    /**
     * {@code postForm} 应以 {@code application/x-www-form-urlencoded} 格式发送表单字段。
     */
    @Test
    void postForm_shouldSendUrlEncodedFormFields() {
        Map<String, String> formFields = new HashMap<>();
        formFields.put("username", "admin");
        formFields.put("password", "p@ss w0rd");

        HttpClientUtils.HttpResult result = HttpClientUtils.postForm(baseUrl + "/echo", null, formFields, null);

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(headerValue("Content-Type")).contains("application/x-www-form-urlencoded");
        String bodyText = new String(lastRequestBody, StandardCharsets.UTF_8);
        assertThat(bodyText).contains("username=admin");
        assertThat(bodyText).contains("password=p%40ss+w0rd");
    }

    /**
     * {@code postMultipart} 应以 {@code multipart/form-data} 格式携带文本字段与文件字段。
     */
    @Test
    void postMultipart_shouldSendTextAndFileFields() {
        Map<String, String> fields = Map.of("desc", "test file");
        HttpClientUtils.HttpFilePart file = new HttpClientUtils.HttpFilePart("file", "a.txt",
                "file-content".getBytes(StandardCharsets.UTF_8), "text/plain");

        HttpClientUtils.HttpResult result = HttpClientUtils.postMultipart(baseUrl + "/echo", null, fields,
                List.of(file), null);

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(headerValue("Content-Type")).contains("multipart/form-data");
        String bodyText = new String(lastRequestBody, StandardCharsets.UTF_8);
        assertThat(bodyText).contains("desc").contains("test file");
        assertThat(bodyText).contains("a.txt").contains("file-content");
    }

    /**
     * {@code postBinary} 应原样发送二进制内容与自定义 {@code Content-Type}。
     */
    @Test
    void postBinary_shouldSendRawBytesWithContentType() {
        byte[] content = {1, 2, 3, 4, 5};

        HttpClientUtils.HttpResult result = HttpClientUtils.postBinary(baseUrl + "/echo", null, content,
                "application/octet-stream", null);

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(headerValue("Content-Type")).isEqualTo("application/octet-stream");
        assertThat(lastRequestBody).isEqualTo(content);
    }

    /**
     * 大小写不敏感地从 {@link #lastRequestHeaders} 中查找请求头值（{@code HttpServer}
     * 回显的请求头名称大小写与发送方不完全一致，如 {@code Content-Type} 被规范化为
     * {@code Content-type}）。
     *
     * @param name 请求头名称（任意大小写）
     * @return 请求头值，找不到时返回 {@code null}
     */
    private static String headerValue(String name) {
        return lastRequestHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 未指定响应超时时使用全局默认值（5 秒），请求 {@code /slow}（延迟 1 秒响应）应正常成功。
     */
    @Test
    void request_shouldUseGlobalDefaultTimeoutWhenNotSpecified() {
        HttpClientUtils.HttpResult result = HttpClientUtils.get(baseUrl + "/slow", null, null, null);

        assertThat(result.getStatusCode()).isEqualTo(200);
    }

    /**
     * 显式指定的响应超时应生效：对故意延迟 1 秒响应的接口指定 200 毫秒超时，应抛出异常
     * （连接被提前中断），而不是等待 1 秒后成功返回。
     */
    @Test
    void request_shouldRespectExplicitResponseTimeout() {
        long start = System.currentTimeMillis();
        try {
            HttpClientUtils.get(baseUrl + "/slow", null, null, 200L);
            throw new AssertionError("预期请求因响应超时而抛出异常，但实际正常返回");
        } catch (IllegalStateException expected) {
            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed).isLessThan(1000L);
        }
    }

    /**
     * 处理 {@code /echo}：回显请求方法+路径、请求体、部分请求头，供断言读取。
     *
     * @param exchange HTTP 交互上下文
     */
    private static void handleEcho(HttpExchange exchange) throws IOException {
        lastRequestInfo = exchange.getRequestMethod() + " "
                + exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() != null ? "?" + exchange.getRequestURI().getRawQuery() : "");
        lastRequestBody = exchange.getRequestBody().readAllBytes();
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        lastRequestHeaders = headers;

        byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    /**
     * 处理 {@code /slow}：延迟 1 秒后返回，用于验证响应超时行为。
     *
     * @param exchange HTTP 交互上下文
     */
    private static void handleSlow(HttpExchange exchange) throws IOException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] response = "slow-ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }
}
