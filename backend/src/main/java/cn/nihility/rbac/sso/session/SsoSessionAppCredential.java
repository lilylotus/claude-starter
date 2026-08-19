package cn.nihility.rbac.sso.session;

/**
 * SSO 会话在某个应用最后一次签发的协议凭证，落库为 Redis {@code sso:session:<token>:apps}
 * Hash 中以 {@code appId} 为字段名的 JSON 值（add-sso-single-logout change design.md
 * Decision 1）。同一 {@code appId} 重复签发时后写覆盖前写，天然保证"最后一次"语义。
 *
 * @param protocol   签发该凭证使用的单点登录协议：{@code CAS} 或 {@code OAUTH2}
 * @param credential 凭证取值：CAS 场景为服务票据（ST-xxx），OAuth2 场景为 access token
 */
public record SsoSessionAppCredential(String protocol, String credential) {
}
