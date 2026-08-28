package cn.nihility.rbac.excelexport.support;

/**
 * 导出 Excel 的一列定义：表头文字 + 取值用的 Java Bean 属性名，供
 * {@code cn.nihility.rbac.excelexport.service.impl.ExcelExportServiceImpl} 按行反射
 * 取值。字段定义驱动列额外携带 {@code controlType}/{@code dictTypeCode}，供字典列换算
 * 展示标签（design.md Decision 5/6）；任职/应用导出固定携带的关联展示列
 * （姓名/组织、负责人/所属组织）不参与字典换算，{@code controlType}/{@code dictTypeCode}
 * 均为 {@code null}。
 *
 * @param header       表头文字
 * @param propertyName 取值用的 Java Bean 属性名（已从元数据字段的数据库列名转换为
 *                     驼峰形式，与前端 {@code toCamelCase} 的转换约定保持一致）
 * @param controlType  控件类型，固定关联展示列为 {@code null}
 * @param dictTypeCode 关联的字典类型编码，非字典类字段或固定关联展示列为 {@code null}
 */
public record ExportColumn(String header, String propertyName, Integer controlType, String dictTypeCode) {

    /**
     * 构造一个不参与字典换算的固定关联展示列（如任职导出的"姓名"/"组织"、应用导出的
     * "负责人"/"所属组织"）。
     *
     * @param header       表头文字
     * @param propertyName 取值用的 Java Bean 属性名
     * @return 固定关联展示列定义
     */
    public static ExportColumn fixed(String header, String propertyName) {
        return new ExportColumn(header, propertyName, null, null);
    }

    /**
     * 构造一个由表单字段定义驱动的列。
     *
     * @param header       表头文字（字段定义的展示名称）
     * @param propertyName 取值用的 Java Bean 属性名
     * @param controlType  控件类型
     * @param dictTypeCode 关联的字典类型编码，非字典类字段为 {@code null}
     * @return 字段定义驱动列定义
     */
    public static ExportColumn dynamic(String header, String propertyName, Integer controlType,
            String dictTypeCode) {
        return new ExportColumn(header, propertyName, controlType, dictTypeCode);
    }
}
