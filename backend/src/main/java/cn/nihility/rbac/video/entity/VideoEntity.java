package cn.nihility.rbac.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 视频记录持久化实体，对应表 {@code tab_video}（add-video-management change design.md
 * Decision 1）。{@code storedFileName} 是磁盘保存文件名（随机字符串 + 小写原始后缀），
 * {@code originalFileName} 仅用于展示，不参与磁盘路径拼接。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_video")
public class VideoEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名（含后缀），仅用于展示。 */
    private String originalFileName;

    /** 磁盘保存文件名（UUID 去横线 + 小写原始后缀）。 */
    private String storedFileName;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 内容类型，按后缀确定，如 {@code video/mp4}。 */
    private String contentType;

    /** 状态：2000=正常，-1000=已删除，见 {@code VideoStatus}。 */
    private Integer status;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
