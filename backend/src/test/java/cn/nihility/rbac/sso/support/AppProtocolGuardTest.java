package cn.nihility.rbac.sso.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link AppProtocolGuard} 的测试，起真实 MySQL 连接（同项目既有"不做重量级 mock"的测试
 * 风格），覆盖 service/redirect_uri 匹配通过/不匹配拒绝、协议类型不符拒绝、应用不存在拒绝
 * （tasks.md 7.4）。每个用例前插入一份独立的测试应用数据，结束后清理，不污染真实数据。
 */
@SpringBootTest
class AppProtocolGuardTest {

    /** 被测组件，真实注入（依赖真实数据库连接）。 */
    @Autowired
    private AppProtocolGuard appProtocolGuard;

    /** 应用数据访问接口。 */
    @Autowired
    private AppMapper appMapper;

    /** 应用对外接口凭证配置数据访问接口。 */
    @Autowired
    private AppConfigMapper appConfigMapper;

    /** 应用单点登录协议配置数据访问接口。 */
    @Autowired
    private AppAuthConfigMapper appAuthConfigMapper;

    /** 本用例插入的应用 id，测试结束后据此清理三张表的记录。 */
    private Long appRefId;

    /** 本用例插入的应用对外标识（open_app_id）。 */
    private String appId;

    /**
     * 每个用例结束后清理本用例插入的测试数据。
     */
    @AfterEach
    void tearDown() {
        if (appRefId != null) {
            appAuthConfigMapper.delete(new LambdaQueryWrapper<AppAuthConfigEntity>()
                    .eq(AppAuthConfigEntity::getAppRefId, appRefId));
            appConfigMapper.delete(new LambdaQueryWrapper<AppConfigEntity>()
                    .eq(AppConfigEntity::getAppRefId, appRefId));
            appMapper.deleteById(appRefId);
        }
    }

    /**
     * 插入一份测试应用数据：{@code tab_app} + {@code tab_app_config} + {@code tab_app_auth_config}。
     *
     * @param authProtocol     协议类型
     * @param servicePatterns  回跳地址 ANT 匹配规则列表，CAS/OAuth2.0 等协议共用
     */
    private void seed(String authProtocol, List<String> servicePatterns) {
        LocalDateTime now = LocalDateTime.now();
        AppEntity app = AppEntity.builder()
                .name("SSO 测试应用")
                .code("sso-test-" + UUID.randomUUID())
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
        appId = "sso-test-app-" + UUID.randomUUID().toString().replace("-", "");

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
     * service 匹配已配置的规则时应校验通过。
     */
    @Test
    void assertCasServiceAllowed_shouldPass_whenServiceMatches() {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));

