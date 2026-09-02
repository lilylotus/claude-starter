package cn.nihility.rbac.sso.qrcode.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sso.qrcode.constant.QrcodeSessionStatus;
import cn.nihility.rbac.sso.qrcode.dto.QrcodeSessionPayload;
import cn.nihility.rbac.sso.qrcode.service.QrcodeSessionService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link QrcodeSessionServiceImpl} 的测试，起真实 Redis 连接（同项目既有"不做重量级 mock"的
 * 测试风格），覆盖状态机全部合法/非法流转：重复扫码、令牌无效时静默忽略、重复确认已确认状态、
 * 未知令牌确认被拒绝、消费后视为已过期（add-sso-login-methods change tasks.md 6.6）。
 */
@SpringBootTest
class QrcodeSessionServiceImplTest {

    /** 被测服务，真实注入。 */
    @Autowired
    private QrcodeSessionService qrcodeSessionService;

    /** 字符串 Redis 模板，用于测试结束后清理。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例创建的会话令牌，供 {@link #tearDown()} 清理。 */
    private String issuedToken;

    /**
     * 每个用例结束后清理本用例创建的 Redis 记录。
     */
    @AfterEach
    void tearDown() {
        if (issuedToken != null) {
            stringRedisTemplate.delete("sso:qrcode:" + issuedToken);
        }
    }

    /**
     * 创建会话后应处于待扫码状态，且原样保留 {@code redirect}/{@code appId}。
     */
    @Test
    void create_shouldStorePendingSession() {
        issuedToken = qrcodeSessionService.create("http://sso.example.com/api/authn/cas/my-app/login?service=x", "my-app");

        Optional<QrcodeSessionPayload> payloadOpt = qrcodeSessionService.find(issuedToken);
        assertThat(payloadOpt).isPresent();
        assertThat(payloadOpt.get().getStatus()).isEqualTo(QrcodeSessionStatus.PENDING);
        assertThat(payloadOpt.get().getAppId()).isEqualTo("my-app");
        assertThat(payloadOpt.get().isConsumed()).isFalse();
    }

    /**
     * 查询一个不存在的令牌应返回空。
     */
    @Test
    void find_shouldReturnEmpty_whenTokenUnknown() {
        assertThat(qrcodeSessionService.find("unknown-token")).isEmpty();
    }

    /**
     * 待扫码状态下标记扫码，应变为已扫码。
     */
    @Test
    void markScanned_shouldTransitionPendingToScanned() {
        issuedToken = qrcodeSessionService.create("redirect", "app");

        qrcodeSessionService.markScanned(issuedToken);

        assertThat(qrcodeSessionService.find(issuedToken)).get()
                .extracting(QrcodeSessionPayload::getStatus).isEqualTo(QrcodeSessionStatus.SCANNED);
    }

    /**
     * 已扫码状态下重复标记扫码应保持幂等，不抛异常，状态不变。
     */
    @Test
    void markScanned_shouldBeIdempotent_whenAlreadyScanned() {
        issuedToken = qrcodeSessionService.create("redirect", "app");
        qrcodeSessionService.markScanned(issuedToken);

        qrcodeSessionService.markScanned(issuedToken);

        assertThat(qrcodeSessionService.find(issuedToken)).get()
                .extracting(QrcodeSessionPayload::getStatus).isEqualTo(QrcodeSessionStatus.SCANNED);
    }

    /**
     * 令牌不存在时标记扫码应静默忽略，不抛出异常。
     */
    @Test
    void markScanned_shouldBeNoop_whenTokenUnknown() {
        qrcodeSessionService.markScanned("unknown-token");
    }

    /**
     * 待扫码状态下确认登录，应变为已确认并绑定 userId。
     */
    @Test
    void confirm_shouldSucceed_whenPending() {
        issuedToken = qrcodeSessionService.create("redirect", "app");

        qrcodeSessionService.confirm(issuedToken, 100L);

        Optional<QrcodeSessionPayload> payloadOpt = qrcodeSessionService.find(issuedToken);
        assertThat(payloadOpt).isPresent();
        assertThat(payloadOpt.get().getStatus()).isEqualTo(QrcodeSessionStatus.CONFIRMED);
        assertThat(payloadOpt.get().getUserId()).isEqualTo(100L);
    }

    /**
     * 已扫码状态下确认登录同样应成功。
     */
    @Test
    void confirm_shouldSucceed_whenScanned() {
        issuedToken = qrcodeSessionService.create("redirect", "app");
        qrcodeSessionService.markScanned(issuedToken);

        qrcodeSessionService.confirm(issuedToken, 101L);

        assertThat(qrcodeSessionService.find(issuedToken)).get()
                .extracting(QrcodeSessionPayload::getStatus).isEqualTo(QrcodeSessionStatus.CONFIRMED);
    }

    /**
     * 令牌不存在时确认登录应被拒绝。
     */
    @Test
    void confirm_shouldReject_whenTokenUnknown() {
        assertThatThrownBy(() -> qrcodeSessionService.confirm("unknown-token", 1L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 已处于已确认状态时再次确认应被拒绝，防止重复确认覆盖已绑定的 userId。
     */
    @Test
    void confirm_shouldReject_whenAlreadyConfirmed() {
        issuedToken = qrcodeSessionService.create("redirect", "app");
        qrcodeSessionService.confirm(issuedToken, 100L);

        assertThatThrownBy(() -> qrcodeSessionService.confirm(issuedToken, 200L))
                .isInstanceOf(BusinessException.class);
        assertThat(qrcodeSessionService.find(issuedToken)).get()
                .extracting(QrcodeSessionPayload::getUserId).isEqualTo(100L);
    }

    /**
     * 标记已消费后，该会话应保留短 TTL 且 {@code consumed=true}，供调用方判断"已确认但已被
     * 消费"应对外展示为已过期，不重复签发会话。
     */
    @Test
    void markConsumed_shouldSetConsumedFlag() {
        issuedToken = qrcodeSessionService.create("redirect", "app");
        qrcodeSessionService.confirm(issuedToken, 100L);
        QrcodeSessionPayload payload = qrcodeSessionService.find(issuedToken).orElseThrow();

        qrcodeSessionService.markConsumed(issuedToken, payload);

        assertThat(qrcodeSessionService.find(issuedToken)).get()
                .extracting(QrcodeSessionPayload::isConsumed).isEqualTo(true);
    }
}
