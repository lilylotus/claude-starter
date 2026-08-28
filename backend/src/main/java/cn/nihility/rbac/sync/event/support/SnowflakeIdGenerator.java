package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.event.config.SyncProperties;
import org.springframework.stereotype.Component;

/**
 * 无外部依赖的雪花 ID 生成器。ID 只用于全局幂等标识，不承担严格递增游标职责。
 */
@Component
public class SnowflakeIdGenerator {

    /** 自定义纪元：2025-01-01T00:00:00Z。 */
    private static final long EPOCH_MILLIS = 1735689600000L;
    private static final long WORKER_ID_MASK = 1023L;
    private static final long SEQUENCE_MASK = 4095L;
    private static final long MAX_BACKWARD_WAIT_MILLIS = 5L;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /** 使用同步配置中的 worker id 构造生成器。 */
    public SnowflakeIdGenerator(SyncProperties properties) {
        if (properties.getWorkerId() < 0 || properties.getWorkerId() > WORKER_ID_MASK) {
            throw new IllegalArgumentException("rbac.sync.worker-id 必须在 0 到 1023 之间");
        }
        this.workerId = properties.getWorkerId();
    }

    /** 生成一个全局唯一 ID；严重时钟回拨时明确失败。 */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long backwardMillis = lastTimestamp - timestamp;
            if (backwardMillis > MAX_BACKWARD_WAIT_MILLIS) {
                throw new IllegalStateException("系统时钟回拨超过允许阈值: " + backwardMillis + "ms");
            }
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH_MILLIS) << 22) | (workerId << 12) | sequence;
    }

    /** 提供可覆盖的时钟入口，便于精确验证回拨策略。 */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private long waitUntil(long targetTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp < targetTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }
}
