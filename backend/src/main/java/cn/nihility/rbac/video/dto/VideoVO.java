package cn.nihility.rbac.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 视频记录的返回视图对象，不包含磁盘保存文件名 {@code storedFileName}——该字段仅用于
 * 服务端定位磁盘文件，不对外暴露（add-video-management change design.md Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoVO {

    /** 视频记录 id。 */
    @Schema(description = "视频记录 id")
    private Long id;

    /** 原始文件名（含后缀）。 */
    @Schema(description = "原始文件名（含后缀）")
    private String originalFileName;

    /** 文件大小（字节）。 */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /** 内容类型，如 video/mp4。 */
    @Schema(description = "内容类型，如 video/mp4")
    private String contentType;

    /** 上传人（用户 id）。 */
    @Schema(description = "上传人（用户 id）")
    private String createBy;

    /** 上传时间。 */
    @Schema(description = "上传时间")
    private LocalDateTime createTime;
}
