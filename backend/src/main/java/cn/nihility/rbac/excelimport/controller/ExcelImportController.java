package cn.nihility.rbac.excelimport.controller;

import cn.nihility.rbac.excelimport.dto.ImportResultVO;
import cn.nihility.rbac.excelimport.service.BatchImportService;
import cn.nihility.rbac.excelimport.service.ImportTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 批量导入接口：按业务对象类型下载导入模板、上传 Excel 文件批量导入组织/
 * 用户/任职/应用四类主数据。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Excel 批量导入", description = "组织/用户/任职/应用四类业务对象的导入模板下载与批量导入接口")
public class ExcelImportController {

    /** Excel 文件的媒体类型（{@code .xlsx}）。 */
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** Excel 导入模板生成业务逻辑接口。 */
    private final ImportTemplateService importTemplateService;

    /** Excel 批量导入业务逻辑接口。 */
    private final BatchImportService batchImportService;

    /**
     * 下载指定业务对象类型的 Excel 导入模板。
     *
     * @param bizType 业务对象类型：ORG/USER/POSITION/APP
     * @return {@code .xlsx} 文件流
     */
    @Operation(summary = "下载 Excel 导入模板", description = "按业务对象类型生成 .xlsx 模板，表头按显示序号升序排列，必填表头加粗标红")
    @GetMapping("/api/excel-import/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP", required = true)
            @RequestParam String bizType) {
        byte[] content = importTemplateService.generateTemplate(bizType);
        String filename = bizType + "-import-template.xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(content);
    }

    /**
     * 按业务对象类型批量导入 Excel 文件。
     *
     * @param bizType 业务对象类型：ORG/USER/POSITION/APP
     * @param file    待导入的 {@code .xlsx} 文件
     * @return 批量导入结果，含成功条数与失败明细
     */
    @Operation(summary = "批量导入 Excel", description = "解析首行表头匹配启用的导入字段配置，必填表头缺失时整体拒绝；表头齐全时逐行独立"
            + "处理，按主键列匹配已有记录（零条新增、一条更新、多条判定失败），单行失败不影响其余行，返回成功条数与失败明细")
    @PostMapping(value = "/api/excel-import/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultVO batchImport(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP", required = true)
            @RequestParam String bizType,
            @Parameter(description = "待导入的 .xlsx 文件", required = true)
            @RequestPart("file") MultipartFile file) {
        return batchImportService.importExcel(bizType, file);
    }
}
