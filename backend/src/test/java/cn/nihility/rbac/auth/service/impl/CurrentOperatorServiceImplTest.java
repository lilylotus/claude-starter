package cn.nihility.rbac.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import org.junit.jupiter.api.Test;

/**
 * {@link CurrentOperatorServiceImpl} 的单元测试，覆盖"已登录会话正常解析出用户 id"、
 * "{@code CurrentUserContext} 未设置时抛异常"两种场景（audit-fields-store-user-id
 * design.md "统一解析当前登录操作人账号编码" Requirement）。
 */
class CurrentOperatorServiceImplTest {

    /**
     * 已登录会话下，应能正确解析出当前登录用户的用户 id。
     */
    @Test
    void resolveUserId_shouldReturnUserId_whenLoggedIn() {
        CurrentUserContext.setUserId(1L);
        try {
            CurrentOperatorServiceImpl service = new CurrentOperatorServiceImpl();

            assertThat(service.resolveUserId()).isEqualTo(1L);
        } finally {
            CurrentUserContext.clear();
        }
    }

    /**
     * {@code CurrentUserContext} 未标记当前登录用户 id 时，应抛出 {@link IllegalStateException}，
     * 而不是静默返回某个固定占位符。
     */
    @Test
    void resolveUserId_shouldThrowIllegalStateException_whenCurrentUserContextNotSet() {
        CurrentUserContext.clear();
        try {
            CurrentOperatorServiceImpl service = new CurrentOperatorServiceImpl();

            assertThatThrownBy(service::resolveUserId).isInstanceOf(IllegalStateException.class);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
