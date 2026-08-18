package cn.nihility.rbac.sso.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link SsoSessionService} 的测试，起真实 Redis 连接（同项目既有"不做重量级 mock"的测试
 * 风格），覆盖签发/校验/清除/过期后校验失败（tasks.md 7.1）。
 */
@SpringBootTest
class SsoSessionServiceTest {

    /** 被测服务，真实注入（依赖真实 Redis 连接）。 */
    @Autowired
    private SsoSessionService ssoSessionService;

    /** 字符串 Redis 模板，用于测试内直接操作 Redis（模拟过期、清理测试数据）。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例签发的会话令牌，用于测试结束后清理。 */
    private String issuedToken;

    /**
     * 每个用例结束后清理本用例签发的 Redis 记录，避免污染后续用例/占用 Redis 空间。
     */
    @AfterEach
    void cleanup() {
        if (issuedToken != null) {
            stringRedisTemplate.delete("sso:session:" + issuedToken);
        }
    }

    /**
     * 签发一个 SSO 会话后，应能通过 {@link SsoSessionService#verify(String)} 校验出对应用户 id。
     */
    @Test
    void issue_shouldMakeTokenVerifiable() {
        issuedToken = ssoSessionService.issue(1001L);

        assertThat(issuedToken).isNotBlank().doesNotContain("-");
        assertThat(ssoSessionService.verify(issuedToken)).contains(1001L);
    }

    /**
     * 校验一个未曾签发过的会话令牌，应返回空。
     */
    @Test
    void verify_shouldReturnEmpty_whenTokenUnknown() {
        assertThat(ssoSessionService.verify("unknown-session-token")).isEmpty();
    }

    /**
     * 清除会话后，该令牌应立即失效。
     */
    @Test
    void revoke_shouldInvalidateToken() {
        issuedToken = ssoSessionService.issue(1002L);

        ssoSessionService.revoke(issuedToken);

        assertThat(ssoSessionService.verify(issuedToken)).isEmpty();
    }

    /**
     * 清除一个不存在的会话令牌应保持幂等，不抛出异常。
     */
    @Test
    void revoke_shouldBeIdempotent_whenTokenUnknown() {
        ssoSessionService.revoke("unknown-session-token");
    }

    /**
     * 会话过期后应校验失败：签发后直接把 Redis 记录的 TTL 改到极短，等待其自然过期后再校验。
     */
    @Test
    void verify_shouldReturnEmpty_afterExpire() throws InterruptedException {
        issuedToken = ssoSessionService.issue(1003L);
        stringRedisTemplate.expire("sso:session:" + issuedToken, 50, TimeUnit.MILLISECONDS);
        Thread.sleep(200);

        assertThat(ssoSessionService.verify(issuedToken)).isEmpty();
    }
}
