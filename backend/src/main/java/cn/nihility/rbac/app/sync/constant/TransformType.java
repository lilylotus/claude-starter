package cn.nihility.rbac.app.sync.constant;

import java.util.Set;

/**
 * 应用同步字段映射的转换方式常量（app-sync-field-mapping change proposal.md）。取值直接
 * 对应 {@code tab_app_sync_field_mapping.transform_type} 列的存储值。
 */
public final class TransformType {

    /** 不转换：应用侧目标字段直接取源字段的原始值。 */
    public static final String NO_TRANSFORM = "NO_TRANSFORM";

    /** 固定值：应用侧目标字段固定取 {@code transformValue} 中配置的值，忽略源字段实际值。 */
    public static final String FIXED_VALUE = "FIXED_VALUE";

    /** 转换脚本：{@code transformValue} 是一段 JavaScript 源码，保存前只做语法校验，不执行。 */
    public static final String SCRIPT = "SCRIPT";

    /** 全部转换方式取值，供请求参数校验使用。 */
    public static final Set<String> ALL_TYPES = Set.of(NO_TRANSFORM, FIXED_VALUE, SCRIPT);

    /**
     * 工具类不允许实例化。
     */
    private TransformType() {
    }
}
