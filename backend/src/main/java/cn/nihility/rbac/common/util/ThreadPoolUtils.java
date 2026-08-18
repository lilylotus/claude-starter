package cn.nihility.rbac.common.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局统一的异步任务线程池工具类，收敛各业务模块各自 {@code new Thread}/临时创建
 * {@code ExecutorService} 的做法，提供统一、可控的异步任务执行入口。内部持有一个进程内
 * 唯一的固定大小 {@link ThreadPoolExecutor}（核心线程数 = 最大线程数 = 4），配合容量为
 * 2048 的有界队列（{@link ArrayBlockingQueue}）缓存待执行任务；当线程与队列均已饱和时，
 * 采用自定义拒绝策略：先记录一条包含拒绝原因（线程池当前状态）的 WARN 日志，再抛出
 * {@link RejectedExecutionException}，不静默丢弃任务、不由调用者线程代为执行。风格对齐
 * {@link RedisUtils}/{@link JacksonUtils}：静态字段持有实例 + 静态方法暴露，不注册为
 * Spring bean、不做构造器注入，业务代码直接 {@code ThreadPoolUtils.execute(...)} 调用。
 */
public final class ThreadPoolUtils {

    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolUtils.class);

    /** 核心线程数（与最大线程数相同，构成固定大小线程池）。 */
    private static final int CORE_POOL_SIZE = 4;

    /** 最大线程数。 */
    private static final int MAXIMUM_POOL_SIZE = 4;

    /** 空闲线程存活时间：核心线程数=最大线程数时不生效，设为 0 表明这是固定大小池。 */
    private static final long KEEP_ALIVE_TIME = 0L;

    /** 有界任务队列容量。 */
    private static final int QUEUE_CAPACITY = 2048;

    /** 全局唯一的固定大小线程池实例。 */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            new NamedThreadFactory(),
            new LoggingAbortPolicy());

    /**
     * 工具类不允许实例化。
     */
    private ThreadPoolUtils() {
    }

    /**
     * 提交一个无返回值的任务到全局线程池执行。
     *
     * @param task 待执行任务
     * @throws RejectedExecutionException 线程与队列均已饱和时抛出
     */
    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }

    /**
     * 提交一个无返回值的任务到全局线程池执行，并返回一个可用于等待任务完成的 {@link Future}。
     *
     * @param task 待执行任务
     * @return 代表任务执行结果的 {@link Future}（完成后 {@code get()} 返回 {@code null}）
     * @throws RejectedExecutionException 线程与队列均已饱和时抛出
     */
    public static Future<?> submit(Runnable task) {
        return EXECUTOR.submit(task);
    }

    /**
     * 提交一个有返回值的任务到全局线程池执行。
     *
     * @param task 待执行任务
     * @param <T>  任务执行结果类型
     * @return 代表任务执行结果的 {@link Future}
     * @throws RejectedExecutionException 线程与队列均已饱和时抛出
     */
    public static <T> Future<T> submit(Callable<T> task) {
        return EXECUTOR.submit(task);
    }

    /**
     * 自定义线程工厂：线程名带 {@code thread-pool-} 前缀 + 递增序号，便于日志/线程 dump
     * 定位；创建的线程设置为非 daemon 线程，避免应用还有异步任务在跑时 JVM 提前退出。
     */
    private static final class NamedThreadFactory implements ThreadFactory {

        /** 线程名前缀。 */
        private static final String THREAD_NAME_PREFIX = "thread-pool-";

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
     * 自定义拒绝策略：线程与队列均已饱和、任务被拒绝时，先以 WARN 级别记录拒绝原因及
     * 线程池当前状态（活跃线程数、队列积压数、已完成任务数），再抛出
     * {@link RejectedExecutionException}，异常语义与 JDK 内置的
     * {@link ThreadPoolExecutor.AbortPolicy} 保持一致，仅额外补充了诊断日志。
     */
    private static final class LoggingAbortPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            log.warn(
                    "线程池任务被拒绝：线程与队列均已饱和，activeCount={}, "
                            + "queueSize={}, poolSize={}, completedTaskCount={}, task={}",
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getPoolSize(),
                    executor.getCompletedTaskCount(),
                    task);
            throw new RejectedExecutionException(
                    "Task " + task + " rejected from " + executor);
        }
    }
}
