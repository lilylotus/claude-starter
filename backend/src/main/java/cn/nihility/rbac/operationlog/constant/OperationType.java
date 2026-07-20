package cn.nihility.rbac.operationlog.constant;

/**
 * 操作类型常量：新增/编辑/启用/停用/删除，供 {@code OperationLogRecorder}
 * 写入日志及前端筛选下拉复用（前端硬编码这 5 个选项，不通过字典模块维护）。
 */
public final class OperationType {

    /** 新增。 */
    public static final int CREATE = 1;

    /** 编辑。 */
    public static final int UPDATE = 2;

    /** 启用。 */
    public static final int ENABLE = 3;

    /** 停用。 */
    public static final int DISABLE = 4;

    /** 删除。 */
    public static final int DELETE = 5;

    /**
     * 工具类不允许实例化。
     */
    private OperationType() {
    }

    /**
     * 将操作类型码值转换为中文文案。
     *
     * @param operationType 操作类型码值
     * @return 中文文案，取值不合法时返回 {@code null}
     */
    public static String label(Integer operationType) {
        if (operationType == null) {
            return null;
        }
        return switch (operationType) {
            case CREATE -> "新增";
            case UPDATE -> "编辑";
            case ENABLE -> "启用";
            case DISABLE -> "停用";
            case DELETE -> "删除";
            default -> null;
        };
    }
}
