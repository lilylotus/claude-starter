package cn.nihility.rbac.chat.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ChatTokenBucket} 令牌桶容量与耗尽行为测试。 */
class ChatTokenBucketTest {

    /** 桶容量内的连续获取应全部成功，超出容量后应被拒绝。 */
    @Test
    void tryAcquire_shouldAllowWithinCapacityAndRejectBeyond() {
        // 极低的补充速率，避免测试执行期间因时间流逝而补充出新令牌，保证断言稳定。
        ChatTokenBucket bucket = new ChatTokenBucket(3, 0.000001d);

        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isFalse();
    }

    /** 容量为 1 的令牌桶耗尽后应持续拒绝，直到有新令牌补充。 */
    @Test
    void tryAcquire_shouldRejectWhenExhausted() {
        ChatTokenBucket bucket = new ChatTokenBucket(1, 0.000001d);

        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isFalse();
        assertThat(bucket.tryAcquire()).isFalse();
    }
}
