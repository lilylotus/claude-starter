package cn.nihility.rbac.sso.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.sso.session.SsoSessionIdHasher;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogEventType;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SsoLogoutController} 的测试（add-sso-single-logout change tasks.md 8.2；
 * add-sso-protocol-access-log change tasks.md 6.2 扩展 SSO 协议调用记录断言），起真实
 * MySQL/Redis 连接，覆盖 spec.md"全局单点登出接口"全部场景：CAS/OAuth2.0 协议应用的 service
 * 匹配放行、appId 不存在或协议类型为"无"时被拒绝、service 不匹配该应用规则时被拒绝、未持有
 * 会话时仍正常 302，并断言每种场景产生的 {@code tab_sso_protocol_log} 记录字段正确
 * （成功登出记录的协议类型取该应用实际配置的协议类型，而不是固定为 CAS）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SsoLogoutControllerTest {

    /** 测试用 service/redirect_uri 白名单规则对应的合法回跳地址前缀。 */
    private static final String ALLOWED_SERVICE = "https://partner.example.com/callback";

    /** MockMvc 客户端。 */
    @Autowired
    private MockMvc mockMvc;

    /** 应用数据访问接口。 */
    @Autowired
    private AppMapper appMapper;

    /** 应用对外接口凭证配置数据访问接口。 */
    @Autowired
    private AppConfigMapper appConfigMapper;

    /** 应用单点登录协议配置数据访问接口。 */
    @Autowired
    private AppAuthConfigMapper appAuthConfigMapper;

    /** SSO 浏览器会话业务逻辑接口，测试内直接签发会话，模拟"已登录"状态。 */
    @Autowired
    private SsoSessionService ssoSessionService;

    /** SSO 协议调用记录数据访问接口（add-sso-protocol-access-log change tasks.md 6.2）。 */
    @Autowired
    private SsoProtocolLogMapper ssoProtocolLogMapper;

    /** 本用例插入的应用 id，供 {@link #tearDown()} 清理。 */
    private Long appRefId;

    /**
     * 本用例请求登出接口时使用的应用对外标识，供 {@link #tearDown()} 清理与
     * {@link #latestSsoProtocolLog}；{@code appId} 不存在场景下手动赋值为请求携带的原始值，
     * 供清理该场景产生的调用记录。
     */
    private String appId;

    /**
     * 每个用例结束后清理本用例插入的测试数据。
     */
    @AfterEach
    void tearDown() {
        if (appId != null) {
            ssoProtocolLogMapper.delete(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                    .eq(SsoProtocolLogEntity::getAppId, appId));
        }
        if (appRefId != null) {
            appAuthConfigMapper.delete(new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appRefId));
            appConfigMapper.delete(new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
            appMapper.deleteById(appRefId);
        }
    }

    /**
     * 插入一份测试应用数据：{@code tab_app} + {@code tab_app_config} + {@code tab_app_auth_config}，
     * 并把生成的应用对外标识记录到 {@link #appId}。
     *
     * @param authProtocol    协议类型
     * @param servicePatterns 回跳地址 ANT 匹配规则列表，CAS/OAuth2.0 等协议共用
     */
    private void seed(String authProtocol, List<String> servicePatterns) {
        LocalDateTime now = LocalDateTime.now();
        AppEntity app = AppEntity.builder()
                .name("全局登出测试应用")
                .code("sso-logout-test-" + UUID.randomUUID())
                .ownerId(1L)
                .orgId(1L)
                .showOrder(0)
                .status(AppStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appMapper.insert(app);
        appRefId = app.getId();
        appId = "sso-logout-test-app-" + UUID.randomUUID().toString().replace("-", "");

        AppConfigEntity appConfig = AppConfigEntity.builder()
                .appRefId(appRefId)
                .appId(appId)
                .accessKey(UUID.randomUUID().toString().replace("-", ""))
                .secretKey("dummy-secret-key-ciphertext")
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .syncMode(SyncMode.PULL)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appConfigMapper.insert(appConfig);

        AppAuthConfigEntity authConfig = AppAuthConfigEntity.builder()
                .appRefId(appRefId)
                .authProtocol(authProtocol)
                .servicePatterns(JacksonUtils.toJson(servicePatterns))
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appAuthConfigMapper.insert(authConfig);
    }

    /**
     * CAS 协议应用的 service 匹配已配置规则时，应撤销会话并 302 重定向到 service，记录一条
     * 协议类型为 CAS 的成功调用记录（spec.md "全局登出接口清除会话并跳回 service" Scenario）。
     */
    @Test
    void logout_shouldClearSessionAndRedirect_whenCasServiceMatches() throws Exception {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));
        String sessionToken = ssoSessionService.issue(1L);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));

        assertThat(ssoSessionService.verify(sessionToken)).isEmpty();

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog();
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(logEntity.getProtocol()).isEqualTo(AuthProtocol.CAS);
        assertThat(logEntity.getUserId()).isEqualTo(1L);
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(sessionToken));
    }

    /**
     * OAuth2.0 协议应用的 service（语义等同 redirect_uri）匹配已配置规则时应正常登出，记录一条
     * 协议类型为 OAUTH2（而不是固定写死 CAS）的成功调用记录（spec.md "OAuth2.0 协议应用的全局
     * 登出" Scenario）。
     */
    @Test
    void logout_shouldClearSessionAndRedirect_whenOAuthRedirectUriMatches() throws Exception {
        seed(AuthProtocol.OAUTH2, List.of("https://partner.example.com/**"));
        String sessionToken = ssoSessionService.issue(1L);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));

        assertThat(ssoSessionService.verify(sessionToken)).isEmpty();

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog();
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(logEntity.getProtocol()).isEqualTo(AuthProtocol.OAUTH2);
        assertThat(logEntity.getUserId()).isEqualTo(1L);
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(sessionToken));
    }

    /**
     * {@code appId} 不存在时应拒绝，不发生重定向，记录一条协议类型兜底为 NONE 的失败调用记录
     * （spec.md "appId 不存在或协议类型为无时被拒绝" Scenario）。
     */
    @Test
    void logout_shouldReject_whenAppIdNotFound() throws Exception {
        appId = "not-exist-app-id";
        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog();
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getProtocol()).isEqualTo(AuthProtocol.NONE);
        assertThat(logEntity.getAppRefId()).isNull();
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 应用协议类型为"无"时应拒绝，不发生重定向，记录一条协议类型为 NONE 的失败调用记录。
     */
    @Test
    void logout_shouldReject_whenProtocolNone() throws Exception {
        seed(AuthProtocol.NONE, List.of());

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog();
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getProtocol()).isEqualTo(AuthProtocol.NONE);
        assertThat(logEntity.getAppRefId()).isEqualTo(appRefId);
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * service 不匹配该应用配置的规则时应拒绝，不发生重定向、不清除会话，记录一条协议类型为
     * CAS（该应用实际配置的协议）的失败调用记录（spec.md "service 未匹配该应用规则时被拒绝"
     * Scenario）。
     */
    @Test
    void logout_shouldReject_whenServiceNotMatch() throws Exception {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));
        String sessionToken = ssoSessionService.issue(1L);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/{appId}/logout", appId)
                        .param("service", "https://evil.example.com/callback").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));

        assertThat(ssoSessionService.verify(sessionToken)).isPresent();

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog();
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getProtocol()).isEqualTo(AuthProtocol.CAS);
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 未持有有效会话时访问全局登出接口，只要 {@code appId}/{@code service} 校验通过，仍应
     * 正常 302 重定向，不报错，仍记录一条成功的调用记录、用户 id 为空（spec.md "未持有有效
     * 会话时仍正常响应" Scenario）。
     */
    @Test
    void logout_shouldRedirect_evenWithoutSession() throws Exception {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog();
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 查询本用例请求登出接口时使用的应用（{@link #appId}）最近一条 {@code LOGOUT} 事件类型的
     * SSO 协议调用记录，断言必须存在，否则用例失败。
     *
     * @return 最近一条登出事件的调用记录
     */
    private SsoProtocolLogEntity latestSsoProtocolLog() {
        List<SsoProtocolLogEntity> logs = ssoProtocolLogMapper.selectList(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                .eq(SsoProtocolLogEntity::getAppId, appId)
                .eq(SsoProtocolLogEntity::getEventType, SsoProtocolLogEventType.LOGOUT)
                .orderByDesc(SsoProtocolLogEntity::getId));
        assertThat(logs).isNotEmpty();
        return logs.get(0);
    }
}
