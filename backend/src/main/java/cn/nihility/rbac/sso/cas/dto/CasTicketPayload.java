package cn.nihility.rbac.sso.cas.dto;

/**
 * CAS 服务票据（ST）签发时绑定的载荷，落库为 Redis {@code cas:st:<ticket>} 键的 JSON 值
 * （app-sso-protocol-runtime change design.md Decision 5）。
 *
 * @param appId         签发该票据的应用对外标识
 * @param service       签发该票据时校验通过的 {@code service} 参数
 * @param userId        该票据绑定的用户 id
 * @param sessionToken  签发该票据时当前浏览器持有的 SSO 会话令牌（原始令牌，非哈希值），
 *                      供 {@code serviceValidate} 记录 SSO 协议调用日志时计算会话标识使用
 *                      （add-sso-protocol-access-log change design.md Decision 6）
 */
public record CasTicketPayload(String appId, String service, Long userId, String sessionToken) {
}
