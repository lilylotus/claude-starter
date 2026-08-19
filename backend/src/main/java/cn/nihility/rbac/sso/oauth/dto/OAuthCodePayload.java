package cn.nihility.rbac.sso.oauth.dto;

/**
 * OAuth2 授权码签发时绑定的载荷，落库为 Redis {@code oauth:code:<code>} 键的 JSON 值
 * （app-sso-protocol-runtime change design.md Decision 6）。{@code ssoSessionToken} 用于
 * 把 {@code /oauth2/token} 换取的 AccessToken 关联回签发授权码时（{@code /oauth2/authorize}，
 * 此时浏览器 Cookie 可用）所处的 SSO 会话（add-sso-single-logout change design.md Decision 1
 * "OAuth2 场景的会话归属问题"），供登出时构造回调通知使用。
 *
 * @param clientId         签发该授权码的 OAuth2 client_id
 * @param redirectUri      签发该授权码时校验通过的 {@code redirect_uri} 参数
 * @param userId           该授权码绑定的用户 id
 * @param scope            授权范围，本实现不做真正的权限范围过滤，原样透传
 * @param ssoSessionToken  签发该授权码时当前浏览器持有的 SSO 会话令牌
 */
public record OAuthCodePayload(String clientId, String redirectUri, Long userId, String scope,
        String ssoSessionToken) {
}
