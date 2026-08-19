package cn.nihility.rbac.sso.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.sso.oauth.dto.IssuedToken;
import cn.nihility.rbac.sso.oauth.dto.OAuthCodePayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthRefreshPayload;
import cn.nihility.rbac.sso.oauth.dto.OAuthTokenPayload;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link OAuthTokenService} 的测试，起真实 Redis 连接，覆盖授权码签发/一次性消费、
 * access token 签发校验/过期、refresh token 签发/校验/过期，以及"刷新后 refresh token
 * 轮转（旧值一次性消费失效、签发新值拥有完整有效期）"（tasks.md 7.2，
 * add-sso-single-logout change design.md Decision 6）。
 */
@SpringBootTest
class OAuthTokenServiceTest {

    /** 被测服务，真实注入（依赖真实 Redis 连接）。 */
    @Autowired
    private OAuthTokenService oAuthTokenService;

    /** 字符串 Redis 模板，用于测试内直接操作 Redis（模拟过期）。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 授权码应能成功消费一次，且携带正确的签发载荷；同一授权码再次消费应返回空（一次性消费）。
     */
    @Test
    void consumeCode_shouldSucceedOnce_thenFailOnSecondConsume() {
        String code = oAuthTokenService.issueCode("client-1", "https://partner.example.com/cb", 20L, "profile",
                "sso-session-1");

        Optional<OAuthCodePayload> first = oAuthTokenService.consumeCode(code);
        assertThat(first).isPresent();
        assertThat(first.get().clientId()).isEqualTo("client-1");
        assertThat(first.get().redirectUri()).isEqualTo("https://partner.example.com/cb");
        assertThat(first.get().userId()).isEqualTo(20L);
        assertThat(first.get().scope()).isEqualTo("profile");
        assertThat(first.get().ssoSessionToken()).isEqualTo("sso-session-1");

        Optional<OAuthCodePayload> second = oAuthTokenService.consumeCode(code);
        assertThat(second).isEmpty();
    }

    /**
     * 消费一个从未签发过的授权码应返回空。
     */
    @Test
    void consumeCode_shouldReturnEmpty_whenCodeUnknown() {
        assertThat(oAuthTokenService.consumeCode("unknown-code")).isEmpty();
    }

    /**
     * 授权码过期后应消费失败。
     */
    @Test
    void consumeCode_shouldReturnEmpty_afterExpire() throws InterruptedException {
        String code = oAuthTokenService.issueCode("client-1", "https://partner.example.com/cb", 20L, null, null);
        stringRedisTemplate.expire("oauth:code:" + code, 50, TimeUnit.MILLISECONDS);
        Thread.sleep(200);

        assertThat(oAuthTokenService.consumeCode(code)).isEmpty();
    }

    /**
     * {@code authorization_code} 场景应同时签发 access token 与 refresh token，且均可被
     * 各自的校验方法命中，载荷内容与签发参数一致。
     */
    @Test
    void issueAccessTokenWithRefresh_shouldIssueBothVerifiableTokens() {
        IssuedToken issued = oAuthTokenService.issueAccessTokenWithRefresh("client-2", 21L, "profile", null);

        assertThat(issued.accessToken()).isNotBlank();
        assertThat(issued.refreshToken()).isNotBlank();
        assertThat(issued.accessToken()).isNotEqualTo(issued.refreshToken());
        assertThat(issued.expiresIn()).isGreaterThan(0);

        Optional<OAuthTokenPayload> tokenPayload = oAuthTokenService.verifyAccessToken(issued.accessToken());
        assertThat(tokenPayload).isPresent();
        assertThat(tokenPayload.get().clientId()).isEqualTo("client-2");
        assertThat(tokenPayload.get().userId()).isEqualTo(21L);
        assertThat(tokenPayload.get().scope()).isEqualTo("profile");

        Optional<OAuthRefreshPayload> refreshPayload = oAuthTokenService.verifyRefreshToken(issued.refreshToken());
        assertThat(refreshPayload).isPresent();
        assertThat(refreshPayload.get().clientId()).isEqualTo("client-2");
        assertThat(refreshPayload.get().userId()).isEqualTo(21L);
    }

    /**
     * {@code ssoSessionToken} 非空时，应在会话-应用凭证映射 Hash 中登记该应用签发的
     * access token（add-sso-single-logout change tasks.md 2.4）。
     */
    @Test
    void issueAccessTokenWithRefresh_shouldRecordAppCredential_whenSessionTokenPresent() {
        String sessionToken = "sso-session-2";
        IssuedToken issued = oAuthTokenService.issueAccessTokenWithRefresh("client-2b", 21L, "profile", sessionToken);

        Object raw = stringRedisTemplate.opsForHash().get("sso:session:" + sessionToken + ":apps", "client-2b");
        assertThat(raw).isNotNull();
        assertThat(raw.toString()).contains("OAUTH2").contains(issued.accessToken());

        stringRedisTemplate.delete("sso:session:" + sessionToken + ":apps");
    }

    /**
     * 校验一个未曾签发过的 access token 应返回空。
     */
    @Test
    void verifyAccessToken_shouldReturnEmpty_whenTokenUnknown() {
        assertThat(oAuthTokenService.verifyAccessToken("unknown-access-token")).isEmpty();
    }

    /**
     * access token 过期后应校验失败。
     */
    @Test
    void verifyAccessToken_shouldReturnEmpty_afterExpire() throws InterruptedException {
        IssuedToken issued = oAuthTokenService.issueAccessTokenWithRefresh("client-3", 22L, null, null);
        stringRedisTemplate.expire("oauth:token:" + issued.accessToken(), 50, TimeUnit.MILLISECONDS);
        Thread.sleep(200);

        assertThat(oAuthTokenService.verifyAccessToken(issued.accessToken())).isEmpty();
    }

    /**
     * 校验一个未曾签发过的 refresh token 应返回空。
     */
    @Test
    void verifyRefreshToken_shouldReturnEmpty_whenTokenUnknown() {
        assertThat(oAuthTokenService.verifyRefreshToken("unknown-refresh-token")).isEmpty();
    }

    /**
     * {@code refresh_token} 授权类型刷新后，应签发一个不同于原 access token 的新 access
     * token 与一个不同于旧值的新 refresh token；旧 refresh token 应被立即删除（一次性消费，
     * 不可再用于下一次刷新），新 refresh token 应拥有完整的配置有效期
     * （add-sso-single-logout change design.md Decision 6）。
     */
    @Test
    void rotateAccessAndRefreshToken_shouldRotateRefreshToken_andInvalidateOldValue() {
        IssuedToken issued = oAuthTokenService.issueAccessTokenWithRefresh("client-4", 23L, "profile", null);

        IssuedToken rotated = oAuthTokenService.rotateAccessAndRefreshToken(issued.refreshToken(), "client-4", 23L,
                "profile");

        assertThat(rotated.accessToken()).isNotEqualTo(issued.accessToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(issued.refreshToken());
        assertThat(oAuthTokenService.verifyAccessToken(rotated.accessToken())).isPresent();
        // 旧 refresh token 应已被删除（一次性消费失效）
        assertThat(oAuthTokenService.verifyRefreshToken(issued.refreshToken())).isEmpty();
        // 新 refresh token 应可继续校验通过，且拥有完整的配置有效期
        assertThat(oAuthTokenService.verifyRefreshToken(rotated.refreshToken())).isPresent();
        Long ttl = stringRedisTemplate.getExpire("oauth:refresh:" + rotated.refreshToken(), TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0);
    }
}
