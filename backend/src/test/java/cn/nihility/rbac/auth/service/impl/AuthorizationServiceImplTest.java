package cn.nihility.rbac.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.permission.mapper.PermissionMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuthorizationServiceImpl} 的单元测试，覆盖有权限放行、无权限拦截、无管理员身份
 * 用户零权限、权限点被停用/角色被移除权限点后不缓存、实时重新查库等分支
 * （rbac-permission-authorization spec.md "无权限访问拦截" Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceImplTest {

    /** 被测服务的权限点数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PermissionMapper permissionMapper;

    /** 被测服务实例。 */
    private AuthorizationServiceImpl authorizationService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationServiceImpl(permissionMapper);
    }

    /**
     * 用户当前拥有的权限编码集合包含请求编码时，应判定为有权限
     * （spec.md "拥有对应权限点的管理员请求被放行" Scenario）。
     */
    @Test
    void hasPermission_shouldReturnTrue_whenCodeGranted() {
        when(permissionMapper.selectGrantedPermissionCodesByUserId(1L))
                .thenReturn(Set.of("UserManagement:user:view"));

        boolean result = authorizationService.hasPermission(1L, "UserManagement:user:view");

        assertThat(result).isTrue();
    }

    /**
     * 用户当前拥有的权限编码集合不包含请求编码时，应判定为无权限
     * （spec.md "无对应权限点时请求被拦截" Scenario）。
     */
    @Test
    void hasPermission_shouldReturnFalse_whenCodeNotGranted() {
        when(permissionMapper.selectGrantedPermissionCodesByUserId(1L))
                .thenReturn(Set.of("UserManagement:user:edit"));

        boolean result = authorizationService.hasPermission(1L, "UserManagement:user:view");

        assertThat(result).isFalse();
    }

    /**
     * 用户没有任何启用状态的管理员身份时（对应 Mapper 返回空集合），应判定为无权限
     * （spec.md "无管理员身份的用户默认零权限" Scenario）。
     */
    @Test
    void hasPermission_shouldReturnFalse_whenUserHasNoAdminIdentity() {
        when(permissionMapper.selectGrantedPermissionCodesByUserId(2L)).thenReturn(Set.of());

        boolean result = authorizationService.hasPermission(2L, "UserManagement:user:view");

        assertThat(result).isFalse();
    }

    /**
     * 每次判断都应重新调用数据访问层查询最新的权限编码集合，不做任何缓存：同一用户、
     * 同一权限编码，前一次查询返回已授权、后一次查询返回未授权（模拟权限点被停用/角色被
     * 移除权限点后的效果）时，两次判断结果应分别如实反映当次查询结果，不受上一次结果影响
     * （spec.md "权限点被停用后立即生效"/"角色被移除某权限点后立即生效" Scenario，
     * design.md Decision 4 "不缓存"）。
     */
    @Test
    void hasPermission_shouldQueryEveryTime_reflectingLatestGrantedCodes() {
        when(permissionMapper.selectGrantedPermissionCodesByUserId(1L))
                .thenReturn(Set.of("UserManagement:user:view"))
                .thenReturn(Set.of());

        boolean firstResult = authorizationService.hasPermission(1L, "UserManagement:user:view");
        boolean secondResult = authorizationService.hasPermission(1L, "UserManagement:user:view");

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();
    }
}
