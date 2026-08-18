package cn.nihility.rbac.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link ThreadPoolUtils} 的测试：验证正常任务能被提交并执行，以及线程与队列均已饱和时
 * 提交新任务会抛出 {@link RejectedExecutionException}。{@link ThreadPoolUtils} 是 JVM
 * 级静态单例，本类不使用 Spring 容器（纯 JUnit），与其余起 Spring 容器的测试隔离运行。
 */
class ThreadPoolUtilsTest {

    /** 全局线程池的核心/最大线程数，需与 {@link ThreadPoolUtils} 内部配置保持一致。 */
    private static final int POOL_SIZE = 4;

    /** 全局线程池有界队列容量，需与 {@link ThreadPoolUtils} 内部配置保持一致。 */
    private static final int QUEUE_CAPACITY = 2048;

    /**
     * 正常提交的 {@code Runnable}/{@code Callable} 任务应能被线程池执行。
     */
    @Test
    void executeAndSubmit_shouldRunSubmittedTasks() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch executeLatch = new CountDownLatch(1);

        ThreadPoolUtils.execute(() -> {
            counter.incrementAndGet();
            executeLatch.countDown();
        });
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> runnableFuture = ThreadPoolUtils.submit(() -> counter.incrementAndGet());
        runnableFuture.get(5, TimeUnit.SECONDS);

        Future<Integer> callableFuture = ThreadPoolUtils.submit(counter::incrementAndGet);
        assertThat(callableFuture.get(5, TimeUnit.SECONDS)).isEqualTo(3);

        assertThat(counter.get()).isEqualTo(3);
    }

    /**
     * 线程数达到最大值且队列已满时，再提交任务应立即抛出 {@link RejectedExecutionException}，
     * 而不是无限阻塞或静默丢弃。通过阻塞任务占满全部线程、再填满有界队列来构造饱和场景，
     * 结束后释放阻塞任务，避免影响后续用例复用的全局线程池。
     */
    @Test
    void submit_shouldThrowRejectedExecutionException_whenPoolAndQueueSaturated() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(POOL_SIZE);

        try {
            // 占满全部核心线程，使其全部阻塞在 blockLatch 上。
            for (int i = 0; i < POOL_SIZE; i++) {
                ThreadPoolUtils.execute(() -> {
                    startedLatch.countDown();
                    awaitUninterruptibly(blockLatch);
                });
            }
            assertThat(startedLatch.await(5, TimeUnit.SECONDS)).isTrue();

            // 填满有界队列。
            for (int i = 0; i < QUEUE_CAPACITY; i++) {
                ThreadPoolUtils.execute(() -> awaitUninterruptibly(blockLatch));
            }

            // 线程与队列均已饱和，再提交任务应被立即拒绝。
            assertThatThrownBy(() -> ThreadPoolUtils.execute(() -> { }))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            blockLatch.countDown();
        }
    }

    /**
     * 等待给定 {@link CountDownLatch}，屏蔽中断异常，供测试内的阻塞任务使用。
     *
     * @param latch 待等待的门闩
     */
    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
