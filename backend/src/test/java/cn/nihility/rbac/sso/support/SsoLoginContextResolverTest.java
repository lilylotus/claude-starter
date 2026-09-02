package cn.nihility.rbac.sso.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.loginlog.constant.LoginMethod;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SsoLoginContextResolver} 的单元测试（add-sso-login-methods change tasks.md 4.2）：
 * CAS 场景、OAuth2 场景、{@code redirect} 缺失、{@code redirect} 无法解析、应用不存在几种
 * 输入。
 */
@ExtendWith(MockitoExtension.class)
class SsoLoginContextResolverTest {

    @Mock
    private AppProtocolGuard appProtocolGuard;

    private SsoLoginContextResolver resolver;

    /**
     * CAS 场景：从 {@code redirect} 路径中反解出 {@code appId}，查到该应用允许短信 + 扫码。
     */
    @Test
    void resolve_shouldExtractAppIdAndLoginMethods_forCasRedirect() {
        resolver = new SsoLoginContextResolver(appProtocolGuard);
        AppAuthConfigEntity authConfig = authConfig(List.of(LoginMethod.PASSWORD, LoginMethod.SMS));
        when(appProtocolGuard.tryResolveAuthConfig("my-cas-app")).thenReturn(Optional.of(authConfig));

        SsoLoginContext context = resolver.resolve(
                "http://sso.example.com/api/authn/cas/my-cas-app/login?service=https%3A%2F%2Fpartner.example.com%2Fcallback");

        assertThat(context.appId()).isEqualTo("my-cas-app");
        assertThat(context.allowedLoginMethods()).containsExactlyInAnyOrder(LoginMethod.PASSWORD, LoginMethod.SMS);
        assertThat(context.allows(LoginMethod.SMS)).isTrue();
        assertThat(context.allows(LoginMethod.QRCODE)).isFalse();
    }

    /**
     * OAuth2 场景：从 {@code redirect} 查询串中反解出 {@code client_id}。
     */
    @Test
    void resolve_shouldExtractClientIdAndLoginMethods_forOAuthRedirect() {
        resolver = new SsoLoginContextResolver(appProtocolGuard);
        AppAuthConfigEntity authConfig = authConfig(List.of(LoginMethod.PASSWORD, LoginMethod.QRCODE));
        when(appProtocolGuard.tryResolveAuthConfig("my-oauth-client")).thenReturn(Optional.of(authConfig));

        SsoLoginContext context = resolver.resolve("http://sso.example.com/api/authn/oauth/authorize"
                + "?response_type=code&client_id=my-oauth-client&redirect_uri=https%3A%2F%2Fpartner.example.com%2Fcb");

        assertThat(context.appId()).isEqualTo("my-oauth-client");
        assertThat(context.allows(LoginMethod.QRCODE)).isTrue();
        assertThat(context.allows(LoginMethod.SMS)).isFalse();
    }

    /**
     * {@code redirect} 缺失（{@code null}/空白）时应保守返回仅含 {@code PASSWORD}，且不查询
     * 应用配置。
     */
    @Test
    void resolve_shouldReturnPasswordOnly_whenRedirectBlank() {
        resolver = new SsoLoginContextResolver(appProtocolGuard);

        SsoLoginContext context = resolver.resolve(null);

        assertThat(context.appId()).isNull();
        assertThat(context.allowedLoginMethods()).containsExactly(LoginMethod.PASSWORD);
        verifyNoInteractions(appProtocolGuard);
    }

    /**
     * {@code redirect} 无法匹配任何已知协议 URL 形状时应保守返回仅含 {@code PASSWORD}。
     */
    @Test
    void resolve_shouldReturnPasswordOnly_whenRedirectUnrecognizable() {
        resolver = new SsoLoginContextResolver(appProtocolGuard);

        SsoLoginContext context = resolver.resolve("http://sso.example.com/some/unrelated/path?x=1");

        assertThat(context.appId()).isNull();
        assertThat(context.allowedLoginMethods()).containsExactly(LoginMethod.PASSWORD);
        verifyNoInteractions(appProtocolGuard);
    }

    /**
     * 反解出 {@code appId} 但查不到该应用的认证配置时应保守返回仅含 {@code PASSWORD}，同时
     * 仍原样返回反解出的 {@code appId}。
     */
    @Test
    void resolve_shouldReturnPasswordOnly_whenAppConfigNotFound() {
        resolver = new SsoLoginContextResolver(appProtocolGuard);
        when(appProtocolGuard.tryResolveAuthConfig("not-exist-app")).thenReturn(Optional.empty());

        SsoLoginContext context = resolver.resolve("http://sso.example.com/api/authn/cas/not-exist-app/login?service=x");

        assertThat(context.appId()).isEqualTo("not-exist-app");
        assertThat(context.allowedLoginMethods()).containsExactly(LoginMethod.PASSWORD);
    }

    /**
     * 应用存在但 {@code loginMethods} 未设置（历史数据）时应保守按仅允许口令登录处理。
     */
    @Test
    void resolve_shouldReturnPasswordOnly_whenLoginMethodsBlank() {
        resolver = new SsoLoginContextResolver(appProtocolGuard);
        AppAuthConfigEntity authConfig = AppAuthConfigEntity.builder().appRefId(1L).loginMethods(null).build();
        when(appProtocolGuard.tryResolveAuthConfig(eq("legacy-app"))).thenReturn(Optional.of(authConfig));

        SsoLoginContext context = resolver.resolve("http://sso.example.com/api/authn/cas/legacy-app/login?service=x");

        assertThat(context.allowedLoginMethods()).containsExactly(LoginMethod.PASSWORD);
    }

    /**
     * 构造一份带指定登录认证方式的应用单点登录协议配置实体。
     *
     * @param loginMethods 登录认证方式列表
     * @return 应用单点登录协议配置实体
     */
    private AppAuthConfigEntity authConfig(List<String> loginMethods) {
        return AppAuthConfigEntity.builder()
                .appRefId(1L)
                .loginMethods(JacksonUtils.toJson(loginMethods))
                .build();
    }
}
