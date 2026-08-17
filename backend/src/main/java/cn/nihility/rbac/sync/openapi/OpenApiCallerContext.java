package cn.nihility.rbac.sync.openapi;

import cn.nihility.rbac.app.entity.AppConfigEntity;

/**
 * 线程级"当前拉取请求所属应用"标记工具类，风格对齐
 * {@code cn.nihility.rbac.auth.context.CurrentUserContext}：
 * {@code OpenApiSignInterceptor} 校验 {@code appKey} 请求头（及 {@code needSign=true} 时的
 * 签名）通过后调用 {@link #set(AppConfigEntity)} 标记当前请求所属的应用配置，供
 * {@code SyncPullService} 读取（app-sync-notify-pull-api change design.md Decision 9）。
 * 调用方必须在请求结束后调用 {@link #clear()}，避免容器线程池复用时下一次请求误继承。
 */
public final class OpenApiCallerContext {

    /** 线程级当前请求所属的应用对外接口配置，未标记时为 {@code null}。 */
    private static final ThreadLocal<AppConfigEntity> HOLDER = new ThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private OpenApiCallerContext() {
    }

    /**
     * 标记当前线程正在处理的拉取请求所属的应用对外接口配置。
     *
     * @param appConfig 应用对外接口配置
     */
    public static void set(AppConfigEntity appConfig) {
        HOLDER.set(appConfig);
    }

    /**
     * 读取当前线程标记的应用对外接口配置。
     *
     * @return 应用对外接口配置，未标记时返回 {@code null}
     */
    public static AppConfigEntity get() {
        return HOLDER.get();
    }

    /**
     * 读取当前线程标记的应用 id（{@code tab_app.id}）。
     *
     * @return 应用 id，未标记时返回 {@code null}
     */
    public static Long getAppRefId() {
        AppConfigEntity appConfig = HOLDER.get();
        return appConfig != null ? appConfig.getAppRefId() : null;
    }

    /**
     * 清除当前线程的标记。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
