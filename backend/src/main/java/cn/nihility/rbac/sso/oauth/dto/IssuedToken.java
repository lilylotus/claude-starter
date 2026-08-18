package cn.nihility.rbac.sso.oauth.dto;

/**
 * {@code grant_type=authorization_code} 授权成功后同时签发的 access token 与
 * refresh token（app-sso-protocol-runtime change design.md Decision 6）。
 *
 * @param accessToken  access token
 * @param refreshToken refresh token
 * @param expiresIn    access token 有效期（秒）
 */
public record IssuedToken(String accessToken, String refreshToken, long expiresIn) {
}
