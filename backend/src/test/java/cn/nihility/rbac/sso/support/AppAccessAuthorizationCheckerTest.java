package cn.nihility.rbac.sso.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.appaccess.support.AppAccessEffectivePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppAccessAuthorizationChecker} 的单元测试（app-access-authorization change
 * tasks.md 8.5，app-access-request-control change tasks.md 6.1 调整签名后同步更新），
 * 覆盖已授权放行、未授权抛出 {@link SsoProtocolException} 两种场景，确认 {@code clientIp}/
 * {@code userAgent} 原样透传给带请求上下文的判定入口；集成到 CAS/OAuth2 凭证签发流程的
 * 场景由 {@code CasControllerTest}/{@code OAuthControllerTest} 覆盖。
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
        when(appAccessEffectivePermissionService.isAuthorized(1L, 2L, "192.168.1.1", "test-ua")).thenReturn(true);

        assertThatCode(() -> checker.assertAuthorized(1L, 2L, "192.168.1.1", "test-ua")).doesNotThrowAnyException();
    }

    /**
     * 用户不具备最终生效授权时，{@code assertAuthorized} 应抛出
     * {@link SsoProtocolException}，供调用方（{@code CasController}/{@code OAuthController}）
     * 现有的拒绝响应分支捕获处理。
     */
    @Test
    void assertAuthorized_shouldThrow_whenNotAuthorized() {
        when(appAccessEffectivePermissionService.isAuthorized(1L, 2L, "192.168.1.1", "test-ua")).thenReturn(false);

        assertThatThrownBy(() -> checker.assertAuthorized(1L, 2L, "192.168.1.1", "test-ua"))
                .isInstanceOf(SsoProtocolException.class);
    }
}
