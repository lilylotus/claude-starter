package cn.nihility.rbac.chat.gateway.support;

/**
 * 手写令牌桶限流实现（design.md Decision 6，不引入 Guava）。{@code synchronized} 保护
 * 内部状态，单个桶的竞争范围仅限"同一 userId/IP 的并发请求"，粒度足够小，不构成性能瓶颈。
 */
public final class ChatTokenBucket {

    /** 桶容量（允许的最大突发令牌数）。 */
    private final double capacity;

    /** 每纳秒补充的令牌数（由"每秒补充令牌数"换算而来）。 */
    private final double refillPerNano;

    /** 当前可用令牌数。 */
    private double availableTokens;

    /** 上一次补充令牌的时间戳（纳秒）。 */
    private long lastRefillNanos;

    /**
     * 构造一个令牌桶，初始令牌数等于桶容量（允许启动即刻的一次突发）。
     *
     * @param capacity        桶容量
     * @param tokensPerSecond 每秒补充的令牌数
     */
    public ChatTokenBucket(int capacity, double tokensPerSecond) {
        this.capacity = capacity;
        this.refillPerNano = tokensPerSecond / 1_000_000_000d;
        this.availableTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * 尝试获取一个令牌。
     *
     * @return 获取成功返回 {@code true}，令牌不足返回 {@code false}
     */
    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens >= 1d) {
            availableTokens -= 1d;
            return true;
        }
        return false;
    }

    /**
     * 按流逝时间补充令牌，不超过桶容量。
     */
    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        availableTokens = Math.min(capacity, availableTokens + elapsed * refillPerNano);
        lastRefillNanos = now;
    }
}
