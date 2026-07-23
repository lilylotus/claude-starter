package cn.nihility.rbac.excelimport.service;

/**
 * Excel 导入模板生成业务逻辑接口。
 */
public interface ImportTemplateService {

    /**
     * 按业务对象类型生成 Excel 导入模板：首行为表头，列顺序按该业务对象类型下启用的
     * 导入字段配置的显示序号升序排列，必填列对应的表头单元格加粗、字体标红。
     *
     * @param bizType 业务对象类型：ORG/USER/POSITION/APP
     * @return {@code .xlsx} 文件内容字节数组
     */
    byte[] generateTemplate(String bizType);
}
