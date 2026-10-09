package cn.nihility.rbac.image.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 按 id 访问/删除图片记录时，记录不存在时抛出该异常（add-image-gallery change tasks.md
 * 5.1）。与 {@code fileupload} 模块的异常类独立实现，不复用（design.md Decision 1）。
 */
public class ImageNotFoundException extends BusinessException {

    /** 记录不存在的状态码，与全局异常处理器中"未匹配路由"场景复用同一语义的 404。 */
    private static final int CODE = 404;

    /**
     * 使用默认提示信息构造异常。
     */
    public ImageNotFoundException() {
        super(CODE, "图片不存在");
    }
}
