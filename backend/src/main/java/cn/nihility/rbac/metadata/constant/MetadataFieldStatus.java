package cn.nihility.rbac.metadata.constant;

/**
 * 元数据字段状态常量。状态值同时承担"启用/停用"与"逻辑删除"两种语义，
 * 与项目内其他主数据模块保持一致的取值约定；元数据字段目录当前没有删除接口，
 * {@link #DELETED} 只是保留同一取值体系，暂无写入路径。
 */
public final class MetadataFieldStatus {

    /** 启用。 */
    public static final int ENABLED = 2000;

    /** 停用。 */
    public static final int DISABLED = 3000;

    /** 已删除（逻辑删除，当前无接口可写入该状态）。 */
    public static final int DELETED = -1000;

    /**
     * 工具类不允许实例化。
     */
    private MetadataFieldStatus() {
    }
}
