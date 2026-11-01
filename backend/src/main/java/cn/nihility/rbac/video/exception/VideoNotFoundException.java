package cn.nihility.rbac.video.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 按 id 访问/删除视频记录时，记录不存在或已被逻辑删除时抛出该异常。
 */
public class VideoNotFoundException extends BusinessException {

    /** 记录不存在的状态码。 */
    private static final int CODE = 404;

    /**
     * 使用默认提示信息构造异常。
     */
    public VideoNotFoundException() {
        super(CODE, "视频不存在");
    }
}
