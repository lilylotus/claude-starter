package cn.nihility.rbac.video.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 上传视频大小超过 {@code rbac.video.max-file-size} 时抛出该异常。
 */
public class VideoFileTooLargeException extends BusinessException {

    /**
     * 构造异常。
     *
     * @param maxSizeText 可读的大小上限文本，如 {@code 2GB}
     */
    public VideoFileTooLargeException(String maxSizeText) {
        super("视频文件大小不能超过 " + maxSizeText);
    }
}
