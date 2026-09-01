package cn.nihility.rbac.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.AuthorizationService;
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
 * 缺失/格式不合法的 menu 请求头、首登强制改密拦截及其白名单豁免、运行时权限判断放行/拦截
 * 等分支（password-login-auth spec.md "请求身份校验"/"操作资源请求头校验"/"首次登录强制改密"、
 * rbac-permission-authorization spec.md "无权限访问拦截"相关 Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class IdentityAuthFilterTest {

    /** 被测过滤器的会话令牌业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private TokenService tokenService;

    /** 被测过滤器的密码业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private PasswordService passwordService;

    /** 被测过滤器的运行时鉴权业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private AuthorizationService authorizationService;

    /** 被测过滤器实例。 */
    private IdentityAuthFilter filter;

    /**
     * 每个用例执行前重新构造被测过滤器，清空线程级当前登录用户标记。
     */
    @BeforeEach
    void setUp() {
        filter = new IdentityAuthFilter(tokenService, passwordService, authorizationService);
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
     * 修改密码接口在"首登强制改密白名单"内，应正常放行，不因首登状态而拦截，也不做权限点
     * 匹配判断——修改自己的密码是不区分权限点的自助操作，该资源编码未被登记进权限点种子
     * 数据，若对其也做权限判断会导致包括默认账号在内的一切用户在首登改密这一步就被鉴权
     * 机制自己锁死；由于路径已在白名单内，{@code isFirstLogin}/{@code hasPermission}
     * 校验均会被短路跳过，不需要也不应该再对其打桩（spec.md "首登用户可以访问修改密码接口"
     * Scenario）。
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
        verify(authorizationService, never()).hasPermission(any(), any());
    }

    /**
     * access-key、menu 请求头均合法、非首登待改密状态、且当前用户拥有请求 menu 编码对应的
     * 权限点时，应放行请求（rbac-permission-authorization spec.md "拥有对应权限点的管理员
     * 请求被放行" Scenario）。
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
        when(authorizationService.hasPermission(1L, "UserManagement:user:view")).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    /**
     * 当前用户不拥有请求 menu 编码对应的权限点时（含没有任何启用状态管理员身份的情况，
     * {@link AuthorizationService} 内部对此统一返回 {@code false}），应拦截该请求并返回
     * 专门的无权限业务错误码，不执行后续业务逻辑（rbac-permission-authorization spec.md
     * "无对应权限点时请求被拦截"/"无管理员身份的用户默认零权限" Scenario）。
     */
    @Test
    void doFilter_shouldReturnForbidden_whenUserLacksPermission() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "UserManagement:user:view");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));
        when(passwordService.isFirstLogin(1L)).thenReturn(false);
        when(authorizationService.hasPermission(1L, "UserManagement:user:view")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.FORBIDDEN);
    }

    /**
     * 无审批开关编辑权限时，应在进入审批开关 Controller 前被拦截。
     */
    @Test
    void doFilter_shouldReturnForbidden_whenUserCannotEditApprovalSwitch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PUT",
                "/api/approval-switches/ORG");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "ApprovalManagement:switch:edit");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));
        when(passwordService.isFirstLogin(1L)).thenReturn(false);
        when(authorizationService.hasPermission(1L, "ApprovalManagement:switch:edit")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.FORBIDDEN);
    }

    /**
     * 无审批处理权限时，应在进入待审批查询 Controller 前被拦截。
     */
    @Test
    void doFilter_shouldReturnForbidden_whenUserCannotViewPendingApprovals() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/approval-requests/pending");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "ApprovalManagement:request:approve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));
        when(passwordService.isFirstLogin(1L)).thenReturn(false);
        when(authorizationService.hasPermission(1L, "ApprovalManagement:request:approve")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("\"code\":" + AuthErrorCode.FORBIDDEN);
    }

    /**
     * “我的申请”属于登录用户自助查询，应绕过角色权限点判断。
     */
    @Test
    void doFilter_shouldPassMineApprovalQuery_asSelfService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/approval-requests/mine");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "ApprovalManagement:request:view");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(passwordService, never()).isFirstLogin(any());
        verify(authorizationService, never()).hasPermission(any(), any());
    }

    /**
     * 表单字段渲染元数据接口供审批详情弹窗展示新旧字段对照使用，调用者不应被要求额外
     * 持有被审批业务对象的管理权限点，应绕过角色权限点判断（fix-approval-detail-
     * render-schema-permission change）。
     */
    @Test
    void doFilter_shouldPassFormFieldRenderSchema_asSelfService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/form-fields/render-schema");
        request.addHeader("identity-token", "valid-access-key");
        request.addHeader("menu", "ApprovalManagement:request:approve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = org.mockito.Mockito.mock(FilterChain.class);
        when(tokenService.verifyAccessKey("valid-access-key")).thenReturn(Optional.of(1L));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(passwordService, never()).isFirstLogin(any());
        verify(authorizationService, never()).hasPermission(any(), any());
    }
}
