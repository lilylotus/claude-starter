package cn.nihility.rbac.excelimport.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.constant.ImportBizTypes;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import cn.nihility.rbac.excelimport.service.ImportTemplateService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Excel 导入模板生成业务逻辑实现，基于 Apache POI 生成 {@code .xlsx} 文件。
 */
@Service
@RequiredArgsConstructor
public class ImportTemplateServiceImpl implements ImportTemplateService {

    /** 模板工作表名称。 */
    private static final String SHEET_NAME = "导入模板";

    /** 表头列默认宽度（字符数 * 256，POI 列宽单位）。 */
    private static final int COLUMN_WIDTH = 20 * 256;

    /** 导入字段配置业务逻辑接口，用于查询启用状态的导入字段配置驱动表头生成。 */
    private final ImportFieldConfigService importFieldConfigService;

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] generateTemplate(String bizType) {
        if (!ImportBizTypes.isValid(bizType)) {
            throw new BusinessException("不支持的业务对象类型：" + bizType);
        }
        List<ImportFieldConfigVO> configs = importFieldConfigService.listActiveByBizType(bizType);
        if (configs.isEmpty()) {
            throw new BusinessException("尚未配置该业务对象类型的导入字段，无法生成导入模板");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            Row headerRow = sheet.createRow(0);
            CellStyle requiredStyle = buildRequiredHeaderStyle(workbook);

            for (int i = 0; i < configs.size(); i++) {
                ImportFieldConfigVO config = configs.get(i);
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(config.getExcelHeaderName());
                if (Boolean.TRUE.equals(config.getIsRequired())) {
                    cell.setCellStyle(requiredStyle);
                }
                sheet.setColumnWidth(i, COLUMN_WIDTH);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("生成导入模板失败");
        }
    }

    /**
     * 构造必填表头列样式：字体加粗、颜色标红。
     *
     * @param workbook 工作簿
     * @return 必填表头单元格样式
     */
    private CellStyle buildRequiredHeaderStyle(XSSFWorkbook workbook) {
        Font requiredFont = workbook.createFont();
        requiredFont.setBold(true);
        requiredFont.setColor(IndexedColors.RED.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(requiredFont);
        return style;
    }
}
