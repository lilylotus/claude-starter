package cn.nihility.rbac.sync.notify.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.common.config.HttpClientProperties;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sync.notify.dto.NotifyAttemptOutcome;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.sign.NotifySignatureAppender;
import cn.nihility.rbac.sync.sign.SignAlgorithmCodecImpl;
import cn.nihility.rbac.sync.sign.SignConstants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link AppNotifyServiceImpl} 的单元测试：本地起一个内嵌 {@link HttpServer} 充当外部应用
 * 的通知回调地址，验证 {@code sendOnce} 只负责发起一次实际 HTTP 请求并把结果归类为
 * 成功/可重试失败/不可重试失败，不做任何落库（app-sync-changelog-pull change design.md
 * Decision 6：状态机落库/流转已迁移到 {@code AppNotifyTaskService}/
 * {@code NotifySendCoordinator}）。
 */
@ExtendWith(MockitoExtension.class)
class AppNotifyServiceImplTest {

    private static HttpServer server;
    private static String successUrl;
    private static String failureUrl;
    private static String rateLimitedUrl;
    private static volatile String lastReceivedBody;
    private static volatile String lastReceivedAppKeyHeader;

    @Mock
    private AppConfigMapper appConfigMapper;

    private AppNotifyServiceImpl service;

    /** 与被测服务共用同一份 SM4 密钥配置，供测试构造已加密的 {@code secretKey}。 */
    private AppSecretProperties appSecretProperties;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/notify-ok", AppNotifyServiceImplTest::handleOk);
        server.createContext("/notify-fail", AppNotifyServiceImplTest::handleFail);
        server.createContext("/notify-rate-limited", AppNotifyServiceImplTest::handleRateLimited);
        server.setExecutor(null);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        successUrl = base + "/notify-ok";
        failureUrl = base + "/notify-fail";
        rateLimitedUrl = base + "/notify-rate-limited";
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        HttpClientProperties httpClientProperties = new HttpClientProperties();
        HttpClientUtils.configure(httpClientProperties);

        appSecretProperties = new AppSecretProperties();
        appSecretProperties.setSm4Key(Sm4JdkUtils.generateKey());
        NotifySignatureAppender notifySignatureAppender = new NotifySignatureAppender(new SignAlgorithmCodecImpl());

        service = new AppNotifyServiceImpl(appConfigMapper, notifySignatureAppender, appSecretProperties);

        lastReceivedBody = null;
        lastReceivedAppKeyHeader = null;
    }

    @AfterEach
    void tearDown() {
        HttpClientUtils.configure(new HttpClientProperties());
    }

    /**
     * 收到 2xx 响应时应返回成功结果，且请求体/签名头正确透传任务快照。
     */
    @Test
    void sendOnce_shouldReturnSuccess_whenCallbackReturns2xx() {
        AppConfigEntity target = sampleTarget(1L, "open-app-1", "access-key-1", successUrl);
        when(appConfigMapper.selectOne(any())).thenReturn(target);

        AppNotifyRecordEntity task = sampleTask(1L, successUrl, "{\"eventId\":\"1\"}");

        NotifyAttemptOutcome outcome = service.sendOnce(task);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(lastReceivedAppKeyHeader).isEqualTo("access-key-1");
        assertThat(lastReceivedBody).isEqualTo("{\"eventId\":\"1\"}");
    }

    /**
     * 收到 500 响应时应归类为可重试失败。
     */
    @Test
    void sendOnce_shouldReturnRetryable_whenCallbackReturns5xx() {
        AppConfigEntity target = sampleTarget(2L, "open-app-2", "access-key-2", failureUrl);
        when(appConfigMapper.selectOne(any())).thenReturn(target);

        NotifyAttemptOutcome outcome = service.sendOnce(sampleTask(2L, failureUrl, "{}"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(500);
    }

    /**
     * 收到 429 响应时应归类为可重试失败。
     */
    @Test
    void sendOnce_shouldReturnRetryable_whenCallbackReturns429() {
        AppConfigEntity target = sampleTarget(3L, "open-app-3", "access-key-3", rateLimitedUrl);
        when(appConfigMapper.selectOne(any())).thenReturn(target);

        NotifyAttemptOutcome outcome = service.sendOnce(sampleTask(3L, rateLimitedUrl, "{}"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(429);
    }

    /**
     * 网络异常（不可达地址）时应归类为可重试失败，{@code httpStatus} 为空。
     */
    @Test
    void sendOnce_shouldReturnRetryable_whenNetworkError() {
        String unreachableUrl = "http://127.0.0.1:1/notify-unreachable";
        AppConfigEntity target = sampleTarget(4L, "open-app-4", "access-key-4", unreachableUrl);
        when(appConfigMapper.selectOne(any())).thenReturn(target);

        NotifyAttemptOutcome outcome = service.sendOnce(sampleTask(4L, unreachableUrl, "{}"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.errorMsg()).isNotBlank();
    }

    /**
     * 目标应用配置查不到时，应归类为不可重试失败（死信）。
     */
    @Test
    void sendOnce_shouldReturnDead_whenTargetNotFound() {
        when(appConfigMapper.selectOne(any())).thenReturn(null);

        NotifyAttemptOutcome outcome = service.sendOnce(sampleTask(5L, successUrl, "{}"));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isFalse();
    }

    private AppConfigEntity sampleTarget(Long appRefId, String appId, String accessKey, String notifyUrl) {
        return AppConfigEntity.builder()
                .appRefId(appRefId)
                .appId(appId)
                .accessKey(accessKey)
                .secretKey(Sm4JdkUtils.encrypt("secret", appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .notifyUrl(notifyUrl)
                .build();
    }

    private AppNotifyRecordEntity sampleTask(Long appRefId, String notifyUrl, String requestBody) {
        return AppNotifyRecordEntity.builder()
                .appRefId(appRefId)
                .notifyUrl(notifyUrl)
                .requestBody(requestBody)
                .retryCount(0)
                .build();
    }

    private static void handleOk(HttpExchange exchange) throws IOException {
        lastReceivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastReceivedAppKeyHeader = exchange.getRequestHeaders().getFirst(SignConstants.HEADER_APP_KEY);
        byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private static void handleFail(HttpExchange exchange) throws IOException {
        byte[] response = "error".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(500, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private static void handleRateLimited(HttpExchange exchange) throws IOException {
        byte[] response = "rate limited".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(429, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }
}
