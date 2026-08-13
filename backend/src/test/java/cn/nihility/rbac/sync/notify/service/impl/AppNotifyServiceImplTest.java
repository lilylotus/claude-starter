package cn.nihility.rbac.sync.notify.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.common.config.HttpClientProperties;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.notify.constant.NotifyStatus;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.sign.NotifySignatureAppender;
import cn.nihility.rbac.sync.sign.SignAlgorithmCodecImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppNotifyServiceImpl} 的单元测试：本地起一个内嵌 {@link HttpServer} 充当外部应用
 * 的通知回调地址，验证请求体、成功/失败结果落库，以及 {@code syncMode != NOTIFY} 时直接
 * 跳过、目标应用查不到时直接跳过等分支（app-sync-org-scope-and-app-change-log change
 * design.md Decision 6）。
 */
@ExtendWith(MockitoExtension.class)
class AppNotifyServiceImplTest {

    private static HttpServer server;
    private static String successUrl;
    private static String failureUrl;
    private static volatile String lastReceivedBody;
    private static volatile String lastReceivedAppKeyHeader;

    @Mock
    private AppConfigMapper appConfigMapper;

    @Mock
    private AppNotifyRecordMapper appNotifyRecordMapper;

    private AppNotifyServiceImpl service;

    /** 与被测服务共用同一份 SM4 密钥配置，供测试构造已加密的 {@code secretKey}。 */
    private AppSecretProperties appSecretProperties;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/notify-ok", AppNotifyServiceImplTest::handleOk);
        server.createContext("/notify-fail", AppNotifyServiceImplTest::handleFail);
        server.setExecutor(null);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        successUrl = base + "/notify-ok";
        failureUrl = base + "/notify-fail";
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

        service = new AppNotifyServiceImpl(appConfigMapper, appNotifyRecordMapper, notifySignatureAppender,
                appSecretProperties);

        lastReceivedBody = null;
        lastReceivedAppKeyHeader = null;
    }

    @AfterEach
    void tearDown() {
        HttpClientUtils.configure(new HttpClientProperties());
    }

    /**
     * 通知成功时应携带正确的请求体（sequence/dataType/operationType/bizId）与
     * {@code X-App-Key} 请求头，且落库为成功状态。
     */
    @Test
    void notifyIfConfigured_shouldSendCorrectPayloadAndRecordSuccess() {
        AppConfigEntity target = AppConfigEntity.builder()
                .appRefId(1L)
                .appId("open-app-1")
                .accessKey("access-key-1")
                .secretKey(Sm4JdkUtils.encrypt("secret", appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .syncMode(SyncMode.NOTIFY)
                .needSign(false)
                .notifyUrl(successUrl)
                .notifyParams(null)
                .build();
        when(appConfigMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(target);

        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1024L)
                .appRefId(1L)
                .dataType("ORG")
                .bizId(88L)
                .operationType(OperationType.CREATE)
                .createTime(LocalDateTime.now())
                .build();

        service.notifyIfConfigured(changeLog);

        assertThat(lastReceivedAppKeyHeader).isEqualTo("access-key-1");
        Map<String, Object> payload = JacksonUtils.toObj(lastReceivedBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(payload.get("sequence")).isEqualTo(1024);
        assertThat(payload.get("dataType")).isEqualTo("ORG");
        assertThat(payload.get("operationType")).isEqualTo("CREATE");
        assertThat(payload.get("bizId")).isEqualTo(88);

        ArgumentCaptor<AppNotifyRecordEntity> captor = ArgumentCaptor.forClass(AppNotifyRecordEntity.class);
        verify(appNotifyRecordMapper).insert(captor.capture());
        AppNotifyRecordEntity record = captor.getValue();
        assertThat(record.getChangeLogId()).isEqualTo(1024L);
        assertThat(record.getAppRefId()).isEqualTo(1L);
        assertThat(record.getNotifyStatus()).isEqualTo(NotifyStatus.SUCCESS);
        assertThat(record.getHttpStatus()).isEqualTo(200);
    }

    /**
     * 通知回调返回非成功状态码时应落库为失败状态。
     */
    @Test
    void notifyIfConfigured_shouldRecordFailure_whenCallbackFails() {
        AppConfigEntity target = AppConfigEntity.builder()
                .appRefId(2L)
                .appId("open-app-2")
                .accessKey("access-key-2")
                .secretKey(Sm4JdkUtils.encrypt("secret", appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .syncMode(SyncMode.NOTIFY)
                .needSign(false)
                .notifyUrl(failureUrl)
                .build();
        when(appConfigMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(target);

        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1025L)
                .appRefId(2L)
                .dataType("ORG")
                .bizId(89L)
                .operationType(OperationType.UPDATE)
                .createTime(LocalDateTime.now())
                .build();

        service.notifyIfConfigured(changeLog);

        ArgumentCaptor<AppNotifyRecordEntity> captor = ArgumentCaptor.forClass(AppNotifyRecordEntity.class);
        verify(appNotifyRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getAppRefId()).isEqualTo(2L);
        assertThat(captor.getValue().getNotifyStatus()).isEqualTo(NotifyStatus.FAILURE);
    }

    /**
     * 目标应用当前同步方式不是 {@code NOTIFY} 时，应直接跳过，不发起任何请求、不落库。
     */
    @Test
    void notifyIfConfigured_shouldSkip_whenSyncModeNotNotify() {
        AppConfigEntity target = AppConfigEntity.builder()
                .appRefId(3L)
                .syncMode(SyncMode.PULL)
                .build();
        when(appConfigMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(target);

        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1026L)
                .appRefId(3L)
                .dataType("ORG")
                .bizId(90L)
                .operationType(OperationType.CREATE)
                .createTime(LocalDateTime.now())
                .build();

        service.notifyIfConfigured(changeLog);

        verify(appNotifyRecordMapper, never()).insert(org.mockito.ArgumentMatchers.any(AppNotifyRecordEntity.class));
    }

    /**
     * 目标应用当前配置查不到时，应直接跳过，不发起任何请求、不落库。
     */
    @Test
    void notifyIfConfigured_shouldSkip_whenTargetNotFound() {
        when(appConfigMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1027L)
                .appRefId(4L)
                .dataType("ORG")
                .bizId(91L)
                .operationType(OperationType.CREATE)
                .createTime(LocalDateTime.now())
                .build();

        service.notifyIfConfigured(changeLog);

        verify(appNotifyRecordMapper, never()).insert(org.mockito.ArgumentMatchers.any(AppNotifyRecordEntity.class));
    }

    private static void handleOk(HttpExchange exchange) throws IOException {
        lastReceivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastReceivedAppKeyHeader = exchange.getRequestHeaders().getFirst("X-App-Key");
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
}
