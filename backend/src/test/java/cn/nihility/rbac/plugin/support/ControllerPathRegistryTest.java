package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link ControllerPathRegistry} 单元测试：验证非覆盖 Controller 的路径去重校验
 * （plugin-jar-management capability spec "插件与已知路径/Bean 定义冲突"，plugin-jar-upgrade
 * change tasks.md 4.5）。
 */
class ControllerPathRegistryTest {

    /**
     * 首次登记一批不冲突的路径应成功。
     */
    @Test
    void tryRegister_shouldSucceed_whenPathsNotOccupied() {
        ControllerPathRegistry registry = new ControllerPathRegistry();
        Optional<String> result = registry.tryRegister("main", Set.of("/api/foo", "/api/bar"));
        assertThat(result).isEmpty();
    }

    /**
     * 与已登记路径冲突时应拒绝，且冲突提示包含冲突路径与原占用方标识。
     */
    @Test
    void tryRegister_shouldConflict_whenPathAlreadyOccupied() {
        ControllerPathRegistry registry = new ControllerPathRegistry();
        registry.tryRegister("main", Set.of("/api/foo"));

        Optional<String> conflict = registry.tryRegister("pluginA", Set.of("/api/foo"));

        assertThat(conflict).isPresent();
        assertThat(conflict.get()).contains("/api/foo").contains("main");
    }

    /**
     * 一批路径中只要有一条冲突，整批都不应登记（避免部分登记造成的不一致状态）。
     */
    @Test
    void tryRegister_shouldNotPartiallyRegister_whenOneOfMultiplePathsConflicts() {
        ControllerPathRegistry registry = new ControllerPathRegistry();
        registry.tryRegister("main", Set.of("/api/foo"));

        Optional<String> conflict = registry.tryRegister("pluginA", Set.of("/api/foo", "/api/new-one"));
        assertThat(conflict).isPresent();

        // /api/new-one 不应被登记（否则后续同名登记会被误判为冲突）。
        Optional<String> secondAttempt = registry.tryRegister("pluginB", Set.of("/api/new-one"));
        assertThat(secondAttempt).isEmpty();
    }
}
