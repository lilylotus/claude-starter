package cn.nihility.rbac.sync.sign;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 单进程内 nonce 短时去重存储（app-sync-notify-pull-api change design.md Decision 10 /
 * Risks）：{@code accessKey + ":" + nonce} 为 key，值为过期时间戳，在
 * {@link SignConstants#TIMESTAMP_WINDOW_MILLIS} 时效窗口内重复出现的 nonce 判定为重放。
 * 仅在单实例内有效，多实例部署下同一 nonce 可能在不同实例上各自"首次出现"而放行两次，
 * 属于已知限制（design.md Risks，非本次目标）。
 */
@Component
public class NonceStore {

    /** {@code accessKey:nonce} -> 过期时间戳（毫秒）。 */
    private final ConcurrentHashMap<String, Long> seenNonces = new ConcurrentHashMap<>();

    /**
     * 尝试登记一个 nonce，登记成功（此前未出现过或已过期）返回 {@code true}，
     * 命中未过期的既有登记（重放）返回 {@code false}。
     *
     * @param accessKey 应用 AccessKey
     * @param nonce     随机数
     * @return 是否登记成功（非重放）
     */
    public boolean tryConsume(String accessKey, String nonce) {
        String key = accessKey + ":" + nonce;
        long now = System.currentTimeMillis();
        long expireAt = now + SignConstants.TIMESTAMP_WINDOW_MILLIS;
        Long previousExpireAt = seenNonces.putIfAbsent(key, expireAt);
        if (previousExpireAt == null) {
            return true;
        }
        if (previousExpireAt < now) {
            // 既有登记已过期，视为新的一次出现，刷新过期时间后放行。
            seenNonces.put(key, expireAt);
            return true;
        }
        return false;
    }

    /**
     * 定期清理已过期的 nonce 登记，避免内存无限增长，每分钟执行一次。
     */
    @Scheduled(fixedDelay = 60_000L)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        seenNonces.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
