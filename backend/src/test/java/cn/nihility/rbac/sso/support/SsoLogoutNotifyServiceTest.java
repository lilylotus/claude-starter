package cn.nihility.rbac.sso.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.dto.AppProtocolInfo;
import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.common.config.HttpClientProperties;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sso.session.SsoSessionAppCredential;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.sync.sign.NotifySignatureAppender;
import cn.nihility.rbac.sync.sign.SignAlgorithmCodecImpl;
import cn.nihility.rbac.sync.sign.SignConstants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SsoLogoutNotifyService} 的单元测试（add-sso-single-logout change tasks.md 8.3），
 * 本地起一个内嵌 {@link HttpServer} 充当各应用的登出通知回调地址，覆盖 spec.md"单点登出后端
 * 回调通知"全部场景：CAS 应用收到最后一次 ticket、OAuth2 应用收到最后一次 access token、
 * 未登录过的应用不收到通知、未配置回调地址的应用被跳过。
 */
@ExtendWith(MockitoExtension.class)
class SsoLogoutNotifyServiceTest {

    /** 单个异步通知任务的最长等待时间（毫秒），供轮询断言使用。 */
    private static final long AWAIT_TIMEOUT_MILLIS = 3000L;

    private static HttpServer server;
    private static String casNotifyUrl;
    private static String oauthNotifyUrl;
    private static String unvisitedNotifyUrl;
    private static volatile String lastCasBody;
    private static volatile String lastCasAppKeyHeader;
    private static volatile String lastOauthBody;
    private static volatile boolean unvisitedCalled;

    @Mock
    private SsoSessionService ssoSessionService;

    @Mock
    private AppProtocolGuard appProtocolGuard;

    private SsoLogoutNotifyService service;

    private AppSecretProperties appSecretProperties;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cas-logout-notify", SsoLogoutNotifyServiceTest::handleCas);
        server.createContext("/oauth-logout-notify", SsoLogoutNotifyServiceTest::handleOauth);
        server.createContext("/unvisited-logout-notify", SsoLogoutNotifyServiceTest::handleUnvisited);
        server.setExecutor(null);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        casNotifyUrl = base + "/cas-logout-notify";
        oauthNotifyUrl = base + "/oauth-logout-notify";
        unvisitedNotifyUrl = base + "/unvisited-logout-notify";
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        HttpClientUtils.configure(new HttpClientProperties());
        appSecretProperties = new AppSecretProperties();
        appSecretProperties.setSm4Key(Sm4JdkUtils.generateKey());
        NotifySignatureAppender notifySignatureAppender = new NotifySignatureAppender(new SignAlgorithmCodecImpl());

        service = new SsoLogoutNotifyService(ssoSessionService, appProtocolGuard, notifySignatureAppender,
                appSecretProperties);

