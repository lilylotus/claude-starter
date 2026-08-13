package cn.nihility.rbac.sync.notify.constant;

/**
 * 通知发送记录状态常量，对应 {@code tab_app_notify_record.notify_status} 列
 * （app-sync-notify-pull-api change design.md Decision 7）。
 */
public final class NotifyStatus {

    /** 成功。 */
    public static final int SUCCESS = 1;

    /** 失败。 */
    public static final int FAILURE = 2;

    /**
     * 工具类不允许实例化。
     */
    private NotifyStatus() {
    }
}
