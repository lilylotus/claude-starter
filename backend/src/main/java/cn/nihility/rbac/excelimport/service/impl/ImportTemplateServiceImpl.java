package cn.nihility.rbac.excelimport.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.constant.ImportBizTypes;
import cn.nihility.rbac.excelimport.constant.ImportLimits;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import cn.nihility.rbac.excelimport.service.ImportTemplateService;
import cn.nihility.rbac.excelimport.service.support.DictImportColumnSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
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

    /** 字典选项隐藏辅助工作表名称，一个 {@code dictTypeCode} 占一列（design.md Decision 2）。 */
    private static final String DICT_SHEET_NAME = "字典选项";

    /** 表头列默认宽度（字符数 * 256，POI 列宽单位）。 */
    private static final int COLUMN_WIDTH = 20 * 256;

    /** 数据校验输入错误时弹窗的标题。 */
    private static final String VALIDATION_ERROR_TITLE = "输入错误";

    /** 数据校验输入错误时弹窗的提示文案。 */
    private static final String VALIDATION_ERROR_TEXT = "请从下拉列表中选择有效的字典选项";

    /** 导入字段配置业务逻辑接口，用于查询启用状态的导入字段配置驱动表头生成。 */
    private final ImportFieldConfigService importFieldConfigService;

    /** 字典列支持组件，用于识别字典下拉单选列并取得其启用字典项的 label 列表。 */
    private final DictImportColumnSupport dictImportColumnSupport;

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

            Map<String, String> dictColumns = dictImportColumnSupport.resolveDictColumns(configs);
            if (!dictColumns.isEmpty()) {
                applyDictDropdowns(workbook, sheet, configs, dictColumns);
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

    /**
     * 为 {@code dictColumns} 涉及的每个字典类型在隐藏辅助 sheet（{@value #DICT_SHEET_NAME}）
     * 写入其启用字典项的 label 列表（一个 {@code dictTypeCode} 占一列，第 1 行为
     * {@code dictTypeCode} 说明，第 2 行起为 label），再对主表中对应的字典列整列
     * （第 2 行到第 {@code 1 + ImportLimits.MAX_ROW_COUNT} 行）添加引用该辅助 sheet
     * 区域的下拉列表数据校验（design.md Decision 2）。
     *
     * @param workbook    工作簿
     * @param sheet       主表工作表
     * @param configs     启用状态的导入字段配置列表，顺序与主表列顺序一致
     * @param dictColumns {@code fieldCode -> dictTypeCode} 映射
     */
    private void applyDictDropdowns(XSSFWorkbook workbook, XSSFSheet sheet, List<ImportFieldConfigVO> configs,
            Map<String, String> dictColumns) {
        List<String> dictTypeCodes = dictColumns.values().stream().distinct().toList();
        XSSFSheet dictSheet = workbook.createSheet(DICT_SHEET_NAME);

        Map<String, Integer> dictColumnIndexByTypeCode = new LinkedHashMap<>();
        Map<String, Integer> labelCountByTypeCode = new LinkedHashMap<>();
        for (int col = 0; col < dictTypeCodes.size(); col++) {
            String dictTypeCode = dictTypeCodes.get(col);
            dictColumnIndexByTypeCode.put(dictTypeCode, col);
            getOrCreateRow(dictSheet, 0).createCell(col).setCellValue(dictTypeCode);

            List<String> labels = dictImportColumnSupport.getEnabledLabels(dictTypeCode);
            for (int i = 0; i < labels.size(); i++) {
                getOrCreateRow(dictSheet, i + 1).createCell(col).setCellValue(labels.get(i));
            }
            labelCountByTypeCode.put(dictTypeCode, labels.size());
        }
        workbook.setSheetVisibility(workbook.getSheetIndex(dictSheet), SheetVisibility.HIDDEN);

        XSSFDataValidationHelper validationHelper = new XSSFDataValidationHelper(sheet);
        for (int i = 0; i < configs.size(); i++) {
            String dictTypeCode = dictColumns.get(configs.get(i).getFieldCode());
            if (dictTypeCode == null) {
                continue;
            }
            int dictColumnIndex = dictColumnIndexByTypeCode.get(dictTypeCode);
            // 无启用项时至少引用一个空白单元格，呈现为一个可选的空选项，避免公式区域非法（起始行晚于结束行）。
            int lastDataRow = Math.max(labelCountByTypeCode.get(dictTypeCode), 1) + 1;
            String columnLetter = CellReference.convertNumToColString(dictColumnIndex);
            String formula = "'" + DICT_SHEET_NAME + "'!$" + columnLetter + "$2:$" + columnLetter + "$" + lastDataRow;

            DataValidationConstraint constraint = validationHelper.createFormulaListConstraint(formula);
            CellRangeAddressList regions = new CellRangeAddressList(1, ImportLimits.MAX_ROW_COUNT, i, i);
            XSSFDataValidation validation = (XSSFDataValidation) validationHelper.createValidation(constraint, regions);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.setShowErrorBox(true);
            validation.createErrorBox(VALIDATION_ERROR_TITLE, VALIDATION_ERROR_TEXT);
            sheet.addValidationData(validation);
        }
    }

    /**
     * 获取工作表中指定行下标的行对象，不存在时创建。
     *
     * @param sheet    工作表
     * @param rowIndex 行下标（0 开始）
     * @return 行对象
     */
    private Row getOrCreateRow(XSSFSheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }
}
