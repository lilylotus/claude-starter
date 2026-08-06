package cn.nihility.rbac.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CurrentOperatorServiceImpl} 的单元测试，覆盖"已登录会话正常解析"、
 * "{@code CurrentUserContext} 未设置时抛异常"、"用户 id 查不到对应用户时抛异常"三种场景
 * （real-operator-audit-fields spec.md "统一解析当前登录操作人账号编码" Requirement）。
 */
@ExtendWith(MockitoExtension.class)
class CurrentOperatorServiceImplTest {

    /** 被测服务的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /**
     * 已登录会话下，应能正确解析出当前登录用户的账号编码。
     */
    @Test
    void resolveCode_shouldReturnUserCode_whenLoggedIn() {
        CurrentUserContext.setUserId(1L);
        try {
            UserEntity user = UserEntity.builder().id(1L).code("ops01").build();
            when(userMapper.selectById(1L)).thenReturn(user);

            CurrentOperatorServiceImpl service = new CurrentOperatorServiceImpl(userMapper);

            assertThat(service.resolveCode()).isEqualTo("ops01");
        } finally {
            CurrentUserContext.clear();
        }
    }

    /**
     * {@code CurrentUserContext} 未标记当前登录用户 id 时，应抛出 {@link IllegalStateException}，
     * 而不是静默返回某个固定占位符。
     */
    @Test
    void resolveCode_shouldThrowIllegalStateException_whenCurrentUserContextNotSet() {
        CurrentUserContext.clear();
        try {
            CurrentOperatorServiceImpl service = new CurrentOperatorServiceImpl(userMapper);

            assertThatThrownBy(service::resolveCode).isInstanceOf(IllegalStateException.class);
        } finally {
            CurrentUserContext.clear();
        }
    }

    /**
     * {@code CurrentUserContext} 标记的用户 id 查不到对应用户记录时，应抛出
     * {@link IllegalStateException}。
     */
    @Test
    void resolveCode_shouldThrowIllegalStateException_whenUserNotFound() {
        CurrentUserContext.setUserId(999L);
        try {
            when(userMapper.selectById(999L)).thenReturn(null);

            CurrentOperatorServiceImpl service = new CurrentOperatorServiceImpl(userMapper);

            assertThatThrownBy(service::resolveCode).isInstanceOf(IllegalStateException.class);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
