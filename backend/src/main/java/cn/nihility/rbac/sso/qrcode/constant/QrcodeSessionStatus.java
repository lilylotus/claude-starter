package cn.nihility.rbac.sso.qrcode.constant;

/**
 * 二维码登录会话状态常量（add-sso-login-methods change design.md Decision 5）。
 * {@code EXPIRED} 是只对外暴露的虚拟状态：token 不存在、已过期、或内部已标记为"已消费"
 * 时统一对外展示为 {@code EXPIRED}，不额外暴露"已消费"这个内部状态。
 */
public final class QrcodeSessionStatus {

    /** 待扫码：会话已创建，尚未被手机浏览器扫码标记。 */
    public static final String PENDING = "PENDING";

    /** 已扫码待确认：手机浏览器已打开确认页并标记扫码，尚未点击确认登录。 */
    public static final String SCANNED = "SCANNED";

    /** 已确认：手机浏览器已完成确认登录，等待 PC 端下一次轮询签发会话。 */
    public static final String CONFIRMED = "CONFIRMED";

    /** 已过期/不存在/已被消费，仅对外查询接口使用，不落库存储该取值本身。 */
    public static final String EXPIRED = "EXPIRED";

    /**
     * 工具类不允许实例化。
     */
    private QrcodeSessionStatus() {
    }
}
