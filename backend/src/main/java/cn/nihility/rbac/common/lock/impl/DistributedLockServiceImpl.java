package cn.nihility.rbac.common.lock.impl;

import cn.nihility.rbac.common.exception.LockAcquireTimeoutException;
import cn.nihility.rbac.common.lock.DistributedLockService;
import cn.nihility.rbac.common.util.RedisUtils;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link DistributedLockService} 的默认实现（add-distributed-lock-service change
 * design.md Decision 1/2/3/4/5/6）：只通过 {@link RedisUtils} 静态方法访问 Redis，不直接
 * 持有 {@code StringRedisTemplate}，风格对齐 {@code SsoSessionService}（Javadoc 风格、
 * "UUID 去横线做 token"的写法）。加锁成功后自动启动后台看门狗续期任务，{@code unlock} 时
 * 停止对应任务。
 *
 * <p>看门狗续期任务由本类内部持有的单线程 daemon {@link ScheduledExecutorService} 统一
 * 调度，刻意与 {@code ThreadPoolUtils} 特意使用非 daemon 线程的既有约定相反：续期任务是
 * 周期性的维护性质任务，不是必须跑完才能让 JVM 退出的关键业务工作，不应该反过来阻止应用
 * 关闭（design.md Decision 6）。</p>
 */
@Service
public class DistributedLockServiceImpl implements DistributedLockService {

    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(DistributedLockServiceImpl.class);

    /** 落地 Redis key 的统一前缀，避免与其他模块的 key 空间冲突。 */
    private static final String LOCK_KEY_PREFIX = "lock:";

    /** 阻塞版 {@code tryLock} 的固定轮询间隔（毫秒）。 */
    private static final long POLL_INTERVAL_MILLIS = 100L;

    /** 看门狗续期周期的最小值（秒），避免 {@code leaseSeconds} 过小时续期周期为 0。 */
    private static final long MIN_WATCHDOG_PERIOD_SECONDS = 1L;

    /** 看门狗续期周期相对于 {@code leaseSeconds} 的分母，留出至少两次重试机会（同 Redisson）。 */
    private static final long WATCHDOG_PERIOD_DIVISOR = 3L;

    /**
     * 看门狗续期任务专用的单线程调度器：续期任务需要能够按 token 单独取消、按固定周期长期
     * 运行，不适合复用 {@code ThreadPoolUtils} 仅支持一次性 {@code execute}/{@code submit}
     * 的固定大小业务线程池（design.md Decision 6 备选方案 B）。
     */
    private final ScheduledExecutorService watchdogExecutor =
            Executors.newSingleThreadScheduledExecutor(new WatchdogThreadFactory());

    /** token -&gt; 该次加锁对应的看门狗续期任务句柄，供 {@code unlock} 时查找并取消。 */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> watchdogTasks = new ConcurrentHashMap<>();

