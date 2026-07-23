package cn.nihility.rbac.operationlog.constant;

/**
 * 操作来源常量：区分某条操作日志记录是页面手动操作还是 Excel 批量导入产生的，
 * 写法比照 {@link OperationType}（fix-excel-import-followups design.md 决策 3）。
 */
public final class OperationSource {

    /** 界面操作。 */
    public static final int MANUAL = 0;

    /** Excel 导入。 */
    public static final int IMPORT = 1;

    /**
     * 工具类不允许实例化。
     */
    private OperationSource() {
    }

    /**
     * 将操作来源码值转换为中文文案。
     *
     * @param operateSource 操作来源码值
     * @return 中文文案，取值不合法时返回 {@code null}
     */
    public static String label(Integer operateSource) {
        if (operateSource == null) {
            return null;
        }
        return switch (operateSource) {
            case MANUAL -> "界面操作";
            case IMPORT -> "Excel导入";
            default -> null;
        };
    }
}
