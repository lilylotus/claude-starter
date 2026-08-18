package cn.nihility.rbac.sso.oauth.dto;

/**
 * OAuth2 access token 签发时绑定的载荷，落库为 Redis {@code oauth:token:<token>} 键的
 * JSON 值（app-sso-protocol-runtime change design.md Decision 6）。
 *
 * @param clientId 签发该 access token 的 OAuth2 client_id
 * @param userId   该 access token 绑定的用户 id
 * @param scope    授权范围，本实现不做真正的权限范围过滤，原样透传
 */
public record OAuthTokenPayload(String clientId, Long userId, String scope) {
}
