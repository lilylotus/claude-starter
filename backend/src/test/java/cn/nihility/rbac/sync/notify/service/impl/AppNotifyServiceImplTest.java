package cn.nihility.rbac.sync.notify.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.common.config.HttpClientProperties;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.notify.constant.NotifyStatus;
import cn.nihility.rbac.sync.notify.dto.NotifyTargetRow;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.notify.mapper.NotifyTargetMapper;
import cn.nihility.rbac.sync.sign.NotifySignatureAppender;
import cn.nihility.rbac.sync.sign.SignAlgorithmCodecImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
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
 * 的通知回调地址，验证请求体、成功/失败结果落库、以及"一个应用通知失败不影响其他应用"
 * 场景（app-sync-notify-pull spec Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class AppNotifyServiceImplTest {

    private static HttpServer server;
    private static String successUrl;
    private static String failureUrl;
    private static volatile String lastReceivedBody;
    private static volatile String lastReceivedAppKeyHeader;

    @Mock
    private NotifyTargetMapper notifyTargetMapper;

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

        service = new AppNotifyServiceImpl(notifyTargetMapper, appNotifyRecordMapper, notifySignatureAppender,
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
    void notifyMatchedApps_shouldSendCorrectPayloadAndRecordSuccess() {
        NotifyTargetRow target = NotifyTargetRow.builder()
                .appRefId(1L)
                .appId("open-app-1")
                .accessKey("access-key-1")
                .secretKey(Sm4JdkUtils.encrypt("secret", appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .notifyUrl(successUrl)
                .notifyParams(null)
                .build();
        when(notifyTargetMapper.selectNotifyTargets("ORG")).thenReturn(List.of(target));

        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1024L)
                .dataType("ORG")
                .bizId(88L)
                .operationType(OperationType.CREATE)
                .createTime(LocalDateTime.now())
                .build();

        service.notifyMatchedApps(changeLog);

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
     * 一条变更事件同时匹配两个应用的通知条件，其中一个应用的通知因返回失败状态码而失败时，
     * 不应影响向另一个应用发起通知（app-sync-notify-pull spec"一个应用通知失败不影响其他
     * 应用"场景）。
     */
    @Test
    void notifyMatchedApps_shouldContinueOtherAppsWhenOneFails() {
        String sm4Key = appSecretProperties.getSm4Key();
        NotifyTargetRow failingTarget = NotifyTargetRow.builder()
                .appRefId(1L)
                .appId("open-app-1")
                .accessKey("access-key-1")
                .secretKey(Sm4JdkUtils.encrypt("secret", sm4Key))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .notifyUrl(failureUrl)
                .build();
        NotifyTargetRow okTarget = NotifyTargetRow.builder()
                .appRefId(2L)
                .appId("open-app-2")
                .accessKey("access-key-2")
                .secretKey(Sm4JdkUtils.encrypt("secret", sm4Key))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .notifyUrl(successUrl)
                .build();
        when(notifyTargetMapper.selectNotifyTargets("ORG")).thenReturn(List.of(failingTarget, okTarget));

        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1025L)
                .dataType("ORG")
                .bizId(89L)
                .operationType(OperationType.UPDATE)
                .createTime(LocalDateTime.now())
                .build();

        service.notifyMatchedApps(changeLog);

        ArgumentCaptor<AppNotifyRecordEntity> captor = ArgumentCaptor.forClass(AppNotifyRecordEntity.class);
        verify(appNotifyRecordMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<AppNotifyRecordEntity> records = captor.getAllValues();
        assertThat(records).hasSize(2);
        assertThat(records).anySatisfy(r -> {
            assertThat(r.getAppRefId()).isEqualTo(1L);
            assertThat(r.getNotifyStatus()).isEqualTo(NotifyStatus.FAILURE);
        });
        assertThat(records).anySatisfy(r -> {
            assertThat(r.getAppRefId()).isEqualTo(2L);
            assertThat(r.getNotifyStatus()).isEqualTo(NotifyStatus.SUCCESS);
        });
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
