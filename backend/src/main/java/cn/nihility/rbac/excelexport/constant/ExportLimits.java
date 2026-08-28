package cn.nihility.rbac.excelexport.constant;

/**
 * Excel 导出能力相关的共享数量上限常量，避免单次导出海量数据拖垮内存/接口耗时
 * （design.md Decision 1）。
 */
public final class ExportLimits {

    /** 单次导出允许生成的最大数据行数（不含表头行），超出拒绝生成文件并提示缩小管辖范围。 */
    public static final int MAX_ROW_COUNT = 50000;

    /**
     * 工具类不允许实例化。
     */
    private ExportLimits() {
    }
}
