package cn.nihility.rbac.image.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 按 id 访问原图内容时，对应的图片记录状态为"已删除"时抛出该异常（add-image-gallery
 * change tasks.md 5.2）。
 */
public class ImageDeletedException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     */
    public ImageDeletedException() {
        super("图片已删除，无法访问");
    }
}
