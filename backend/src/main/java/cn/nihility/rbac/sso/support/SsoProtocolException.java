package cn.nihility.rbac.sso.support;

/**
 * SSO 协议校验专用异常（app-sso-protocol-runtime change design.md Decision 4）：
 * {@link AppProtocolGuard} 在 {@code service}/{@code redirect_uri} 白名单不匹配、协议类型
 * 不符、应用不存在等场景下抛出。CAS/OAuth2 端点整体绕开 {@code GlobalResponseAdvice}/
 * {@code GlobalExceptionHandler} 的 {@code {code,message,data}} 包装路径，本异常须由
 * {@code CasController}/{@code OAuthController} 在本地 catch 后手写响应，不允许向上传播
 * 到 {@code GlobalExceptionHandler}。
 */
public class SsoProtocolException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 提示信息
     */
    public SsoProtocolException(String message) {
        super(message);
    }
}
