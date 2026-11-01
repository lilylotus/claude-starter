package cn.nihility.rbac.video.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 流式播放时播放凭证缺失、无效、已过期、与视频不匹配或视频已删除时抛出该异常，由
 * {@code GlobalExceptionHandler} 以 HTTP 403 响应（{@code <video>} 无法读取响应体，
 * 需要真实的错误状态码让播放器触发 error 事件，add-video-management change design.md
 * Decision 3）。
 */
public class InvalidPlayTicketException extends BusinessException {

    /** 无权访问的状态码。 */
    public static final int CODE = 403;

    /**
     * 使用默认提示信息构造异常。
     */
    public InvalidPlayTicketException() {
        super(CODE, "播放凭证无效或已过期");
    }
}
