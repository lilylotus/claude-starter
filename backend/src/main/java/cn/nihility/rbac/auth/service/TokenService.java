package cn.nihility.rbac.auth.service;

import cn.nihility.rbac.auth.dto.TokenPair;
import java.util.Optional;

/**
 * Redis 会话令牌业务逻辑接口：签发 access-key/refresh-key、校验 access-key、按 refresh-key
 * 刷新 access-key（password-login-auth change design.md Decision 3）。
 */
public interface TokenService {

    /**
     * 为指定用户签发一对新的 access-key/refresh-key 并写入 Redis 会话记录；同一用户重新
     * 登录会整体覆盖此前的会话记录（不强制踢旧会话，见 design.md Risks/Trade-offs）。
     *
     * @param userId 用户 id
     * @return 签发的令牌对
     */
    TokenPair issue(Long userId);

    /**
     * 校验 access-key 是否对应一个未过期的有效会话：反查 userId 后回读该用户的会话 Hash，
     * 确认其中记录的 accessKey 与本次请求携带的一致且未过期。
     *
     * @param accessKey 访问令牌
     * @return 校验通过时返回对应的用户 id，否则返回空
     */
    Optional<Long> verifyAccessKey(String accessKey);

    /**
     * 按 refresh-key 换取新的 access-key，旧 access-key 立即失效（下一次身份校验时因
     * 会话 Hash 中的 accessKey 已不一致而被判定失效）；refresh-key 保持不变。
     *
     * @param refreshKey 刷新令牌
     * @return 包含新 access-key 的令牌对
     */
    TokenPair refresh(String refreshKey);
}
