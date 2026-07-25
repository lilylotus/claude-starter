package cn.nihility.rbac.excelimport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import cn.nihility.rbac.excelimport.service.support.DictImportColumnSupport;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ImportTemplateServiceImpl} 的单元测试，重点覆盖：表头列顺序（复用查询结果的
 * 顺序，即按显示序号升序）、必填列表头加粗标红、未配置任何导入字段时拒绝生成、非法
 * 业务对象类型拒绝生成，以及字典列下拉数据校验（选项内容/顺序、无启用项时的空下拉、
 * 非字典列不受影响）。
 */
@ExtendWith(MockitoExtension.class)
class ImportTemplateServiceImplTest {

    /** 被测服务的导入字段配置业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private ImportFieldConfigService importFieldConfigService;

    /**
     * 被测服务的字典列支持依赖，使用 Mockito 打桩；未打桩时
     * {@code resolveDictColumns} 默认返回空 Map（Mockito 对 Map 返回类型的默认行为），
     * 因此不涉及字典列的用例无需额外打桩即可保持通过。
     */
    @Mock
    private DictImportColumnSupport dictImportColumnSupport;

    /** 被测服务实例。 */
    private ImportTemplateServiceImpl importTemplateService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        importTemplateService = new ImportTemplateServiceImpl(importFieldConfigService, dictImportColumnSupport);
    }

    /**
     * 生成模板时，业务对象类型非法应拒绝生成。
     */
    @Test
    void generateTemplate_shouldThrowException_whenBizTypeInvalid() {
        assertThatThrownBy(() -> importTemplateService.generateTemplate("INVALID"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 生成模板时，该业务对象类型下没有任何启用的导入字段配置，应拒绝生成。
     */
    @Test
    void generateTemplate_shouldThrowException_whenNoActiveConfigs() {
        when(importFieldConfigService.listActiveByBizType("ORG")).thenReturn(List.of());

        assertThatThrownBy(() -> importTemplateService.generateTemplate("ORG"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 生成的模板首行表头应按查询结果（服务层已按显示序号升序排列）的顺序写入，
     * 必填列的表头单元格应加粗、字体颜色为红色，非必填列不应套用该样式。
     */
    @Test
    void generateTemplate_shouldWriteHeadersInOrder_andBoldRedRequiredColumns() throws Exception {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("code", "组织编码", true, 90),
                buildConfig("name", "组织名称", true, 100),
                buildConfig("remark", "备注", false, 10));
        when(importFieldConfigService.listActiveByBizType("ORG")).thenReturn(configs);

        byte[] content = importTemplateService.generateTemplate("ORG");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Row headerRow = workbook.getSheetAt(0).getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("组织编码");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("组织名称");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("备注");

            assertRequiredStyle(workbook, headerRow.getCell(0), true);
            assertRequiredStyle(workbook, headerRow.getCell(1), true);
            assertRequiredStyle(workbook, headerRow.getCell(2), false);
        }
    }

    /**
     * 字典下拉单选列应生成带下拉数据校验的单元格区域，选项内容与顺序取自
     * {@link DictImportColumnSupport#getEnabledLabels}，且隐藏辅助 sheet
     * （{@code 字典选项}）按 {@code dictTypeCode} 写入 label 列表并被隐藏。
     */
    @Test
    void generateTemplate_shouldAddDropdown_forDictColumn() throws Exception {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("code", "用户编号", true, 100),
                buildConfig("gender", "性别", true, 90));
        when(importFieldConfigService.listActiveByBizType("USER")).thenReturn(configs);
        when(dictImportColumnSupport.resolveDictColumns(configs)).thenReturn(Map.of("gender", "GENDER"));
        when(dictImportColumnSupport.getEnabledLabels("GENDER")).thenReturn(List.of("男", "女"));

        byte[] content = importTemplateService.generateTemplate("USER");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            List<? extends DataValidation> validations = sheet.getDataValidations();
            assertThat(validations).hasSize(1);
            DataValidation validation = validations.get(0);
            assertThat(validation.getValidationConstraint().getFormula1()).isEqualTo("'字典选项'!$A$2:$A$3");
            assertThat(validation.getRegions().getCellRangeAddresses()[0].getFirstColumn()).isEqualTo(1);
            assertThat(validation.getRegions().getCellRangeAddresses()[0].getFirstRow()).isEqualTo(1);

            int dictSheetIndex = workbook.getSheetIndex("字典选项");
            assertThat(dictSheetIndex).isNotEqualTo(-1);
            assertThat(workbook.getSheetVisibility(dictSheetIndex)).isEqualTo(SheetVisibility.HIDDEN);

            XSSFSheet dictSheet = workbook.getSheetAt(dictSheetIndex);
            assertThat(dictSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("GENDER");
            assertThat(dictSheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("男");
            assertThat(dictSheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("女");
        }
    }

    /**
     * 字典类型当前没有任何启用项时，主表对应列仍应生成下拉数据校验，但引用的辅助
     * sheet 区域只包含一个空白单元格（呈现为空选项），不应因为区域"起始行晚于结束
     * 行"而报错。
     */
    @Test
    void generateTemplate_shouldCreateEmptyDropdown_whenNoEnabledOptions() throws Exception {
        List<ImportFieldConfigVO> configs = List.of(buildConfig("gender", "性别", true, 100));
        when(importFieldConfigService.listActiveByBizType("USER")).thenReturn(configs);
        when(dictImportColumnSupport.resolveDictColumns(configs)).thenReturn(Map.of("gender", "GENDER"));
        when(dictImportColumnSupport.getEnabledLabels("GENDER")).thenReturn(List.of());

        byte[] content = importTemplateService.generateTemplate("USER");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            DataValidation validation = sheet.getDataValidations().get(0);
            assertThat(validation.getValidationConstraint().getFormula1()).isEqualTo("'字典选项'!$A$2:$A$2");
        }
    }

    /**
     * 不涉及字典列的场景下，不应生成任何数据校验，也不应新增隐藏辅助 sheet。
     */
    @Test
    void generateTemplate_shouldNotAddDropdown_forNonDictColumn() throws Exception {
        List<ImportFieldConfigVO> configs = List.of(buildConfig("code", "组织编码", true, 90));
        when(importFieldConfigService.listActiveByBizType("ORG")).thenReturn(configs);

        byte[] content = importTemplateService.generateTemplate("ORG");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheetAt(0).getDataValidations()).isEmpty();
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }
    }

    /**
     * 数据区域应按 {@link DictImportColumnSupport#resolveTextFormatFieldCodes} 的判定结果
     * 设置 Excel 文本格式（{@code @}）：POSITION 场景下无关联表单字段定义的固定标识列
     * （{@code __userCode}/{@code __orgCode}）、关联 {@code DICT} 控件类型的
     * {@code positionType} 列均应为文本格式，关联 {@code NUMBER} 控件类型的
     * {@code showOrder} 列不应为文本格式。
     */
    @Test
    void generateTemplate_shouldSetTextFormat_forNonNumberColumns() throws Exception {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("__userCode", "人员编号", true, 100),
                buildConfig("__orgCode", "组织编码", true, 90),
                buildConfig("positionType", "任职类型", false, 80),
                buildConfig("showOrder", "显示序号", false, 70));
        when(importFieldConfigService.listActiveByBizType("POSITION")).thenReturn(configs);
        when(dictImportColumnSupport.resolveTextFormatFieldCodes(configs))
                .thenReturn(Set.of("__userCode", "__orgCode", "positionType"));

        byte[] content = importTemplateService.generateTemplate("POSITION");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getRow(1).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString()).isEqualTo("@");
            // showOrder（NUMBER 控件类型）未命中文本格式集合，数据区域单元格不会被创建，保留 Excel 默认数字格式。
            assertThat(sheet.getRow(1).getCell(3)).isNull();
        }
    }

    /**
     * 断言指定单元格的字体是否符合"必填标红加粗"样式。
     *
     * @param workbook 工作簿，用于按字体索引反查字体对象
     * @param cell     待断言的单元格
     * @param required 是否期望符合必填样式
     */
    private void assertRequiredStyle(XSSFWorkbook workbook, Cell cell, boolean required) {
        Font font = workbook.getFontAt(cell.getCellStyle().getFontIndex());
        if (required) {
            assertThat(font.getBold()).isTrue();
            assertThat(font.getColor()).isEqualTo(IndexedColors.RED.getIndex());
        } else {
            assertThat(font.getBold()).isFalse();
        }
    }

    /**
     * 构造一条测试用的导入字段配置视图对象。
     *
     * @param fieldCode  字段标识
     * @param headerName Excel 表头文字
     * @param required   是否必填
     * @param showOrder  显示序号
     * @return 导入字段配置视图对象
     */
    private ImportFieldConfigVO buildConfig(String fieldCode, String headerName, boolean required, int showOrder) {
        return ImportFieldConfigVO.builder()
                .id((long) showOrder)
                .bizType("ORG")
                .fieldCode(fieldCode)
                .excelHeaderName(headerName)
                .isPrimaryKey(false)
                .isRequired(required)
                .showOrder(showOrder)
                .build();
    }
}
