package cn.nihility.rbac.appaccess.override.constant;

import java.util.Set;

/**
 * 人工例外类型常量：{@code GRANT} 手动追加授权、{@code DENY} 手动收回授权（黑名单式覆盖，
 * 优先级最高，见 {@code AppAccessEffectivePermissionService} 的合并判定规则）。
 */
public final class OverrideType {

    /** 手动追加授权：即使用户不在任何策略命中范围内也能访问该应用。 */
    public static final String GRANT = "GRANT";

    /** 手动收回授权：即使用户命中某策略或存在手动追加授权也不能访问该应用。 */
    public static final String DENY = "DENY";

    /** 全部合法取值，供保存时校验请求参数使用。 */
    public static final Set<String> ALL_TYPES = Set.of(GRANT, DENY);

    /**
     * 工具类不允许实例化。
     */
    private OverrideType() {
    }
}