    @Override
    public Optional<String> tryLock(String key, long leaseSeconds) {
        String lockKey = lockKey(key);
        String token = newToken();
        if (RedisUtils.setIfAbsent(lockKey, token, leaseSeconds, TimeUnit.SECONDS)) {
            startWatchdog(lockKey, token, leaseSeconds);
            return Optional.of(token);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> tryLock(String key, long waitSeconds, long leaseSeconds) {
        String lockKey = lockKey(key);
        String token = newToken();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSeconds);
        while (true) {
            if (RedisUtils.setIfAbsent(lockKey, token, leaseSeconds, TimeUnit.SECONDS)) {
                startWatchdog(lockKey, token, leaseSeconds);
                return Optional.of(token);
            }
            if (System.nanoTime() >= deadlineNanos) {
                return Optional.empty();
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean unlock(String key, String token) {
        stopWatchdog(token);
        return RedisUtils.compareAndDelete(lockKey(key), token);
    }

    @Override
    public <T> T executeWithLock(String key, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        String token = acquireOrThrow(key, waitSeconds, leaseSeconds);
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }

    @Override
    public void executeWithLock(String key, long waitSeconds, long leaseSeconds, Runnable action) {
        String token = acquireOrThrow(key, waitSeconds, leaseSeconds);
        try {
            action.run();
        } finally {
            unlock(key, token);
        }
    }

    /**
     * 应用关闭时停止全部仍在运行的看门狗任务，避免看门狗的后台线程阻塞或延迟应用正常关闭
     * （design.md Decision 6）。
     */
    @PreDestroy
    public void destroy() {
        watchdogExecutor.shutdownNow();
    }

    /**
     * 阻塞加锁，超时未获取到锁时转换为统一异常。
     *
     * @param key          业务含义的 key 后缀
     * @param waitSeconds  最长等待时长（秒）
     * @param leaseSeconds 锁的初始有效期（秒）
     * @return 加锁成功时返回的锁凭证
     * @throws LockAcquireTimeoutException 等待超时仍未获取到锁时抛出
     */
    private String acquireOrThrow(String key, long waitSeconds, long leaseSeconds) {
        return tryLock(key, waitSeconds, leaseSeconds)
                .orElseThrow(() -> new LockAcquireTimeoutException(
                        "获取分布式锁超时：key=" + key + ", waitSeconds=" + waitSeconds));
    }

    /**
     * 加锁成功后启动该次加锁对应的看门狗续期任务：以 {@code max(leaseSeconds / 3, 1)} 秒为
     * 周期，持续把该锁的过期时间续到 {@code leaseSeconds}，直到 {@code unlock} 被调用或发现
     * 锁已不属于自己（design.md Decision 6）。此处使用 {@code scheduleAtFixedRate} 而不是
     * {@code scheduleWithFixedDelay}：续期任务本身耗时极短（一次 Redis 往返），用固定速率
     * 能让续期节奏更贴近预期周期，不会因为任务执行耗时被动顺延、逐渐偏离预期的续期节奏。
     *
     * @param lockKey      落地的完整 Redis key（已带 {@link #LOCK_KEY_PREFIX} 前缀）
     * @param token        本次加锁生成的凭证
     * @param leaseSeconds 锁的有效期（秒），也是每次续期要重设到的目标值
     */
    private void startWatchdog(String lockKey, String token, long leaseSeconds) {
        long periodSeconds = Math.max(leaseSeconds / WATCHDOG_PERIOD_DIVISOR, MIN_WATCHDOG_PERIOD_SECONDS);
        ScheduledFuture<?> future = watchdogExecutor.scheduleAtFixedRate(
                () -> renew(lockKey, token, leaseSeconds),
                periodSeconds,
                periodSeconds,
                TimeUnit.SECONDS);
        watchdogTasks.put(token, future);
    }

    /**
     * 看门狗单次续期动作：按预期 token 原子续期，续期失败（说明 Redis 中该 key 当前值已经
     * 不是这次加锁的 token）时停止自身并记录 WARN 日志，不再继续对一把已经不属于自己的锁做
     * 无意义续期。
     *
     * @param lockKey      落地的完整 Redis key
     * @param token        本次加锁生成的凭证
     * @param leaseSeconds 续期目标值（秒）
     */
    private void renew(String lockKey, String token, long leaseSeconds) {
        boolean renewed = RedisUtils.compareAndExpire(lockKey, token, leaseSeconds, TimeUnit.SECONDS);
        if (!renewed) {
            log.warn("分布式锁看门狗续期失败，锁已不属于当前持有者，停止续期：lockKey={}, token={}", lockKey, token);
            stopWatchdog(token);
        }
    }

    /**
     * 停止并移除指定 token 对应的看门狗续期任务（若存在），不存在时安全跳过、不抛异常。
     *
     * @param token 加锁时生成的凭证
     */
    private void stopWatchdog(String token) {
        ScheduledFuture<?> future = watchdogTasks.remove(token);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 拼接落地 Redis key。
     *
     * @param key 业务含义的 key 后缀
     * @return 完整 Redis key
     */
    private String lockKey(String key) {
        return LOCK_KEY_PREFIX + key;
    }

    /**
     * 生成一个不含横线的 UUID 字符串，作为本次加锁的凭证（对齐 {@code SsoSessionService}
     * 风格）。
     *
     * @return 凭证字符串
     */
    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 看门狗调度线程的线程工厂：固定线程名 {@code lock-watchdog}，设置为 daemon 线程（理由见
     * 类注释），避免应用关闭时被这个后台线程阻塞。
     */
    private static final class WatchdogThreadFactory implements ThreadFactory {

        /** 线程名前缀。 */
        private static final String THREAD_NAME_PREFIX = "lock-watchdog-";

        /** 线程序号生成器（单线程池下始终为 1，保留以便未来扩展为多线程时线程名不重复）。 */
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
