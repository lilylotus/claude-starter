package cn.nihility.rbac.common.lock.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.LockAcquireTimeoutException;
import cn.nihility.rbac.common.lock.DistributedLockService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link DistributedLockServiceImpl} 的测试，起真实 Redis 连接（同项目既有"不做重量级
 * mock"的测试风格，对齐 {@code SsoSessionServiceTest}），覆盖 tasks.md 2.5 列出的全部场景：
 * 非阻塞/阻塞加锁、安全解锁、{@code executeWithLock} 便捷封装、阻塞等待被中断、以及看门狗
 * 自动续期的三种场景（长业务耗时下锁不提前过期、{@code unlock} 后看门狗停止、锁已不属于
 * 自己时看门狗自行停止且不抛异常）。
 */
@SpringBootTest
class DistributedLockServiceImplTest {

    /** 被测服务，真实注入（依赖真实 Redis 连接）。 */
    @Autowired
    private DistributedLockService distributedLockService;

    /** 字符串 Redis 模板，用于测试内直接操作 Redis（模拟他人持锁、清理测试数据）。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例加锁使用的业务 key 后缀（落地 Redis key 会自动加 {@code lock:} 前缀），测试结束后统一清理。 */
    private final List<String> usedKeys = new ArrayList<>();

    /**
     * 每个用例结束后清理本用例可能残留的 Redis 记录，避免污染后续用例/占用 Redis 空间。
     */
    @AfterEach
    void cleanup() {
        usedKeys.forEach(key -> stringRedisTemplate.delete("lock:" + key));
        usedKeys.clear();
    }

    /**
     * 目标 key 未被锁定时，非阻塞 {@code tryLock} 应立即加锁成功并返回非空凭证。
     */
    @Test
    void tryLock_shouldSucceed_whenKeyNotLocked() {
        String key = trackedKey("non-blocking-success");

        Optional<String> token = distributedLockService.tryLock(key, 10);

        assertThat(token).isPresent();
        assertThat(stringRedisTemplate.opsForValue().get("lock:" + key)).isEqualTo(token.get());
    }

    /**
     * 目标 key 已被持有时，非阻塞 {@code tryLock} 应立即返回空结果，不阻塞等待。
     */
    @Test
    void tryLock_shouldFailImmediately_whenKeyAlreadyLocked() {
        String key = trackedKey("non-blocking-occupied");
        Optional<String> first = distributedLockService.tryLock(key, 10);
        assertThat(first).isPresent();

        Optional<String> second = distributedLockService.tryLock(key, 10);

        assertThat(second).isEmpty();
    }

