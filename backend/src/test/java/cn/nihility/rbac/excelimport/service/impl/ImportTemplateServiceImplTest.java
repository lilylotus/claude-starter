package cn.nihility.rbac.excelimport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ImportTemplateServiceImpl} 的单元测试，重点覆盖：表头列顺序（复用查询结果的
 * 顺序，即按显示序号升序）、必填列表头加粗标红、未配置任何导入字段时拒绝生成、非法
 * 业务对象类型拒绝生成。
 */
@ExtendWith(MockitoExtension.class)
class ImportTemplateServiceImplTest {

    /** 被测服务的导入字段配置业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private ImportFieldConfigService importFieldConfigService;

    /** 被测服务实例。 */
    private ImportTemplateServiceImpl importTemplateService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        importTemplateService = new ImportTemplateServiceImpl(importFieldConfigService);
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
