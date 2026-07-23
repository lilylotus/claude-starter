package cn.nihility.rbac.excelimport.constant;

/**
 * Excel 导入字段配置状态常量。与项目内其他主数据模块不同，本模块没有独立的
 * "停用"语义（导入配置要么启用参与模板生成/导入，要么被逻辑删除），只保留启用与
 * 已删除两种取值，仍沿用同一套数值体系便于跨模块比对阅读。
 */
public final class ImportFieldConfigStatus {

    /** 启用。 */
    public static final int ENABLED = 2000;

    /** 已删除（逻辑删除）。 */
    public static final int DELETED = -1000;

    /**
     * 工具类不允许实例化。
     */
    private ImportFieldConfigStatus() {
    }
}
