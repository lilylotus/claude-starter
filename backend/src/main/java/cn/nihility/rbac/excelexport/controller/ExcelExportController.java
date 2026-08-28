package cn.nihility.rbac.excelexport.controller;

import cn.nihility.rbac.excelexport.service.ExcelExportService;
import cn.nihility.rbac.excelimport.constant.ImportBizTypes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Excel 导出接口：按业务对象类型导出组织/用户/任职/应用四类主数据为 {@code .xlsx} 文件，
 * 导出内容按当前登录用户的管辖组织范围收窄（用户导出除外，见
 * {@code cn.nihility.rbac.excelexport.service.impl.ExcelExportServiceImpl} 与
 * design.md Decision 2）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Excel 导出", description = "组织/用户/任职/应用四类业务对象按当前登录用户管辖组织范围导出 Excel 的接口")
public class ExcelExportController {

    /** Excel 文件的媒体类型（{@code .xlsx}）。 */
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** 导出文件名的时间戳格式，与导入模板/失败明细文件的命名风格保持一致。 */
    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** Excel 导出业务逻辑接口。 */
    private final ExcelExportService excelExportService;

    /**
     * 按业务对象类型导出 Excel。
     *
     * @param bizType 业务对象类型：ORG/USER/POSITION/APP
     * @return {@code .xlsx} 文件流，文件名形如 {@code 组织导出-202608281530.xlsx}
     */
    @Operation(summary = "导出 Excel", description = "按业务对象类型导出组织/用户/任职/应用主数据；组织/任职/应用按当前登录用户管辖组织"
            + "范围收窄，用户导出不做组织范围收紧；导出列取自该业务对象类型下状态启用且勾选\"是否导出\"的表单字段定义，按显示序号升序"
            + "排列；待导出记录数超过 5 万行时拒绝生成文件")
    @GetMapping("/api/excel-export/download")
    public ResponseEntity<byte[]> download(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP", required = true)
            @RequestParam String bizType) {
        byte[] content = excelExportService.export(bizType);
        String filename = ImportBizTypes.labelOf(bizType) + "导出-"
                + LocalDateTime.now().format(FILENAME_TIMESTAMP_FORMATTER) + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(content);
    }
}
