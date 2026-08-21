package cn.nihility.rbac.sso.support;

/**
 * SSO 协议校验专用异常（app-sso-protocol-runtime change design.md Decision 4）：
 * {@link AppProtocolGuard} 在 {@code service}/{@code redirect_uri} 白名单不匹配、协议类型
 * 不符、应用不存在等场景下抛出，{@link AppAccessAuthorizationChecker} 在应用访问授权判定
 * 为不可访问时抛出。CAS/OAuth2 端点整体绕开 {@code GlobalResponseAdvice}/
 * {@code GlobalExceptionHandler} 的 {@code {code,message,data}} 包装路径，本异常须由
 * {@code CasController}/{@code OAuthController} 在本地 catch 后手写响应，不允许向上传播
 * 到 {@code GlobalExceptionHandler}。
 */
public class SsoProtocolException extends RuntimeException {

    /**
     * 拒绝来源的策略 id：仅由应用访问授权判定为"排在最前的候选策略请求控制条件不满足"这一
     * 具体分支拒绝时非空，其余场景（白名单不匹配、人工例外拒绝等）均为 {@code null}
     * （policy-condition-exclusive-priority change design.md Decision）。
     */
    private final Long deniedByPolicyId;

    /**
     * 构造异常，{@code deniedByPolicyId} 默认为 {@code null}，供非策略拒绝场景使用。
     *
     * @param message 提示信息
     */
    public SsoProtocolException(String message) {
        this(message, null);
    }

    /**
     * 构造异常，附带拒绝来源的策略 id。
     *
     * @param message          提示信息
     * @param deniedByPolicyId 拒绝来源的策略 id，非策略拒绝场景传 {@code null}
     */
    public SsoProtocolException(String message, Long deniedByPolicyId) {
        super(message);
        this.deniedByPolicyId = deniedByPolicyId;
    }

    /**
     * 获取拒绝来源的策略 id。
     *
     * @return 拒绝来源的策略 id，非策略拒绝场景为 {@code null}
     */
    public Long getDeniedByPolicyId() {
        return deniedByPolicyId;
    }
}
