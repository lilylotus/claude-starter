package cn.nihility.rbac.fileupload.service;

import cn.nihility.rbac.fileupload.dto.FileDownloadResult;
import cn.nihility.rbac.fileupload.dto.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传/下载业务逻辑接口（add-file-upload change design.md Decision 1）。
 */
public interface FileUploadService {

    /**
     * 上传文件：保存到磁盘的文件保存根目录下，保存文件名使用随机字符串加原始文件后缀
     * 生成，并新增一条文件上传记录。
     *
     * @param file 待上传的文件
     * @return 新增记录的视图对象
     */
    FileUploadVO upload(MultipartFile file);

    /**
     * 按文件上传记录 id 下载文件：记录不存在时抛出 {@code FileRecordNotFoundException}，
     * 记录状态非"正常"时抛出 {@code FileDisabledException}，均通过后从磁盘读取文件内容。
     *
     * @param id 文件上传记录 id
     * @return 文件下载结果
     */
    FileDownloadResult download(Long id);
}
