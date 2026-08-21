package cn.nihility.rbac.sso.oauth.dto;

/**
 * OAuth2 refresh token 签发时绑定的载荷，落库为 Redis {@code oauth:refresh:<token>} 键的
 * JSON 值（app-sso-protocol-runtime change design.md Decision 6）。refresh token 不轮转，
 * 校验成功后本载荷不会被删除或替换。
 *
 * @param clientId      签发该 refresh token 的 OAuth2 client_id
 * @param userId        该 refresh token 绑定的用户 id
 * @param scope         授权范围，本实现不做真正的权限范围过滤，原样透传
 * @param sessionToken  签发该 refresh token 时关联的 SSO 会话令牌（原始令牌，非哈希值），
 *                      刷新轮转时延续传给新签发的 refresh token/access token，确保刷新链路
 *                      始终关联同一个会话标识（add-sso-protocol-access-log change design.md
 *                      Decision 6）
 */
public record OAuthRefreshPayload(String clientId, Long userId, String scope, String sessionToken) {
}
