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
import cn.nihility.rbac.sso.session.SsoSessionService;
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
 * {@link SsoLogoutController} 的测试（add-sso-single-logout change tasks.md 8.2），起真实
 * MySQL/Redis 连接，覆盖 spec.md"全局单点登出接口"全部场景：CAS/OAuth2.0 协议应用的 service
 * 匹配放行、appId 不存在或协议类型为"无"时被拒绝、service 不匹配该应用规则时被拒绝、未持有
 * 会话时仍正常 302。
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

    /** 本用例插入的应用 id，供 {@link #tearDown()} 清理。 */
    private Long appRefId;

    /**
     * 每个用例结束后清理本用例插入的测试数据。
     */
    @AfterEach
    void tearDown() {
        if (appRefId != null) {
            appAuthConfigMapper.delete(new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appRefId));
            appConfigMapper.delete(new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
            appMapper.deleteById(appRefId);
        }
    }

    /**
     * 插入一份测试应用数据：{@code tab_app} + {@code tab_app_config} + {@code tab_app_auth_config}。
     *
     * @param authProtocol    协议类型
     * @param servicePatterns 回跳地址 ANT 匹配规则列表，CAS/OAuth2.0 等协议共用
     * @return 插入的应用对外标识（路径中的 {@code appId}）
     */
    private String seed(String authProtocol, List<String> servicePatterns) {
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
        String appId = "sso-logout-test-app-" + UUID.randomUUID().toString().replace("-", "");

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
        return appId;
    }

    /**
     * CAS 协议应用的 service 匹配已配置规则时，应撤销会话并 302 重定向到 service
     * （spec.md "全局登出接口清除会话并跳回 service" Scenario）。
     */
    @Test
    void logout_shouldClearSessionAndRedirect_whenCasServiceMatches() throws Exception {
        String appId = seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));
        String sessionToken = ssoSessionService.issue(1L);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));

        assertThat(ssoSessionService.verify(sessionToken)).isEmpty();
    }

    /**
     * OAuth2.0 协议应用的 service（语义等同 redirect_uri）匹配已配置规则时应正常登出
     * （spec.md "OAuth2.0 协议应用的全局登出" Scenario）。
     */
    @Test
    void logout_shouldClearSessionAndRedirect_whenOAuthRedirectUriMatches() throws Exception {
        String appId = seed(AuthProtocol.OAUTH2, List.of("https://partner.example.com/**"));
        String sessionToken = ssoSessionService.issue(1L);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));

        assertThat(ssoSessionService.verify(sessionToken)).isEmpty();
    }

    /**
     * {@code appId} 不存在时应拒绝，不发生重定向（spec.md "appId 不存在或协议类型为无时被拒绝"
     * Scenario）。
     */
    @Test
    void logout_shouldReject_whenAppIdNotFound() throws Exception {
        mockMvc.perform(get("/api/authn/{appId}/logout", "not-exist-app-id").param("service", ALLOWED_SERVICE))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
    }

    /**
     * 应用协议类型为"无"时应拒绝，不发生重定向。
     */
    @Test
    void logout_shouldReject_whenProtocolNone() throws Exception {
        String appId = seed(AuthProtocol.NONE, List.of());

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
    }

    /**
     * service 不匹配该应用配置的规则时应拒绝，不发生重定向、不清除会话（spec.md "service
     * 未匹配该应用规则时被拒绝" Scenario）。
     */
    @Test
    void logout_shouldReject_whenServiceNotMatch() throws Exception {
        String appId = seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));
        String sessionToken = ssoSessionService.issue(1L);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/{appId}/logout", appId)
                        .param("service", "https://evil.example.com/callback").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));

        assertThat(ssoSessionService.verify(sessionToken)).isPresent();
    }

    /**
     * 未持有有效会话时访问全局登出接口，只要 {@code appId}/{@code service} 校验通过，仍应
     * 正常 302 重定向，不报错（spec.md "未持有有效会话时仍正常响应" Scenario）。
     */
    @Test
    void logout_shouldRedirect_evenWithoutSession() throws Exception {
        String appId = seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));

        mockMvc.perform(get("/api/authn/{appId}/logout", appId).param("service", ALLOWED_SERVICE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));
    }
}
