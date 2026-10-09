package cn.nihility.rbac.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 原图内容读取结果，携带磁盘文件的二进制内容与探测出的媒体类型（add-image-gallery change
 * design.md Decision 5）。与 {@code fileupload} 模块的 {@code FileDownloadResult} 套路
 * 相同但独立实现（design.md Decision 1），且不携带 {@code originalFileName}——原图内容
 * 接口不设置 {@code Content-Disposition}，不需要下载文件名。
 */
@Getter
@Builder
@AllArgsConstructor
public class ImageRawContent {

    /** 图片二进制内容。 */
    private final byte[] content;

    /**
     * 媒体类型；优先用 {@link java.nio.file.Files#probeContentType} 探测磁盘文件得到，
     * 探测不出时按原始后缀名回退为 {@code image/jpeg}/{@code image/png}/{@code image/gif}，
     * 不回退到 {@code application/octet-stream}（design.md Decision 5：必须是
     * {@code image/*} 起始的类型才能被 {@code <img>} 正确渲染）。
     */
    private final String contentType;
}
