package cn.nihility.rbac.loginlog.service;

/**
 * 登录日志手动记录入口，供 {@code AuthServiceImpl#login} 在每一次登录尝试（成功或
 * 失败）结束时显式调用，不通过 Spring AOP 切面或注解驱动的方式自动触发。
 *
 * <p>登录发起 IP、登录终端类型、登录操作系统、登录浏览器、原始 User-Agent 均不作为
 * 方法入参，由实现内部自动解析当前 HTTP 请求上下文获取，调用方无需关心这些细节。
 *
 * <p>不带 {@code loginMethod} 参数的两个重载固定按口令登录（{@code LoginMethod#PASSWORD}）
 * 记录，管理端口令登录（{@code AuthServiceImpl}）与 SSO 口令登录（{@code SsoLoginController}）
 * 现有调用无需改动；短信、扫码登录入口须调用带 {@code loginMethod} 参数的重载显式传入对应
 * 登录方式（add-sso-login-methods change design.md Decision 7）。
 */
public interface LoginLogRecorder {

    /**
     * 记录一次登录成功，登录方式固定为口令登录。
     *
     * @param loginAccount 本次登录尝试提交的账号
     * @param userId       登录成功的用户 id
     * @param userName     登录成功的用户姓名
     * @param sessionId    本次建立的 SSO 会话标识（会话令牌的 SHA-256 摘要），仅 SSO
     *                     登录成功时非空，管理端口令登录（不产生 SSO 会话）传 {@code null}
     */
    void recordSuccess(String loginAccount, Long userId, String userName, String sessionId);

    /**
     * 记录一次登录成功。
     *
     * @param loginAccount 本次登录尝试提交的账号
     * @param userId       登录成功的用户 id
     * @param userName     登录成功的用户姓名
     * @param sessionId    本次建立的 SSO 会话标识（会话令牌的 SHA-256 摘要），仅 SSO
     *                     登录成功时非空，管理端口令登录（不产生 SSO 会话）传 {@code null}
     * @param loginMethod  登录方式，见 {@code LoginMethod}
     */
    void recordSuccess(String loginAccount, Long userId, String userName, String sessionId, String loginMethod);

    /**
     * 记录一次登录失败，登录方式固定为口令登录。
     *
     * @param loginAccount 本次登录尝试提交的账号，解密失败时为 {@code null}
     * @param userId       关联的用户 id，账号不存在/解密失败时为 {@code null}
     * @param userName     关联的用户姓名，账号不存在/解密失败时为 {@code null}
     * @param failReason   失败原因文案，见 {@code LoginFailReason}
     */
    void recordFailure(String loginAccount, Long userId, String userName, String failReason);

    /**
     * 记录一次登录失败。
     *
     * @param loginAccount 本次登录尝试提交的账号，解密失败/无法定位账号时为 {@code null}
     * @param userId       关联的用户 id，账号不存在/无法定位账号时为 {@code null}
     * @param userName     关联的用户姓名，账号不存在/无法定位账号时为 {@code null}
     * @param failReason   失败原因文案，见 {@code LoginFailReason}
     * @param loginMethod  登录方式，见 {@code LoginMethod}
     */
    void recordFailure(String loginAccount, Long userId, String userName, String failReason, String loginMethod);
}
