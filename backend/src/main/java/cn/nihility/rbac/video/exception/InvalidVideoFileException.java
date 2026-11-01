package cn.nihility.rbac.video.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 上传文件后缀不在白名单内，或文件头魔数与后缀对应格式不一致时抛出该异常
 * （add-video-management change design.md Decision 2）。
 */
public class InvalidVideoFileException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     */
    public InvalidVideoFileException() {
        super("仅支持 mp4/webm/ogg 格式的视频");
    }
}
