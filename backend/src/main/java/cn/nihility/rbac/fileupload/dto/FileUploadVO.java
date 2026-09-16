package cn.nihility.rbac.fileupload.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 文件上传成功后的返回视图对象（add-file-upload change design.md Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {

    /** 文件上传记录 id，下载接口按该 id 取回文件。 */
    private Long id;

    /** 原始文件名（含后缀）。 */
    private String originalFileName;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 状态：2000=正常，3000=失效。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
