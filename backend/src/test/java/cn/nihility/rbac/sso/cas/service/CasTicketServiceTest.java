package cn.nihility.rbac.sso.cas.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.sso.cas.dto.CasTicketPayload;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link CasTicketService} 的测试，起真实 Redis 连接，覆盖签发/一次性消费/重复消费失败/
 * 过期失败（tasks.md 7.2），以及签发时同步登记会话-应用凭证映射
 * （add-sso-single-logout change tasks.md 2.2）。
 */
@SpringBootTest
class CasTicketServiceTest {

    /** 被测服务，真实注入（依赖真实 Redis 连接）。 */
    @Autowired
    private CasTicketService casTicketService;

    /** 字符串 Redis 模板，用于测试内直接操作 Redis（模拟过期）。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 签发的票据应以 {@code ST-} 为前缀。
     */
    @Test
    void issue_shouldStartWithTicketPrefix() {
        String ticket = casTicketService.issue("app-1", "https://partner.example.com/cb", 10L, "session-token-1");

        assertThat(ticket).startsWith("ST-");
        casTicketService.consume(ticket);
    }

    /**
     * 合法票据应能成功消费一次，且携带正确的签发载荷；同一票据再次消费应返回空（一次性消费）。
     */
    @Test
    void consume_shouldSucceedOnce_thenFailOnSecondConsume() {
        String ticket = casTicketService.issue("app-1", "https://partner.example.com/cb", 10L, "session-token-2");

        Optional<CasTicketPayload> first = casTicketService.consume(ticket);
        assertThat(first).isPresent();
        assertThat(first.get().appId()).isEqualTo("app-1");
        assertThat(first.get().service()).isEqualTo("https://partner.example.com/cb");
        assertThat(first.get().userId()).isEqualTo(10L);
        assertThat(first.get().sessionToken()).isEqualTo("session-token-2");

        Optional<CasTicketPayload> second = casTicketService.consume(ticket);
        assertThat(second).isEmpty();
    }

    /**
     * 消费一个从未签发过的票据应返回空。
     */
    @Test
    void consume_shouldReturnEmpty_whenTicketUnknown() {
        assertThat(casTicketService.consume("ST-unknown-ticket")).isEmpty();
    }

    /**
     * 票据过期后应消费失败。
     */
    @Test
    void consume_shouldReturnEmpty_afterExpire() throws InterruptedException {
        String ticket = casTicketService.issue("app-1", "https://partner.example.com/cb", 10L, "session-token-3");
        stringRedisTemplate.expire("cas:st:" + ticket, 50, TimeUnit.MILLISECONDS);
        Thread.sleep(200);

        assertThat(casTicketService.consume(ticket)).isEmpty();
    }

    /**
     * 签发票据后，应在会话-应用凭证映射 Hash 中登记该应用最后一次签发的票据
     * （add-sso-single-logout change design.md Decision 1）。
     */
    @Test
    void issue_shouldRecordAppCredential_inSessionAppsHash() {
        String sessionToken = "session-token-4";
        String ticket = casTicketService.issue("app-1", "https://partner.example.com/cb", 10L, sessionToken);

        Object raw = stringRedisTemplate.opsForHash().get("sso:session:" + sessionToken + ":apps", "app-1");
        assertThat(raw).isNotNull();
        assertThat(raw.toString()).contains("CAS").contains(ticket);

        casTicketService.consume(ticket);
        stringRedisTemplate.delete("sso:session:" + sessionToken + ":apps");
    }
}
