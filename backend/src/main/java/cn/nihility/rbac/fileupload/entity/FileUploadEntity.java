package cn.nihility.rbac.fileupload.entity;

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
 * 文件上传记录持久化实体，对应表 {@code tab_file_upload}（add-file-upload change
 * design.md Decision 2）。保存文件名与原始文件名解耦：{@code storedFileName} 是磁盘
 * 保存文件名（随机字符串 + 原始后缀），{@code originalFileName} 仅用于展示与下载时的
 * {@code Content-Disposition} 文件名，不参与磁盘路径拼接，避免路径穿越。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_file_upload")
public class FileUploadEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名（含后缀）。 */
    private String originalFileName;

    /** 保存在磁盘目录中的文件名（UUID 去横线 + 原始后缀）。 */
    private String storedFileName;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 状态：2000=正常，3000=失效，见 {@code FileUploadStatus}。 */
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
