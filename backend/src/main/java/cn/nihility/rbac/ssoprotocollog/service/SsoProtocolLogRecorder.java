package cn.nihility.rbac.ssoprotocollog.service;

/**
 * SSO 协议调用记录手动记录入口，供 {@code CasController}、{@code OAuthController}、
 * {@code SsoLogoutController}、{@code SsoLogoutExecutor} 在 CAS/OAuth2.0 协议运行时端点
 * （含全局单点登出接口）的每一个成功/失败分支显式调用，不通过 Spring AOP 切面或注解驱动的
 * 方式自动触发（同 {@code LoginLogRecorder} 既有模式）。
 *
 * <p>客户端 IP、User-Agent 均不作为方法入参，由实现内部自动解析当前 HTTP 请求上下文获取，
 * 调用方无需关心这些细节（add-sso-protocol-access-log change design.md Decision 2）。
 */
public interface SsoProtocolLogRecorder {

    /**
     * 记录一次调用成功。
     *
     * @param protocol  协议类型：{@code CAS}/{@code OAUTH2}
     * @param eventType 事件类型，见 {@code SsoProtocolLogEventType}
     * @param appId     原始 appId/client_id 参数值，即使解析不到应用也保留
     * @param appRefId  解析出的应用 id，解析不到应用时为 {@code null}
     * @param userId    关联的用户 id，处理链路尚未解析出用户身份时为 {@code null}
     * @param sessionId 本次调用所属 SSO 会话标识（会话令牌的 SHA-256 摘要），处理链路尚未
     *                  解析出会话时为 {@code null}
     */
    void recordSuccess(String protocol, String eventType, String appId, Long appRefId, Long userId,
            String sessionId);

    /**
     * 记录一次调用失败。
     *
     * @param protocol   协议类型：{@code CAS}/{@code OAUTH2}/{@code NONE}（应用不存在或未开启
     *                   协议时的兜底值）
     * @param eventType  事件类型，见 {@code SsoProtocolLogEventType}
     * @param appId      原始 appId/client_id 参数值，即使解析不到应用也保留
     * @param appRefId   解析出的应用 id，解析不到应用时为 {@code null}
     * @param userId     关联的用户 id：只要处理链路已经解析出了用户 id（即使本次调用最终判定
     *                   为失败），也 SHALL 使用该已解析到的值，不因失败就丢弃已掌握的身份信息；
     *                   处理链路在拿到用户 id 之前就已经失败的分支为 {@code null}
     * @param sessionId  本次调用所属 SSO 会话标识，与 {@code userId} 遵循相同的"能拿到就填、
     *                   不因失败丢弃"原则
     * @param failReason 失败原因文案
     */
    void recordFailure(String protocol, String eventType, String appId, Long appRefId, Long userId, String sessionId,
            String failReason);
}
