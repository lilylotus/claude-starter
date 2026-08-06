package cn.nihility.rbac.auth.service;

/**
 * 统一解析"当前登录操作人用户 id"的能力，基于 {@link cn.nihility.rbac.auth.context.CurrentUserContext}
 * 标记的当前登录用户 id，供各业务模块的新增/编辑/启停用/删除等写操作复用，填充
 * {@code create_by}/{@code update_by} 审计字段，以及操作日志的 {@code create_by} 字段，
 * 替代各自硬编码的固定字符串占位符。
 */
public interface CurrentOperatorService {

    /**
     * 解析当前登录操作人的用户 id。
     *
     * @return 当前登录用户的 {@code tab_user.id}
     * @throws IllegalStateException 当前线程不处于已认证的登录会话上下文中（
     *                                {@code CurrentUserContext} 未标记用户 id），这是调用方
     *                                脱离预期调用上下文的编程错误，不做静默降级
     */
    Long resolveUserId();
}
