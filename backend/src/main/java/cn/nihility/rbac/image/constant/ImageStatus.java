package cn.nihility.rbac.image.constant;

/**
 * 图片记录状态常量。本表没有"停用"这个中间状态，只有"正常/已删除"两种语义，与
 * {@code MenuStatus.DELETED} 的 {@code -1000} 取值含义对齐（add-image-gallery change
 * design.md Decision 2）。
 */
public final class ImageStatus {

    /** 正常。 */
    public static final int NORMAL = 2000;

    /** 已删除（逻辑删除）。 */
    public static final int DELETED = -1000;

    /**
     * 工具类不允许实例化。
     */
    private ImageStatus() {
    }
}
