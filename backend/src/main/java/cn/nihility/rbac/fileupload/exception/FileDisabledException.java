package cn.nihility.rbac.fileupload.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 按 id 下载文件时，对应的文件上传记录状态为"失效"时抛出该异常
 * （add-file-upload change design.md Decision 6）。
 */
public class FileDisabledException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     */
    public FileDisabledException() {
        super("文件已失效，无法下载");
    }
}
