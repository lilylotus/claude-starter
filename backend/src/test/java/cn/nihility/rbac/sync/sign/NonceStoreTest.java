package cn.nihility.rbac.sync.sign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link NonceStore} 的单元测试：首次出现放行，时效窗口内重复出现判定为重放，
 * 不同 AccessKey 下相同 nonce 互不影响。
 */
class NonceStoreTest {

    /** 被测实例。 */
    private final NonceStore nonceStore = new NonceStore();

    /**
     * 首次出现的 nonce 应登记成功（放行）。
     */
    @Test
    void tryConsume_shouldAllowFirstOccurrence() {
        assertThat(nonceStore.tryConsume("appKey1", "nonce-a")).isTrue();
    }

    /**
     * 时效窗口内重复出现的同一 {@code accessKey+nonce} 应判定为重放（拒绝）。
     */
    @Test
    void tryConsume_shouldRejectReplayWithinWindow() {
        assertThat(nonceStore.tryConsume("appKey1", "nonce-b")).isTrue();
        assertThat(nonceStore.tryConsume("appKey1", "nonce-b")).isFalse();
    }

    /**
     * 不同 AccessKey 下即使 nonce 相同也应各自独立判定，互不影响。
     */
    @Test
    void tryConsume_shouldBeIndependentPerAccessKey() {
        assertThat(nonceStore.tryConsume("appKey1", "same-nonce")).isTrue();
        assertThat(nonceStore.tryConsume("appKey2", "same-nonce")).isTrue();
    }

    /**
     * 清理任务不应影响仍处于时效窗口内的登记（不会误清理未过期条目）。
     */
    @Test
    void cleanupExpired_shouldNotRemoveUnexpiredEntries() {
        nonceStore.tryConsume("appKey1", "nonce-c");
        nonceStore.cleanupExpired();
        assertThat(nonceStore.tryConsume("appKey1", "nonce-c")).isFalse();
    }
}
