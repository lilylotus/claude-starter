package cn.nihility.rbac.sso.cas.service;

import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.sso.cas.dto.CasTicketPayload;
import cn.nihility.rbac.sso.config.RbacSsoProperties;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CAS 服务票据（ST）业务逻辑（app-sso-protocol-runtime change design.md Decision 5）：以
 * {@code cas:st:<ticket>} 为 Redis key 存储 JSON 载荷，签发后短期有效，
 * {@link #consume(String)} 读后立即删除，保证一次性消费。
 */
@Service
@RequiredArgsConstructor
public class CasTicketService {

    /** 票据字面前缀，贴近真实 CAS 票据命名习惯，非协议强制要求。 */
    private static final String TICKET_PREFIX = "ST-";

    /** Redis key 前缀，完整 key 为该前缀 + 票据。 */
    private static final String REDIS_KEY_PREFIX = "cas:st:";

    /** 字符串 Redis 模板。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** SSO 相关配置：CAS 票据有效期。 */
    private final RbacSsoProperties ssoProperties;

    /**
     * 签发一枚服务票据。
     *
     * @param appId   应用对外标识
     * @param service 校验通过的 {@code service} 参数
     * @param userId  绑定的用户 id
     * @return 服务票据（{@code ST-} + 32 位随机十六进制）
     */
    public String issue(String appId, String service, Long userId) {
        String ticket = TICKET_PREFIX + newHex();
        CasTicketPayload payload = new CasTicketPayload(appId, service, userId);
        stringRedisTemplate.opsForValue().set(REDIS_KEY_PREFIX + ticket, JacksonUtils.toJson(payload),
                ssoProperties.getCasTicketExpireSeconds(), TimeUnit.SECONDS);
        return ticket;
    }

    /**
     * 消费一枚服务票据：读取成功与否均立即删除该票据（无论后续校验是否通过都不可能再被使用）。
     *
     * @param ticket 服务票据
     * @return 票据存在（未过期、未被消费过）时返回其签发载荷，否则返回空
     */
    public Optional<CasTicketPayload> consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return Optional.empty();
        }
        String redisKey = REDIS_KEY_PREFIX + ticket;
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        stringRedisTemplate.delete(redisKey);
        return Optional.of(JacksonUtils.toObj(json, CasTicketPayload.class));
    }

    /**
     * 生成一个不含横线的 UUID 字符串（32 位十六进制），作为票据的随机部分。
     *
     * @return 32 位十六进制字符串
     */
    private String newHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
