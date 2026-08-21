package cn.nihility.rbac.sso.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.appaccess.support.AppAccessAuthorizationDecision;
import cn.nihility.rbac.appaccess.support.AppAccessEffectivePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppAccessAuthorizationChecker} 的单元测试（app-access-authorization change
 * tasks.md 8.5，app-access-request-control change tasks.md 6.1、
 * policy-condition-exclusive-priority change tasks.md 5.6 调整签名后同步更新），覆盖
 * 已授权放行、未授权抛出 {@link SsoProtocolException} 且携带拒绝来源策略 id 两种场景，
 * 确认 {@code clientIp}/{@code userAgent} 原样透传给带请求上下文的判定入口；集成到
 * CAS/OAuth2 凭证签发流程的场景由 {@code CasControllerTest}/{@code OAuthControllerTest}
 * 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class AppAccessAuthorizationCheckerTest {

    /** 被测组件的最终生效权限计算与查询依赖，使用 Mockito 打桩。 */
    @Mock
    private AppAccessEffectivePermissionService appAccessEffectivePermissionService;

    /** 被测组件实例。 */
    private AppAccessAuthorizationChecker checker;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        checker = new AppAccessAuthorizationChecker(appAccessEffectivePermissionService);
    }

    /**
     * 用户具备最终生效授权时，{@code assertAuthorized} 应正常返回，不抛出异常。
     */
    @Test
    void assertAuthorized_shouldNotThrow_whenAuthorized() {
        when(appAccessEffectivePermissionService.checkAuthorization(1L, 2L, "192.168.1.1", "test-ua"))
                .thenReturn(AppAccessAuthorizationDecision.allow());

        assertThatCode(() -> checker.assertAuthorized(1L, 2L, "192.168.1.1", "test-ua")).doesNotThrowAnyException();
    }

    /**
     * 用户不具备最终生效授权、且并非由具体策略造成（候选为空/人工例外拒绝）时，
     * {@code assertAuthorized} 应抛出 {@link SsoProtocolException}，其
     * {@code deniedByPolicyId} 为空，供调用方（{@code CasController}/
     * {@code OAuthController}）现有的拒绝响应分支捕获处理。
     */
    @Test
    void assertAuthorized_shouldThrow_whenNotAuthorized() {
        when(appAccessEffectivePermissionService.checkAuthorization(1L, 2L, "192.168.1.1", "test-ua"))
                .thenReturn(AppAccessAuthorizationDecision.denyWithoutPolicy());

        assertThatThrownBy(() -> checker.assertAuthorized(1L, 2L, "192.168.1.1", "test-ua"))
                .isInstanceOf(SsoProtocolException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((SsoProtocolException) e).getDeniedByPolicyId())
                        .isNull());
    }

    /**
     * 用户被排在最前的候选策略请求控制条件拒绝时，{@code assertAuthorized} 抛出的异常应
     * 携带该策略的 id（policy-condition-exclusive-priority change design.md Decision）。
     */
    @Test
    void assertAuthorized_shouldCarryDeniedPolicyId_whenDeniedByPolicy() {
        when(appAccessEffectivePermissionService.checkAuthorization(1L, 2L, "192.168.1.1", "test-ua"))
                .thenReturn(AppAccessAuthorizationDecision.denyByPolicy(100L));

        assertThatThrownBy(() -> checker.assertAuthorized(1L, 2L, "192.168.1.1", "test-ua"))
                .isInstanceOf(SsoProtocolException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((SsoProtocolException) e).getDeniedByPolicyId())
                        .isEqualTo(100L));
    }
}