        lastCasBody = null;
        lastCasAppKeyHeader = null;
        lastOauthBody = null;
        unvisitedCalled = false;
    }

    @AfterEach
    void tearDown() {
        HttpClientUtils.configure(new HttpClientProperties());
    }

    /**
     * 会话登录过的多个应用均应收到登出通知：CAS 应用收到表单字段 {@code ticket}（最后一次
     * 签发的服务票据），OAuth2 应用收到表单字段 {@code access_token}（最后一次签发的
     * access token），且均携带签名请求头；未配置回调地址的应用被跳过、未登录过的应用
     * （虽已配置回调地址但本次会话未访问过）不收到通知（spec.md 多个 Scenario 合并覆盖）。
     */
    @Test
    void notifyLogout_shouldNotifyOnlyVisitedAppsWithConfiguredUrl() throws InterruptedException {
        Map<String, SsoSessionAppCredential> credentials = new LinkedHashMap<>();
        credentials.put("app-cas", new SsoSessionAppCredential(AuthProtocol.CAS, "ST-last-ticket"));
        credentials.put("app-oauth", new SsoSessionAppCredential(AuthProtocol.OAUTH2, "last-access-token"));
        credentials.put("app-no-url", new SsoSessionAppCredential(AuthProtocol.CAS, "ST-should-not-be-sent"));
        when(ssoSessionService.listAppCredentials("session-1")).thenReturn(credentials);

        List<AppProtocolInfo> activeApps = List.of(
                buildAppInfo("app-cas", AuthProtocol.CAS, casNotifyUrl, "ak-cas", "secret-cas"),
                buildAppInfo("app-oauth", AuthProtocol.OAUTH2, oauthNotifyUrl, "ak-oauth", "secret-oauth"),
                buildAppInfo("app-no-url", AuthProtocol.CAS, null, "ak-no-url", "secret-no-url"),
                buildAppInfo("app-unvisited", AuthProtocol.CAS, unvisitedNotifyUrl, "ak-unvisited", "secret-unvisited"));
        when(appProtocolGuard.listActiveProtocolApps()).thenReturn(activeApps);

        service.notifyLogout("session-1");

        awaitUntil(() -> lastCasBody != null && lastOauthBody != null);

        assertThat(lastCasBody).isEqualTo("ticket=ST-last-ticket");
        assertThat(lastCasAppKeyHeader).isEqualTo("ak-cas");
        assertThat(lastOauthBody).isEqualTo("access_token=last-access-token");

        // 给未预期的调用留出与正常通知同等的等待窗口，确保不是"还没来得及调用"
        Thread.sleep(300);
        assertThat(unvisitedCalled).isFalse();

        verify(ssoSessionService).clearAppCredentials("session-1");
    }

    /**
     * 本次会话未登录过任何应用（映射为空）时，应直接返回，不做任何事，且仍然清理会话-应用
     * 凭证映射（幂等）。
     */
    @Test
    void notifyLogout_shouldDoNothing_whenNoCredentials() {
        when(ssoSessionService.listAppCredentials("session-empty")).thenReturn(Map.of());

        service.notifyLogout("session-empty");

        verify(ssoSessionService).clearAppCredentials("session-empty");
    }

    /**
     * 会话令牌为空时应直接返回，不触发任何读取/清理动作。
     */
    @Test
    void notifyLogout_shouldDoNothing_whenTokenBlank() {
        service.notifyLogout("");

        org.mockito.Mockito.verifyNoInteractions(ssoSessionService, appProtocolGuard);
    }

    /**
     * 构造一个测试用的应用协议信息。
     *
     * @param appId           应用对外标识
     * @param authProtocol    协议类型
     * @param logoutNotifyUrl 登出通知回调地址，可能为 {@code null}
     * @param accessKey       对外接口 AccessKey
     * @param secretKeyPlain  对外接口 SecretKey 明文（内部按测试用 SM4 密钥加密后落入 DTO）
     * @return 应用协议信息
     */
    private AppProtocolInfo buildAppInfo(String appId, String authProtocol, String logoutNotifyUrl, String accessKey,
            String secretKeyPlain) {
        return AppProtocolInfo.builder()
                .appId(appId)
                .authProtocol(authProtocol)
                .logoutNotifyUrl(logoutNotifyUrl)
                .accessKey(accessKey)
                .secretKey(Sm4JdkUtils.encrypt(secretKeyPlain, appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .build();
    }

    /**
     * 轮询等待条件成立，超时仍未成立时任由后续断言失败（不在本方法内断言，保留失败堆栈的
     * 可读性）。
     *
     * @param condition 待等待的条件
     * @throws InterruptedException 等待被中断
     */
    private void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    private static void handleCas(HttpExchange exchange) throws IOException {
        lastCasBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastCasAppKeyHeader = exchange.getRequestHeaders().getFirst(SignConstants.HEADER_APP_KEY);
        respondOk(exchange);
    }

    private static void handleOauth(HttpExchange exchange) throws IOException {
        lastOauthBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        respondOk(exchange);
    }

    private static void handleUnvisited(HttpExchange exchange) throws IOException {
        unvisitedCalled = true;
        respondOk(exchange);
    }

    private static void respondOk(HttpExchange exchange) throws IOException {
        byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }
}
