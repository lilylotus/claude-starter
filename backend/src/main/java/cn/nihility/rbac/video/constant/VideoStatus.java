package cn.nihility.rbac.video.constant;

/**
 * 视频记录状态常量，只有"正常/已删除"两种语义，与 {@code ImageStatus} 取值对齐
 * （add-video-management change design.md Decision 1）。
 */
public final class VideoStatus {

    /** 正常。 */
    public static final int NORMAL = 2000;

    /** 已删除（逻辑删除）。 */
    public static final int DELETED = -1000;

    /**
     * 工具类不允许实例化。
     */
    private VideoStatus() {
    }
}
