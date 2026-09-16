package cn.nihility.rbac.fileupload.controller;

import cn.nihility.rbac.fileupload.dto.FileDownloadResult;
import cn.nihility.rbac.fileupload.dto.FileUploadVO;
import cn.nihility.rbac.fileupload.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传/下载接口（add-file-upload change design.md Decision 5）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "通用文件上传", description = "通用文件上传落盘落库、按记录 id 下载文件")
public class FileUploadController {

    /** 探测不出文件媒体类型时的兜底类型。 */
    private static final MediaType FALLBACK_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    /** 文件上传/下载业务逻辑接口。 */
    private final FileUploadService fileUploadService;

    /**
     * 上传文件。
     *
     * @param file 待上传的文件
     * @return 新增记录的视图对象
     */
    @Operation(summary = "上传文件", description = "接收 multipart/form-data 单文件，保存到磁盘的文件保存根目录下并新增一条文件上传记录")
    @PostMapping(value = "/api/file-upload/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadVO upload(
            @Parameter(description = "待上传的文件", required = true)
            @RequestParam("file") MultipartFile file) {
        return fileUploadService.upload(file);
    }

    /**
     * 按文件上传记录 id 下载文件。
     *
     * @param id 文件上传记录 id
     * @return 文件二进制内容
     */
    @Operation(summary = "下载文件", description = "按文件上传记录 id 下载文件，仅状态为正常的记录允许下载")
    @GetMapping("/api/file-upload/download/{id}")
    public ResponseEntity<byte[]> download(
            @Parameter(description = "文件上传记录 id", required = true)
            @PathVariable Long id) {
        FileDownloadResult result = fileUploadService.download(id);
        MediaType mediaType = StringUtils.hasText(result.getContentType())
                ? MediaType.parseMediaType(result.getContentType()) : FALLBACK_MEDIA_TYPE;
        String encodedFilename = URLEncoder.encode(result.getOriginalFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(result.getContent());
    }
}
