package cn.nihility.rbac.common.lock;

import cn.nihility.rbac.common.exception.LockAcquireTimeoutException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 基于项目现有单一 Redis 实例的通用分布式锁公共服务（add-distributed-lock-service change
 * design.md），供后续需要"避免同一资源被并发请求同时修改"的业务模块直接注入使用（如应用
 * 同步、审批流程状态流转）。只提供建议性（advisory）锁语义：所有调用方都必须遵守"先加锁再
 * 操作"的约定才有效，不支持锁重入，不是跨多个独立 Redis 实例的 Redlock 强一致算法。
 *
 * <p>加锁成功后会自动启动后台看门狗（watchdog）任务持续续期，调用方不需要精确预估业务逻辑
 * 耗时来设置 {@code leaseSeconds}，只要还没有显式 {@code unlock}、进程仍存活且能访问
 * Redis，锁就不会因为业务逻辑执行时间较长而被提前释放；一旦进程崩溃或与 Redis 失联，锁仍会
 * 在最后一次续期设置的 {@code leaseSeconds} 后自然过期，作为兜底，不会永久占用。</p>
 */
public interface DistributedLockService {

    /**
     * 非阻塞加锁：尝试立即获取指定业务 key 对应的锁，失败时不等待、立即返回空结果。
     *
     * @param key          业务含义的 key 后缀（落地 Redis key 会自动加 {@code lock:} 前缀）
     * @param leaseSeconds 锁的初始有效期（秒），加锁成功后由看门狗持续续期到该值，直到
     *                     {@code unlock} 被调用
     * @return 加锁成功时返回非空的锁凭证（token），失败（该锁当前被其他调用方持有）时返回空
     */
    Optional<String> tryLock(String key, long leaseSeconds);

    /**
     * 阻塞加锁（带超时重试）：目标锁当前被占用时，在最长 {@code waitSeconds} 秒内以固定
     * 间隔轮询重试，直到成功获取锁或等待超时。
     *
     * @param key          业务含义的 key 后缀（落地 Redis key 会自动加 {@code lock:} 前缀）
     * @param waitSeconds  最长等待时长（秒）
     * @param leaseSeconds 锁的初始有效期（秒），加锁成功后由看门狗持续续期到该值，直到
     *                     {@code unlock} 被调用
     * @return 加锁成功时返回非空的锁凭证（token），等待超时仍未获取到锁时返回空
     */
    Optional<String> tryLock(String key, long waitSeconds, long leaseSeconds);

    /**
     * 安全解锁：仅当该 key 当前持有的锁凭证与传入的 {@code token} 一致时才释放锁。无论解锁
     * 是否成功，只要调用了本方法，都会停止该 {@code token} 对应的看门狗续期任务。
     *
     * @param key   加锁时使用的业务含义 key 后缀
     * @param token 加锁时返回的锁凭证
     * @return 是否释放成功；{@code key} 当前未被锁定、或持有的凭证与传入 {@code token} 不
     *         一致时返回 {@code false}，不会误删其他调用方持有的锁
     */
    boolean unlock(String key, String token);

    /**
     * 便捷封装：阻塞加锁 → 执行业务逻辑（有返回值）→ 无论正常返回还是抛出异常都释放锁。
     *
     * @param key          业务含义的 key 后缀
     * @param waitSeconds  最长等待时长（秒）
     * @param leaseSeconds 锁的初始有效期（秒）
     * @param action       持锁期间执行的业务逻辑
     * @param <T>          业务逻辑返回值类型
     * @return 业务逻辑的执行结果
     * @throws LockAcquireTimeoutException {@code waitSeconds} 内始终未能获取到锁时抛出，
     *                                      此时 {@code action} 不会被执行
     */
    <T> T executeWithLock(String key, long waitSeconds, long leaseSeconds, Supplier<T> action);

    /**
     * 便捷封装：阻塞加锁 → 执行业务逻辑（无返回值）→ 无论正常返回还是抛出异常都释放锁。
     *
     * @param key          业务含义的 key 后缀
     * @param waitSeconds  最长等待时长（秒）
     * @param leaseSeconds 锁的初始有效期（秒）
     * @param action       持锁期间执行的业务逻辑
     * @throws LockAcquireTimeoutException {@code waitSeconds} 内始终未能获取到锁时抛出，
     *                                      此时 {@code action} 不会被执行
     */
    void executeWithLock(String key, long waitSeconds, long leaseSeconds, Runnable action);
}