        assertThat(appProtocolGuard.assertCasServiceAllowed(appId, "https://partner.example.com/callback")).isNotNull();
    }

    /**
     * service 不匹配任何已配置规则时应拒绝。
     */
    @Test
    void assertCasServiceAllowed_shouldReject_whenServiceNotMatch() {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));

        assertThatThrownBy(() -> appProtocolGuard.assertCasServiceAllowed(appId, "https://evil.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * 应用协议类型不是 CAS 时应拒绝，即便传入的 service 本身格式合法。
     */
    @Test
    void assertCasServiceAllowed_shouldReject_whenProtocolMismatch() {
        seed(AuthProtocol.OAUTH2, List.of("https://partner.example.com/**"));

        assertThatThrownBy(() -> appProtocolGuard.assertCasServiceAllowed(appId, "https://partner.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * 应用不存在时应拒绝。
     */
    @Test
    void assertCasServiceAllowed_shouldReject_whenAppNotFound() {
        assertThatThrownBy(() -> appProtocolGuard.assertCasServiceAllowed("not-exist-app-id", "https://partner.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * redirect_uri 匹配已配置的规则时应校验通过。
     */
    @Test
    void assertOAuthRedirectUriAllowed_shouldPass_whenRedirectUriMatches() {
        seed(AuthProtocol.OAUTH2, List.of("https://partner.example.com/**"));

        assertThat(appProtocolGuard.assertOAuthRedirectUriAllowed(appId, "https://partner.example.com/callback")).isNotNull();
    }

    /**
     * redirect_uri 不匹配任何已配置规则时应拒绝。
     */
    @Test
    void assertOAuthRedirectUriAllowed_shouldReject_whenRedirectUriNotMatch() {
        seed(AuthProtocol.OAUTH2, List.of("https://partner.example.com/**"));

        assertThatThrownBy(() -> appProtocolGuard.assertOAuthRedirectUriAllowed(appId, "https://evil.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * redirect_uri 为空时应直接拒绝（design.md Decision 6"收紧为必填"）。
     */
    @Test
    void assertOAuthRedirectUriAllowed_shouldReject_whenRedirectUriBlank() {
        seed(AuthProtocol.OAUTH2, List.of("https://partner.example.com/**"));

        assertThatThrownBy(() -> appProtocolGuard.assertOAuthRedirectUriAllowed(appId, ""))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * 应用协议类型不是 OAuth2.0 时应拒绝。
     */
    @Test
    void assertOAuthRedirectUriAllowed_shouldReject_whenProtocolMismatch() {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));

        assertThatThrownBy(() -> appProtocolGuard.assertOAuthRedirectUriAllowed(appId, "https://partner.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * 应用不存在时应拒绝。
     */
    @Test
    void assertOAuthRedirectUriAllowed_shouldReject_whenAppNotFound() {
        assertThatThrownBy(() -> appProtocolGuard.assertOAuthRedirectUriAllowed("not-exist-client-id", "https://partner.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * CAS 协议应用的 service 匹配已配置规则时，全局登出的校验方法应通过
     * （add-sso-single-logout change spec.md "全局登出接口清除会话并跳回 service" Scenario）。
     */
    @Test
    void assertLogoutServiceAllowed_shouldPass_whenCasServiceMatches() {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));

        assertThat(appProtocolGuard.assertLogoutServiceAllowed(appId, "https://partner.example.com/callback")).isNotNull();
    }

    /**
     * OAuth2.0 协议应用的 service（语义等同 redirect_uri）匹配已配置规则时应通过
     * （add-sso-single-logout change spec.md "OAuth2.0 协议应用的全局登出" Scenario）。
     */
    @Test
    void assertLogoutServiceAllowed_shouldPass_whenOAuthRedirectUriMatches() {
        seed(AuthProtocol.OAUTH2, List.of("https://partner.example.com/**"));

        assertThat(appProtocolGuard.assertLogoutServiceAllowed(appId, "https://partner.example.com/callback")).isNotNull();
    }

    /**
     * 协议类型为 NONE 时应拒绝（add-sso-single-logout change spec.md "appId 不存在或协议类型
     * 为无时被拒绝" Scenario）。
     */
    @Test
    void assertLogoutServiceAllowed_shouldReject_whenProtocolNone() {
        seed(AuthProtocol.NONE, List.of());

        assertThatThrownBy(() -> appProtocolGuard.assertLogoutServiceAllowed(appId, "https://partner.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * service 不匹配该应用（按其协议类型）配置的规则时应拒绝（add-sso-single-logout change
     * spec.md "service 未匹配该应用规则时被拒绝" Scenario）。
     */
    @Test
    void assertLogoutServiceAllowed_shouldReject_whenServiceNotMatch() {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));

        assertThatThrownBy(() -> appProtocolGuard.assertLogoutServiceAllowed(appId, "https://evil.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * 应用不存在时应拒绝。
     */
    @Test
    void assertLogoutServiceAllowed_shouldReject_whenAppNotFound() {
        assertThatThrownBy(() -> appProtocolGuard.assertLogoutServiceAllowed("not-exist-app-id", "https://partner.example.com/callback"))
                .isInstanceOf(SsoProtocolException.class);
    }

    /**
     * 已启用单点登录协议的应用查询应返回该应用，携带登出通知回调地址与签名参数；协议类型为
     * NONE 的应用不应出现在结果中（add-sso-single-logout change tasks.md 3.1）。
     */
    @Test
    void listActiveProtocolApps_shouldReturnEnabledApps_excludingNone() {
        seed(AuthProtocol.CAS, List.of("https://partner.example.com/**"));
        String casAppId = appId;
        AppAuthConfigEntity authConfig = appAuthConfigMapper.selectOne(
                new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appRefId));
        authConfig.setLogoutNotifyUrl("https://partner.example.com/logout-notify");
        appAuthConfigMapper.updateById(authConfig);

        assertThat(appProtocolGuard.listActiveProtocolApps())
                .anySatisfy(info -> {
                    if (casAppId.equals(info.getAppId())) {
                        assertThat(info.getAuthProtocol()).isEqualTo(AuthProtocol.CAS);
                        assertThat(info.getLogoutNotifyUrl()).isEqualTo("https://partner.example.com/logout-notify");
                        assertThat(info.getAccessKey()).isNotBlank();
                    }
                });
    }
}
