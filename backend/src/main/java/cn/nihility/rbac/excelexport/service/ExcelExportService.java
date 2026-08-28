package cn.nihility.rbac.excelexport.service;

/**
 * Excel 导出业务逻辑接口：按业务对象类型（ORG/USER/POSITION/APP）生成一个 {@code .xlsx}
 * 文件的字节内容。
 */
public interface ExcelExportService {

    /**
     * 按业务对象类型生成导出 Excel 的字节内容。
     *
     * @param bizType 业务对象类型：ORG/USER/POSITION/APP
     * @return {@code .xlsx} 文件字节内容
     */
    byte[] export(String bizType);
}
