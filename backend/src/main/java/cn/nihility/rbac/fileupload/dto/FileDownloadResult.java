package cn.nihility.rbac.fileupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 文件下载结果，携带磁盘文件的二进制内容、原始文件名（供
 * {@code Content-Disposition} 使用）与探测出的媒体类型
 * （add-file-upload change design.md Decision 5）。
 */
@Getter
@Builder
@AllArgsConstructor
public class FileDownloadResult {

    /** 文件二进制内容。 */
    private final byte[] content;

    /** 原始文件名（含后缀）。 */
    private final String originalFileName;

    /**
     * 探测出的媒体类型；优先用 {@link java.nio.file.Files#probeContentType} 探测磁盘文件
     * 得到，探测不出时为 {@code null}，由调用方回退为 {@code application/octet-stream}。
     */
    private final String contentType;
}
