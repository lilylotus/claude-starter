package cn.nihility.rbac.image.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 上传的文件后缀不在白名单内，或文件内容无法被 {@code ImageIO} 解析为合法图片时抛出该
 * 异常（add-image-gallery change tasks.md 5.3 / design.md Decision 3）。
 */
public class InvalidImageFileException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     */
    public InvalidImageFileException() {
        super("仅支持 jpg/jpeg/png/gif 格式且内容可解析的图片文件");
    }
}
