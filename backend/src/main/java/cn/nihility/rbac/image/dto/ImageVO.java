package cn.nihility.rbac.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 图片记录的返回视图对象（add-image-gallery change tasks.md 4.1），不包含磁盘保存文件名
 * {@code storedFileName}——该字段仅用于服务端定位磁盘文件，不对外暴露。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageVO {

    /** 图片记录 id，原图内容接口按该 id 取回图片字节内容。 */
    @Schema(description = "图片记录 id")
    private Long id;

    /** 原始文件名（含后缀）。 */
    @Schema(description = "原始文件名（含后缀）")
    private String originalFileName;

    /** 文件大小（字节）。 */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /** 图片宽度（像素），供前端预先计算瀑布流布局。 */
    @Schema(description = "图片宽度（像素）")
    private Integer imageWidth;

    /** 图片高度（像素），供前端预先计算瀑布流布局。 */
    @Schema(description = "图片高度（像素）")
    private Integer imageHeight;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
