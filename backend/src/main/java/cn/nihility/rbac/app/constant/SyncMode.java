package cn.nihility.rbac.app.constant;

/**
 * 应用数据同步方式常量，落库为可读字符串值（而非整型码值），供 {@code tab_app_config.sync_mode}
 * 列与请求校验复用。整个应用只有一份同步方式，不区分组织/用户/应用/字典四个数据域。
 */
public final class SyncMode {

    /** 通知：本系统数据变化时主动回调外部接口（{@code notify_url}）。 */
    public static final String NOTIFY = "NOTIFY";

    /** 拉取：外部系统主动调用本系统接口拉取数据。 */
    public static final String PULL = "PULL";

    /**
     * 工具类不允许实例化。
     */
    private SyncMode() {
    }
}
