package cn.nihility.rbac.plugin.support;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 静态反射解析 Controller 类对外暴露的请求路径集合（plugin-bean-override capability spec
 * "覆盖 Controller 接口"、plugin-jar-management capability spec "插件与已知路径/Bean 定义
 * 冲突"）：用于在 Bean 定义注册阶段（早于 {@code RequestMappingHandlerMapping} 初始化）
 * 提前发现路径冲突，避免退化到运行期才因"Ambiguous mapping"导致启动失败。
 * <p>
 * {@code @GetMapping}/{@code @PostMapping} 等快捷注解均元标注了 {@code @RequestMapping}
 * 并通过 {@code @AliasFor} 把 {@code path}/{@code value} 别名到 {@code @RequestMapping}，
 * {@link AnnotatedElementUtils#findMergedAnnotation} 能正确合并解析出实际路径，因此本类
 * 统一只处理合并后的 {@link RequestMapping}，不需要分别处理每个快捷注解。
 * <p>
 * 本解析为"尽量提前发现冲突"的最佳努力实现，不追求与 Spring 运行期完整路径匹配语义
 * （如 HTTP method、请求头/参数条件、Ant 通配符等价性判断）完全一致。
 */
public final class RequestMappingPathResolver {

    /**
     * 工具类不允许实例化。
     */
    private RequestMappingPathResolver() {
    }

    /**
     * 解析 Controller 类对外暴露的全部请求路径。
     *
     * @param controllerClass Controller 类
     * @return 请求路径集合，类上既无类型级也无方法级 {@code @RequestMapping} 时返回空集合
     */
    public static Set<String> resolvePaths(Class<?> controllerClass) {
        RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class);
        List<String> typePaths = typeMapping != null ? combinedPaths(typeMapping) : List.of("");

        Set<String> result = new LinkedHashSet<>();
        for (Method method : controllerClass.getMethods()) {
            RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (methodMapping == null) {
                continue;
            }
            List<String> methodPaths = combinedPaths(methodMapping);
            if (methodPaths.isEmpty()) {
                methodPaths = List.of("");
            }
            for (String typePath : typePaths) {
                for (String methodPath : methodPaths) {
                    result.add(normalize(typePath + methodPath));
                }
            }
        }
        if (result.isEmpty() && typeMapping != null) {
            for (String typePath : typePaths) {
                result.add(normalize(typePath));
            }
        }
        return result;
    }

    /**
     * 提取 {@code @RequestMapping} 上的路径值（{@code path}/{@code value} 互为别名），
     * 未声明路径时返回单元素空字符串列表。
     *
     * @param mapping {@code @RequestMapping} 合并注解
     * @return 路径值列表
     */
    private static List<String> combinedPaths(RequestMapping mapping) {
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length > 0 ? List.of(paths) : List.of("");
    }

    /**
     * 规范化拼接后的路径：合并多余的 {@code /}、补齐前导 {@code /}、去除多余的尾部 {@code /}。
     *
     * @param rawPath 原始拼接路径
     * @return 规范化后的路径
     */
    private static String normalize(String rawPath) {
        String combined = rawPath.replaceAll("/+", "/");
        if (!combined.startsWith("/")) {
            combined = "/" + combined;
        }
        if (combined.length() > 1 && combined.endsWith("/")) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined;
    }
}
