package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link RequestMappingPathResolver} 单元测试。
 */
class RequestMappingPathResolverTest {

    /**
     * 类型级前缀 + 方法级路径应正确拼接。
     */
    @Test
    void resolvePaths_shouldCombineTypeAndMethodLevelPaths() {
        Set<String> paths = RequestMappingPathResolver.resolvePaths(WithTypeLevelPrefix.class);
        assertThat(paths).containsExactlyInAnyOrder("/api/foo/bar", "/api/foo/baz");
    }

    /**
     * 只有方法级路径、无类型级前缀时应直接使用方法级路径。
     */
    @Test
    void resolvePaths_shouldUseMethodPathOnly_whenNoTypeLevelMapping() {
        Set<String> paths = RequestMappingPathResolver.resolvePaths(WithoutTypeLevelPrefix.class);
        assertThat(paths).containsExactly("/only-method");
    }

    /**
     * 只有类型级路径、没有任何方法级 {@code @RequestMapping} 时应返回类型级路径本身。
     */
    @Test
    void resolvePaths_shouldUseTypePathOnly_whenNoMethodLevelMapping() {
        Set<String> paths = RequestMappingPathResolver.resolvePaths(WithTypeLevelOnly.class);
        assertThat(paths).containsExactly("/only-type");
    }

    /**
     * 既无类型级也无方法级 {@code @RequestMapping} 时应返回空集合。
     */
    @Test
    void resolvePaths_shouldReturnEmpty_whenNoMappingAtAll() {
        assertThat(RequestMappingPathResolver.resolvePaths(NotAController.class)).isEmpty();
    }

    /** 类型级前缀 + 两个方法级路径的示例 Controller。 */
    @RestController
    @RequestMapping("/api/foo")
    static class WithTypeLevelPrefix {

        /**
         * 示例方法。
         *
         * @return 占位返回值
         */
        @GetMapping("/bar")
        public String bar() {
            return "bar";
        }

        /**
         * 示例方法。
         *
         * @return 占位返回值
         */
        @PostMapping("/baz")
        public String baz() {
            return "baz";
        }
    }

    /** 无类型级前缀、只有方法级路径的示例 Controller。 */
    @RestController
    static class WithoutTypeLevelPrefix {

        /**
         * 示例方法。
         *
         * @return 占位返回值
         */
        @GetMapping("/only-method")
        public String onlyMethod() {
            return "only-method";
        }
    }

    /** 只有类型级路径、无任何方法级映射的示例 Controller。 */
    @RestController
    @RequestMapping("/only-type")
    static class WithTypeLevelOnly {
    }

    /** 完全没有 {@code @RequestMapping} 的普通类。 */
    static class NotAController {
    }
}