    /**
     * 阻塞 {@code tryLock} 等待期间，锁被原持有者释放后，应能在 {@code waitSeconds} 用完前成功获取。
     */
    @Test
    void blockingTryLock_shouldSucceed_afterLockReleasedDuringWait() throws InterruptedException {
        String key = trackedKey("blocking-released");
        Optional<String> first = distributedLockService.tryLock(key, 10);
        assertThat(first).isPresent();

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            distributedLockService.unlock(key, first.get());
        });
        releaser.start();

        long start = System.nanoTime();
        Optional<String> second = distributedLockService.tryLock(key, 3, 10);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        releaser.join(2000);

        assertThat(second).isPresent();
        assertThat(elapsedMillis).isLessThan(3000L);
        distributedLockService.unlock(key, second.get());
    }

    /**
     * 阻塞 {@code tryLock} 在整个 {@code waitSeconds} 等待期间目标 key 始终被占用时，应在
     * 等待超时后返回空结果。
     */
    @Test
    void blockingTryLock_shouldReturnEmpty_whenWaitTimesOut() {
        String key = trackedKey("blocking-timeout");
        Optional<String> holder = distributedLockService.tryLock(key, 10);
        assertThat(holder).isPresent();

        Optional<String> result = distributedLockService.tryLock(key, 1, 10);

        assertThat(result).isEmpty();
        distributedLockService.unlock(key, holder.get());
    }

    /**
     * 阻塞 {@code tryLock} 轮询等待过程中，执行线程被外部中断时，应恢复中断标志位并立即返回空结果。
     */
    @Test
    void blockingTryLock_shouldRestoreInterruptFlag_whenInterruptedDuringWait() throws InterruptedException {
        String key = trackedKey("blocking-interrupted");
        Optional<String> holder = distributedLockService.tryLock(key, 10);
        assertThat(holder).isPresent();

        AtomicReference<Optional<String>> resultRef = new AtomicReference<>();
        AtomicBoolean interruptedAfterReturn = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            Optional<String> result = distributedLockService.tryLock(key, 5, 10);
            resultRef.set(result);
            interruptedAfterReturn.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        Thread.sleep(150);
        worker.interrupt();
        worker.join(2000);

        assertThat(resultRef.get()).isEmpty();
        assertThat(interruptedAfterReturn.get()).isTrue();
        distributedLockService.unlock(key, holder.get());
    }

    /**
     * 使用加锁时返回的正确 token 调用 {@code unlock}，应释放成功。
     */
    @Test
    void unlock_shouldSucceed_withCorrectToken() {
        String key = trackedKey("unlock-correct-token");
        String token = distributedLockService.tryLock(key, 10).orElseThrow();

        boolean result = distributedLockService.unlock(key, token);

        assertThat(result).isTrue();
        assertThat(stringRedisTemplate.hasKey("lock:" + key)).isFalse();
    }

    /**
     * 使用错误/他人的 token 调用 {@code unlock}，不应释放锁，返回 {@code false}。
     */
    @Test
    void unlock_shouldFail_withWrongToken() {
        String key = trackedKey("unlock-wrong-token");
        String token = distributedLockService.tryLock(key, 10).orElseThrow();

        boolean result = distributedLockService.unlock(key, "not-the-real-token");

        assertThat(result).isFalse();
        assertThat(stringRedisTemplate.opsForValue().get("lock:" + key)).isEqualTo(token);
        distributedLockService.unlock(key, token);
    }

    /**
     * {@code executeWithLock} 加锁成功后应正常执行业务逻辑并在完成后释放锁；业务逻辑抛出
     * 异常时锁也应被释放，异常向上传播。
     */
    @Test
    void executeWithLock_shouldRunActionAndReleaseLock_evenWhenActionThrows() {
        String key = trackedKey("execute-with-lock-exception");

        assertThatThrownBy(() -> distributedLockService.executeWithLock(key, 2, 10, () -> {
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("business failure");

        assertThat(stringRedisTemplate.hasKey("lock:" + key)).isFalse();
    }

    /**
     * {@code executeWithLock} 正常返回值场景：应执行业务逻辑并返回其结果，完成后释放锁。
     */
    @Test
    void executeWithLock_shouldReturnActionResult_andReleaseLock() {
        String key = trackedKey("execute-with-lock-success");

        String result = distributedLockService.executeWithLock(key, 2, 10, () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(stringRedisTemplate.hasKey("lock:" + key)).isFalse();
    }

    /**
     * {@code executeWithLock} 在 {@code waitSeconds} 内始终未能获取到锁时，应抛出
     * {@link LockAcquireTimeoutException} 且不执行传入的业务逻辑。
     */
    @Test
    void executeWithLock_shouldThrow_andSkipAction_whenAcquireTimesOut() {
        String key = trackedKey("execute-with-lock-timeout");
        String holderToken = distributedLockService.tryLock(key, 10).orElseThrow();
        AtomicInteger invocationCount = new AtomicInteger();

        assertThatThrownBy(() -> distributedLockService.executeWithLock(key, 1, 10, invocationCount::incrementAndGet))
                .isInstanceOf(LockAcquireTimeoutException.class);

        assertThat(invocationCount.get()).isZero();
        distributedLockService.unlock(key, holderToken);
    }

    /**
     * 看门狗场景一：业务逻辑实际耗时超过初始 {@code leaseSeconds} 时，锁应持续被看门狗续期、
     * 不会提前过期。
     */
    @Test
    void watchdog_shouldKeepLockAlive_whenHeldLongerThanLeaseSeconds() throws InterruptedException {
        String key = trackedKey("watchdog-keep-alive");
        String token = distributedLockService.tryLock(key, 3).orElseThrow();

        Thread.sleep(4000);

        assertThat(stringRedisTemplate.opsForValue().get("lock:" + key)).isEqualTo(token);
        distributedLockService.unlock(key, token);
    }

    /**
     * 看门狗场景二：{@code unlock} 后看门狗应停止续期，被删除的锁不会因为遗留的续期任务
     * 而重新出现。
     */
    @Test
    void watchdog_shouldStop_afterUnlock() throws InterruptedException {
        String key = trackedKey("watchdog-stop-after-unlock");
        String token = distributedLockService.tryLock(key, 3).orElseThrow();

        boolean unlocked = distributedLockService.unlock(key, token);
        Thread.sleep(1500);

        assertThat(unlocked).isTrue();
        assertThat(stringRedisTemplate.hasKey("lock:" + key)).isFalse();
    }

    /**
     * 看门狗场景三：Redis 中该锁的值被手动改写为不属于当前持有者的值（模拟锁已被其他调用方
     * 重新获取）时，看门狗应自行停止，不抛出任何异常影响主流程。
     */
    @Test
    void watchdog_shouldStopItself_whenLockNoLongerBelongsToHolder() throws InterruptedException {
        String key = trackedKey("watchdog-lock-stolen");
        distributedLockService.tryLock(key, 3).orElseThrow();

        stringRedisTemplate.opsForValue().set("lock:" + key, "other-holder-token", 5, TimeUnit.SECONDS);
        Thread.sleep(1500);

        assertThat(stringRedisTemplate.opsForValue().get("lock:" + key)).isEqualTo("other-holder-token");
    }

    /**
     * 生成一个测试用的业务 key 后缀，并记录下来供 {@link #cleanup()} 统一清理。
     *
     * @param suffix key 后缀
     * @return 带唯一后缀的业务 key
     */
    private String trackedKey(String suffix) {
        String key = "test:lock-service:" + suffix;
        usedKeys.add(key);
        return key;
    }
}
