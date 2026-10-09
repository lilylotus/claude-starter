package cn.nihility.rbac.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 批量上传图片中单个文件的处理结果（add-image-gallery change tasks.md 12.1 / design.md
 * Decision 11）：一个文件失败不影响其它文件，整次批量请求返回每个文件各自的成功/失败结果。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageBatchUploadResultVO {

    /** 原始文件名（含后缀），用于前端在结果列表里标识是哪个文件。 */
    @Schema(description = "原始文件名（含后缀）")
    private String originalFileName;

    /** 该文件是否上传成功。 */
    @Schema(description = "是否上传成功")
    private boolean success;

    /** 成功时的新增记录视图对象，失败时为 {@code null}。 */
    @Schema(description = "成功时的新增记录，失败时为 null")
    private ImageVO image;

    /** 失败原因，成功时为 {@code null}。 */
    @Schema(description = "失败原因，成功时为 null")
    private String message;
}
