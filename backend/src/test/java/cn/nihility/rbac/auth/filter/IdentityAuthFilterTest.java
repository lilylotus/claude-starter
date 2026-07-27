package cn.nihility.rbac.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link IdentityAuthFilter} 的单元测试，覆盖白名单放行、缺失/无效 identity-token、
 * 缺失/格式不合法的 menu 请求头、首登强制改密拦截及其白名单豁免等分支
 * （password-login-auth spec.md "请求身份校验"/"操作资源请求头校验"/"首次登录强制改密"相关 Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class IdentityAuthFilterTest {

    /** 被测过滤器的会话令牌业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private TokenService tokenService;

    /** 被测过滤器的密码业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private PasswordService passwordService;

    /** 被测过滤器实例。 */
    private IdentityAuthFilter filter;

    /**
     * 每个用例执行前重新构造被测过滤器，清空线程级当前登录用户标记。
     */
    @BeforeEach
    void setUp() {
        filter = new IdentityAuthFilter(tokenService, passwordService);
        CurrentUserContext.clear();
    }

    /**
     * 每个用例结束后清空线程级当前登录用户标记，避免污染后续用例。
     */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /**
     * 白名单路径（如登录接口）应直接放行，不做任何请求头校验。
     */
    @Test
    void doFilter_shouldPass_whenPathIsWhitelisted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    /**
     * 未携带 {@code identity-token} 请求头时，应拦截请求并返回未登录业务错误
     * （spec.md "未携带身份标识请求头" Scenario）。
     */
    @Test
    void doFilter_shouldReturnUnauthorized_whenIdentityTokenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.UNAUTHORIZED);
    }

    /**
     * {@code identity-token} 对应的 access-key 无效/已过期时，应拦截请求并返回未登录业务错误
     * （spec.md "携带已过期的 access-key" Scenario）。
     */
    @Test
    void doFilter_shouldReturnUnauthorized_whenAccessKeyInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("identity-token", "expired-access-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("expired-access-key")).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.UNAUTHORIZED);
    }

    /**
     * access-key 有效但缺失 {@code menu} 请求头时，应拦截请求（spec.md "未携带操作资源请求头" Scenario）。
     */
    @Test
    void doFilter_shouldReturnUnauthorized_whenMenuHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("identity-token", "valid-access-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.UNAUTHORIZED);
    }

    /**
     * access-key 有效但 {@code menu} 请求头格式不合法时，应拦截请求
     * （spec.md "操作资源请求头格式不合法" Scenario）。
     */
    @Test
    void doFilter_shouldReturnUnauthorized_whenMenuHeaderMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "not-a-valid-menu-code");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.UNAUTHORIZED);
    }

    /**
     * 首登待改密用户访问非改密类业务接口时应被拦截，返回专门的首登业务错误码
     * （spec.md "首登用户访问业务接口被拦截" Scenario）。
     */
    @Test
    void doFilter_shouldReturnFirstLoginRequired_whenUserPendingFirstLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "UserManagement:user:view");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));
        when(passwordService.isFirstLogin(1L)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.FIRST_LOGIN_REQUIRED);
    }

    /**
     * 修改密码接口在"首登强制改密白名单"内，应正常放行，不因首登状态而拦截；由于路径已在
     * 白名单内，{@code isFirstLogin} 校验会被短路跳过，不需要也不应该再对其打桩
     * （spec.md "首登用户可以访问修改密码接口" Scenario）。
     */
    @Test
    void doFilter_shouldPass_whenAccessingChangePasswordPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/password");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "Auth:password:change");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(passwordService, never()).isFirstLogin(any());
    }

    /**
     * access-key、menu 请求头均合法且非首登待改密状态时，应放行请求
     * （spec.md "携带有效 access-key" Scenario）。
     */
    @Test
    void doFilter_shouldPass_whenAllChecksSucceed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "UserManagement:user:view");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));
        when(passwordService.isFirstLogin(1L)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
