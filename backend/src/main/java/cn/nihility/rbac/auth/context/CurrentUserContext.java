package cn.nihility.rbac.auth.context;

/**
 * 线程级"当前登录用户"标记工具类：{@code IdentityAuthFilter} 校验 {@code identity-token}
 * 通过后调用 {@link #setUserId(Long)} 标记当前请求所属的用户 id，供 controller/service 层
 * （如修改密码、重置密码、审计字段 {@code create_by}/{@code update_by} 填充）读取当前操作人。
 * 调用方必须在 {@code finally} 块中调用 {@link #clear()}，避免容器线程池复用时下一次请求
 * 误继承上一次请求残留的标记。
 */
public final class CurrentUserContext {

    /** 线程级当前登录用户 id，未登录/未标记时为 {@code null}。 */
    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private CurrentUserContext() {
    }

    /**
     * 标记当前线程正在处理的请求所属的登录用户 id。
     *
     * @param userId 用户 id
     */
    public static void setUserId(Long userId) {
        HOLDER.set(userId);
    }

    /**
     * 读取当前线程标记的登录用户 id。
     *
     * @return 用户 id，未标记时返回 {@code null}
     */
    public static Long getUserId() {
        return HOLDER.get();
    }

    /**
     * 清除当前线程的登录用户标记。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
