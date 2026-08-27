package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link OverrideDenyListChecker} 单元测试：验证覆盖黑名单命中判断
 * （plugin-bean-override capability spec "覆盖范围限制"，plugin-jar-upgrade change
 * tasks.md 4.6）。
 */
class OverrideDenyListCheckerTest {

    /**
     * 目标类全限定名命中黑名单时应判定为拒绝。
     */
    @Test
    void isDenied_shouldReturnTrue_whenTargetInDenyList() {
        OverrideDenyListChecker checker =
                new OverrideDenyListChecker(Set.of("cn.nihility.rbac.auth.filter.IdentityAuthFilter"));
        assertThat(checker.isDenied("cn.nihility.rbac.auth.filter.IdentityAuthFilter")).isTrue();
    }

    /**
     * 目标类全限定名未命中黑名单时应判定为允许。
     */
    @Test
    void isDenied_shouldReturnFalse_whenTargetNotInDenyList() {
        OverrideDenyListChecker checker =
                new OverrideDenyListChecker(Set.of("cn.nihility.rbac.auth.filter.IdentityAuthFilter"));
        assertThat(checker.isDenied("cn.nihility.rbac.some.OtherService")).isFalse();
    }
}
