package cn.nihility.rbac.sync.notify.support;

import cn.nihility.rbac.sync.notify.config.NotifyRetryProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知发送专用线程池（app-sync-changelog-pull change design.md Decision 6）：独立于
 * Disruptor 消费者线程与 {@code log-cleanup}/通知重试调度器的调度线程，固定大小与
 * {@code rbac.sync.notify-retry.scheduler-batch-size} 同量级，避免调度器单轮抢占的任务
 * 提交后互相排队等待过久；不复用全局共享的 {@code ThreadPoolUtils}（其固定 4 线程规模是
 * 面向轻量级 fire-and-forget 场景设计的，不足以承载通知发送这种"单轮可能上百个任务"的
 * 并发量级，且与其他模块共享会造成互相挤占）。
 */
@Slf4j
@Component
public class NotifySendExecutor {

    /** 有界任务队列容量相对线程数的倍数，允许短时突发排队而不至无限增长。 */
    private static final int QUEUE_CAPACITY_MULTIPLIER = 4;

    /** 线程池优雅关闭等待超时（秒）。 */
    private static final long SHUTDOWN_AWAIT_SECONDS = 10L;

    /** 内部线程池实例。 */
    private final ThreadPoolExecutor executor;

    /**
     * 按配置的调度器单轮抢占批量大小构建固定大小线程池。
     *
     * @param notifyRetryProperties 通知重试相关配置
     */
    public NotifySendExecutor(NotifyRetryProperties notifyRetryProperties) {
        int poolSize = Math.max(1, notifyRetryProperties.getSchedulerBatchSize());
        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(poolSize * QUEUE_CAPACITY_MULTIPLIER),
                new NamedThreadFactory(),
                new LoggingAbortPolicy());
    }

    /**
     * 提交一个通知发送任务到专用线程池执行。
     *
     * @param task 待执行任务
     * @throws RejectedExecutionException 线程与队列均已饱和时抛出，调用方应捕获并降级为
     *                                     "等待调度器下一轮兜底扫描"，不影响主流程
     */
    public void submit(Runnable task) {
        executor.execute(task);
    }

    /**
     * 容器关闭时优雅停止线程池，避免进程退出时正在发送的请求被强行中断。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 自定义线程工厂：线程名带 {@code notify-send-} 前缀 + 递增序号，便于日志/线程 dump 定位；
     * 创建的线程设置为非 daemon 线程。
     */
    private static final class NamedThreadFactory implements ThreadFactory {

        /** 线程名前缀。 */
        private static final String THREAD_NAME_PREFIX = "notify-send-";

        /** 线程序号生成器，保证同一进程内线程名不重复。 */
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + sequence.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }

    /**
     * 自定义拒绝策略：线程与队列均已饱和、任务被拒绝时，先以 WARN 级别记录线程池当前状态，
     * 再抛出 {@link RejectedExecutionException}，异常语义与 JDK 内置的
     * {@link ThreadPoolExecutor.AbortPolicy} 保持一致，仅额外补充诊断日志（对齐
     * {@code cn.nihility.rbac.common.util.ThreadPoolUtils} 既有风格）。
     */
    private static final class LoggingAbortPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            log.warn(
                    "通知发送线程池任务被拒绝：线程与队列均已饱和，等待调度器下一轮兜底扫描，"
                            + "activeCount={}, queueSize={}, poolSize={}, completedTaskCount={}",
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getPoolSize(),
                    executor.getCompletedTaskCount());
            throw new RejectedExecutionException("Task " + task + " rejected from " + executor);
        }
    }
}
