package cn.nihility.rbac.fileupload.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 按 id 下载文件时，对应的文件上传记录不存在时抛出该异常
 * （add-file-upload change design.md Decision 6）。
 */
public class FileRecordNotFoundException extends BusinessException {

    /** 记录不存在的状态码，与全局异常处理器中"未匹配路由"场景复用同一语义的 404。 */
    private static final int CODE = 404;

    /**
     * 使用默认提示信息构造异常。
     */
    public FileRecordNotFoundException() {
        super(CODE, "文件不存在");
    }
}
