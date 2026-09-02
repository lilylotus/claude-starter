## 1. RedisUtils 通用原子操作

- [x] 1.1 `RedisUtils` 新增 `setIfAbsent(String key, String value, long timeout,
      TimeUnit unit)`，包一层 `opsForValue().setIfAbsent(key, value, timeout, unit)`，
      返回是否设置成功。
- [x] 1.2 `RedisUtils` 新增 `compareAndDelete(String key, String expectedValue)`，用
      `DefaultRedisScript`/`RedisScript` 执行 Lua 脚本
      `if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1])
      else return 0 end`，通过 `template().execute(script, List.of(key),
      expectedValue)` 调用，返回值 `1` 映射为 `true`、`0` 映射为 `false`。
- [x] 1.3 `RedisUtils` 新增 `compareAndExpire(String key, String expectedValue, long
      timeout, TimeUnit unit)`，Lua 脚本 `if redis.call('get', KEYS[1]) == ARGV[1]
      then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end`，用法
      与 `compareAndDelete` 一致，返回值 `1` 映射为 `true`、`0` 映射为 `false`。
- [x] 1.4 为以上三个方法补充单元测试（真实 Redis 连接，对齐项目现有 `RedisUtilsTest`
      风格）：`setIfAbsent` 首次写入成功/二次调用失败且不覆盖原值；`compareAndDelete`
      值匹配删除成功/值不匹配不删除/key 不存在不删除；`compareAndExpire` 值匹配续期
      成功/值不匹配不续期/key 不存在不续期。

## 2. 分布式锁服务

- [x] 2.1 新增 `common/lock` 包：`DistributedLockService` 接口，方法签名对齐
      design.md（`tryLock(key, leaseSeconds)`、`tryLock(key, waitSeconds,
      leaseSeconds)`、`unlock(key, token)`、`executeWithLock` 两个重载）。
- [x] 2.2 新增 `DistributedLockServiceImpl`：
      - 落地 Redis key 统一加 `lock:` 前缀。
      - 加锁 token 用去横线 UUID（对齐 `SsoSessionService` 风格）。
      - 非阻塞 `tryLock` 直接调用 `RedisUtils.setIfAbsent` 一次，成功后启动看门狗
        续期任务（见 2.3）。
      - 阻塞 `tryLock` 内部固定 100ms 轮询间隔重试，用 `System.nanoTime()` 记录截止
        时间判断是否超时；`Thread.sleep` 被中断时恢复中断标志位并返回空结果；成功后
        同样启动看门狗续期任务。
      - `unlock` 先停止该 token 对应的看门狗续期任务（若存在），再调用
        `RedisUtils.compareAndDelete(lockKey, token)`。
      - `executeWithLock`（`Runnable`/`Supplier<T>` 两个重载）内部调用阻塞版
        `tryLock`，超时抛 `LockAcquireTimeoutException`；获取到锁后
        `try { action } finally { unlock }`。
- [x] 2.3 看门狗自动续期：
      - `DistributedLockServiceImpl` 内部持有一个单线程 daemon `ScheduledExecutorService`
        （独立于 `ThreadPoolUtils`，专用于周期性续期任务），以 `@PreDestroy` 在应用
        关闭时 `shutdownNow()`。
      - 用 `ConcurrentHashMap<String token, ScheduledFuture<?>>` 记录每次加锁对应的
        续期任务句柄。
      - 加锁成功后按 `max(leaseSeconds / 3, 1)` 秒为周期调度续期任务，任务内容为调用
        `RedisUtils.compareAndExpire(lockKey, token, leaseSeconds, SECONDS)`；返回
        `false` 时（说明锁已不属于自己）记录 WARN 日志并 `cancel` 自身、从 Map 中移除。
      - `unlock` 时按 token 从 Map 中查找并 `cancel` 对应的 `ScheduledFuture`、移除该
        条目。
- [x] 2.4 新增 `common/exception/LockAcquireTimeoutException extends BusinessException`。
- [x] 2.5 单元测试（真实 Redis 连接，对齐 `SsoSessionServiceTest` 风格）覆盖：
      - 非阻塞 `tryLock` 未被占用时成功/已被占用时立即失败。
      - 阻塞 `tryLock` 等待期间锁释放后成功获取；等待超时返回空结果。
      - `unlock` 用正确 token 释放成功；用错误/他人 token 不释放、返回 `false`。
      - `executeWithLock` 正常执行业务逻辑后释放锁（含业务逻辑抛异常时也能释放）；
        加锁超时抛 `LockAcquireTimeoutException` 且不执行业务逻辑。
      - 阻塞等待期间线程被中断时正确恢复中断标志位并返回空结果。
      - 看门狗：用一个较短的 `leaseSeconds`（如 3 秒）加锁后，等待超过 `leaseSeconds`
        仍未 `unlock`，断言该 key 在 Redis 中仍然存在（被看门狗续期，未自然过期）；
        `unlock` 后断言看门狗停止（key 被删除后不再重新出现）；手动在 Redis 里删除/
        修改该 key 模拟"锁已不属于自己"，断言看门狗能停止自身且不抛异常影响主流程。

## 3. 验证

- [x] 3.1 `./gradlew test`（`backend/` 目录下）全量跑通。
- [x] 3.2 `openspec validate add-distributed-lock-service --strict` 通过。

## 4. OpenSpec 文档同步

- [x] 4.1 实现完成后，基于真实 diff/测试结果，用 `openspec-doc-sync` 校对更新本
      change 的 `proposal.md`/`design.md`/`tasks.md`。
- [x] 4.2 用 `openspec-sync-specs` 把 spec delta 应用到 `backend-common-utilities`，
      再归档本 change。
