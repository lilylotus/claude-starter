package cn.nihility.rbac.excelimport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.dto.ImportResultVO;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import cn.nihility.rbac.excelimport.service.support.ImportRowExecutor;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * {@link BatchImportServiceImpl} 的单元测试，重点覆盖：表头匹配、必填表头缺失整体
 * 拒绝、单次上传行数上限、逐行处理成功/失败明细汇总，行处理本身委托
 * {@link ImportRowExecutor}（打桩），不在此处重复验证各业务模块的写入逻辑。
 */
@ExtendWith(MockitoExtension.class)
class BatchImportServiceImplTest {

    /** 被测服务的导入字段配置业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private ImportFieldConfigService importFieldConfigService;

    /** 被测服务的单行处理器依赖，使用 Mockito 打桩。 */
    @Mock
    private ImportRowExecutor importRowExecutor;

    /** 被测服务实例。 */
    private BatchImportServiceImpl batchImportService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        batchImportService = new BatchImportServiceImpl(importFieldConfigService, importRowExecutor);
    }

    /**
     * 业务对象类型非法时应拒绝整个导入请求。
     */
    @Test
    void importExcel_shouldThrowException_whenBizTypeInvalid() {
        MockMultipartFile file = new MockMultipartFile("file", "t.xlsx", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> batchImportService.importExcel("INVALID", file))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 未上传文件（为空）时应拒绝整个导入请求。
     */
    @Test
    void importExcel_shouldThrowException_whenFileEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "t.xlsx", "application/octet-stream", new byte[0]);

        assertThatThrownBy(() -> batchImportService.importExcel("ORG", emptyFile))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 上传文件表头缺少某个必填列对应的表头文字时，应拒绝整个导入请求，不处理任何数据行。
     */
    @Test
    void importExcel_shouldThrowException_whenRequiredHeaderMissing() throws Exception {
        when(importFieldConfigService.listActiveByBizType("ORG")).thenReturn(List.of(
                buildConfig("code", "组织编码", true),
                buildConfig("name", "组织名称", true)));

        byte[] content = buildWorkbook(List.of("组织编码"), List.of(List.of("ORG001")));
        MockMultipartFile file = new MockMultipartFile("file", "t.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        assertThatThrownBy(() -> batchImportService.importExcel("ORG", file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织名称");
    }

    /**
     * 数据行数超过单次上传上限（1000 行）时应拒绝整个导入请求。
     */
    @Test
    void importExcel_shouldThrowException_whenRowCountExceedsLimit() throws Exception {
        when(importFieldConfigService.listActiveByBizType("ORG")).thenReturn(List.of(buildConfig("code", "组织编码", true)));

        List<List<String>> rows = java.util.stream.IntStream.rangeClosed(1, 1001)
                .mapToObj(i -> List.of("ORG" + i))
                .toList();
        byte[] content = buildWorkbook(List.of("组织编码"), rows);
        MockMultipartFile file = new MockMultipartFile("file", "t.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        assertThatThrownBy(() -> batchImportService.importExcel("ORG", file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1000");
    }

    /**
     * 表头齐全、行数未超限时，应逐行委托 {@link ImportRowExecutor} 处理，汇总成功
     * 条数与失败明细（含行号与失败原因）。
     */
    @Test
    void importExcel_shouldReturnSuccessAndFailCounts() throws Exception {
        when(importFieldConfigService.listActiveByBizType("ORG")).thenReturn(List.of(buildConfig("code", "组织编码", true)));

        byte[] content = buildWorkbook(List.of("组织编码"),
                List.of(List.of("ORG001"), List.of("ORG002"), List.of("ORG003")));
        MockMultipartFile file = new MockMultipartFile("file", "t.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        doNothing().when(importRowExecutor).processRow(eq("ORG"), argThatContainsCode("ORG001"), any());
        doThrow(new BusinessException("组织编码[ORG002]已存在"))
                .when(importRowExecutor).processRow(eq("ORG"), argThatContainsCode("ORG002"), any());
        doNothing().when(importRowExecutor).processRow(eq("ORG"), argThatContainsCode("ORG003"), any());

        ImportResultVO result = batchImportService.importExcel("ORG", file);

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailList()).hasSize(1);
        assertThat(result.getFailList().get(0).getReason()).contains("ORG002");
        assertThat(result.getFailList().get(0).getRowNo()).isEqualTo(3);
    }

    /**
     * 构造一个按指定 {@code code} 值匹配行数据 Map 的 Mockito 参数匹配器。
     *
     * @param code 期望匹配的组织编码值
     * @return 参数匹配器
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> argThatContainsCode(String code) {
        return org.mockito.ArgumentMatchers.argThat(map -> map != null && code.equals(map.get("code")));
    }

    /**
     * 构造一条测试用的导入字段配置视图对象。
     *
     * @param fieldCode  字段标识
     * @param headerName Excel 表头文字
     * @param required   是否必填
     * @return 导入字段配置视图对象
     */
    private ImportFieldConfigVO buildConfig(String fieldCode, String headerName, boolean required) {
        return ImportFieldConfigVO.builder()
                .bizType("ORG")
                .fieldCode(fieldCode)
                .excelHeaderName(headerName)
                .isPrimaryKey(true)
                .isRequired(required)
                .showOrder(0)
                .build();
    }

    /**
     * 构造一个内存中的 {@code .xlsx} 工作簿字节数组，首行为表头，其余行为数据。
     *
     * @param headers 表头文字列表
     * @param rows    数据行列表，每行与 {@code headers} 等长
     * @return {@code .xlsx} 文件字节数组
     */
    private byte[] buildWorkbook(List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("sheet1");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row dataRow = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    dataRow.createCell(c).setCellValue(values.get(c));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
