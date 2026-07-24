package cn.nihility.rbac.excelimport.constant;

/**
 * Excel 导入能力相关的共享数量上限常量，供模板生成（下拉数据校验覆盖的行区域）与
 * 批量导入（单次上传允许处理的最大数据行数）两处共用同一个数值，避免各自硬编码一份
 * 导致后续修改行数上限时只改一处而遗漏另一处。
 */
public final class ImportLimits {

    /** 单次上传允许处理的最大数据行数（不含表头行），超出拒绝并提示分批上传。 */
    public static final int MAX_ROW_COUNT = 1000;

    /**
     * 工具类不允许实例化。
     */
    private ImportLimits() {
    }
}
