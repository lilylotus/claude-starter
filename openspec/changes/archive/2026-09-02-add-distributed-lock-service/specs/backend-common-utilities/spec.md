## ADDED Requirements

### Requirement: RedisUtils 通用原子 SET-IF-ABSENT 操作
`RedisUtils` SHALL 提供 `setIfAbsent(String key, String value, long timeout, TimeUnit unit)`
方法，原子地"仅当 key 当前不存在时写入该值并设置过期时间"（底层为 Redis
`SET key value NX PX`），返回是否实际写入成功；key 已存在时 SHALL NOT 修改其当前值或
过期时间，返回 `false`。

#### Scenario: key 不存在时写入成功
- **WHEN** 调用 `RedisUtils.setIfAbsent(key, value, timeout, unit)`，该 `key` 当前不存在
- **THEN** 方法返回 `true`，`key` 被写入指定值并设置了对应过期时间

#### Scenario: key 已存在时写入失败且不覆盖原值
- **WHEN** 调用 `RedisUtils.setIfAbsent(key, value, timeout, unit)`，该 `key` 当前已存在
  （值为其他内容）
- **THEN** 方法返回 `false`，该 `key` 的原值与过期时间均不受影响

### Requirement: RedisUtils 通用原子按预期值比较删除操作
`RedisUtils` SHALL 提供 `compareAndDelete(String key, String expectedValue)` 方法，原子地
"仅当 key 当前值等于 `expectedValue` 时才删除该 key"（底层用 Lua 脚本实现，保证比较与删除
在一次 Redis 调用内完成，不存在中间态被其他调用方读到或修改的窗口），返回是否实际删除；
`key` 不存在、或当前值与 `expectedValue` 不相等时 SHALL NOT 删除，返回 `false`。

#### Scenario: 当前值匹配时删除成功
- **WHEN** 调用 `RedisUtils.compareAndDelete(key, expectedValue)`，该 `key` 当前存在且值
  等于 `expectedValue`
- **THEN** 方法返回 `true`，该 `key` 被删除

#### Scenario: 当前值不匹配时不删除
- **WHEN** 调用 `RedisUtils.compareAndDelete(key, expectedValue)`，该 `key` 当前存在但值
  不等于 `expectedValue`
- **THEN** 方法返回 `false`，该 `key` 未被删除，仍保留其原值

#### Scenario: key 不存在时不删除
- **WHEN** 调用 `RedisUtils.compareAndDelete(key, expectedValue)`，该 `key` 当前不存在
- **THEN** 方法返回 `false`

### Requirement: RedisUtils 通用原子按预期值比较续期操作
`RedisUtils` SHALL 提供 `compareAndExpire(String key, String expectedValue, long timeout,
TimeUnit unit)` 方法，原子地"仅当 key 当前值等于 `expectedValue` 时才重新设置其过期时间"
（底层用 Lua 脚本实现，保证比较与续期在一次 Redis 调用内完成），返回是否实际续期成功；
`key` 不存在、或当前值与 `expectedValue` 不相等时 SHALL NOT 修改其过期时间，返回 `false`。

#### Scenario: 当前值匹配时续期成功
- **WHEN** 调用 `RedisUtils.compareAndExpire(key, expectedValue, timeout, unit)`，该
  `key` 当前存在且值等于 `expectedValue`
- **THEN** 方法返回 `true`，该 `key` 的过期时间被重新设置为 `timeout`

#### Scenario: 当前值不匹配时不续期
- **WHEN** 调用 `RedisUtils.compareAndExpire(key, expectedValue, timeout, unit)`，该
  `key` 当前存在但值不等于 `expectedValue`
- **THEN** 方法返回 `false`，该 `key` 的过期时间不受影响

#### Scenario: key 不存在时不续期
- **WHEN** 调用 `RedisUtils.compareAndExpire(key, expectedValue, timeout, unit)`，该
  `key` 当前不存在
- **THEN** 方法返回 `false`

### Requirement: 分布式锁——非阻塞加锁
系统 SHALL 提供一个位于 `cn.nihility.rbac.common.lock` 包下的 Spring 注入式服务
`DistributedLockService`，其 `tryLock(String key, long leaseSeconds)` 方法 SHALL 尝试
立即获取指定业务 key 对应的分布式锁（内部落地 Redis key 为 `lock:` 前缀 + 该业务 key，
避免与其他模块的 key 空间冲突），成功时返回一个非空的锁凭证（token），失败（该锁当前被
其他调用方持有）时 SHALL 不阻塞等待、立即返回空结果。加锁成功后该锁 SHALL 在
`leaseSeconds` 秒后自动过期释放（无需显式解锁也不会永久占用），且系统 SHALL 立即启动
一个后台看门狗任务持续为该锁续期（见"分布式锁——看门狗自动续期"需求），使锁不会仅仅因为
调用方业务逻辑耗时超过 `leaseSeconds` 而被提前释放。

#### Scenario: 目标 key 未被锁定时立即加锁成功
- **WHEN** 调用 `tryLock(key, leaseSeconds)`，该 `key` 当前没有被任何调用方持有锁
- **THEN** 方法立即返回一个非空的锁凭证，看门狗开始为该锁周期性续期

#### Scenario: 目标 key 已被持有时立即返回失败
- **WHEN** 调用 `tryLock(key, leaseSeconds)`，该 `key` 当前已被其他调用方持有锁且未过期
- **THEN** 方法立即返回空结果，不阻塞等待、不重试

### Requirement: 分布式锁——阻塞加锁（带超时重试）
`DistributedLockService` SHALL 提供 `tryLock(String key, long waitSeconds, long
leaseSeconds)` 重载方法：当目标锁当前被占用时，SHALL 在最长 `waitSeconds` 秒内以固定
间隔轮询重试，直到成功获取锁或等待超时；超时仍未获取到锁时返回空结果。轮询等待期间线程
被中断时，SHALL 恢复中断标志位并立即返回空结果，不 SHALL 吞掉中断信号。

#### Scenario: 等待期间锁被释放后成功获取
- **WHEN** 调用 `tryLock(key, waitSeconds, leaseSeconds)` 时该 `key` 已被占用，但在
  `waitSeconds` 秒内该锁被原持有者释放或自然过期
- **THEN** 方法在锁可用后的下一次轮询中返回一个非空的锁凭证并启动看门狗续期，不需要等到
  `waitSeconds` 用完

#### Scenario: 等待超时仍未获取到锁
- **WHEN** 调用 `tryLock(key, waitSeconds, leaseSeconds)`，该 `key` 在整个 `waitSeconds`
  等待期间始终被其他调用方持有
- **THEN** 方法在等待时长达到 `waitSeconds` 后返回空结果

#### Scenario: 等待期间线程被中断
- **WHEN** 调用 `tryLock(key, waitSeconds, leaseSeconds)` 在轮询等待过程中，执行线程被
  外部中断
- **THEN** 方法立即返回空结果，且当前线程的中断标志位被重新置位，不静默吞掉中断信号

### Requirement: 分布式锁——安全解锁
`DistributedLockService` SHALL 提供 `unlock(String key, String token)` 方法，仅当该
`key` 当前持有的锁凭证与传入的 `token` 一致时才释放锁（返回 `true`）；`key` 当前未被
锁定、或被锁定但持有的凭证与传入 `token` 不一致（如锁已过期后被其他调用方重新获取）时
SHALL NOT 释放该锁，返回 `false`，不 SHALL 抛出异常、不 SHALL 误删其他调用方持有的锁。
无论解锁是否成功，只要调用了 `unlock`，系统 SHALL 停止该 token 对应的看门狗续期任务
（不再有意义继续为一把调用方已表示不再需要的锁续期）。

#### Scenario: 使用加锁时返回的 token 解锁成功
- **WHEN** 调用方使用此前 `tryLock` 成功返回的 token 调用 `unlock(key, token)`，且该锁
  尚未过期、未被其他调用方重新获取
- **THEN** 方法返回 `true`，该锁被释放，其对应的看门狗续期任务被停止，其他调用方之后
  可以成功获取

#### Scenario: token 不匹配时拒绝解锁
- **WHEN** 调用方使用一个不是当前持有者的 token（如锁已过期后被另一调用方重新获取）调用
  `unlock(key, token)`
- **THEN** 方法返回 `false`，不释放该锁，不影响当前实际持有者的锁状态，但仍会停止传入
  token 对应的看门狗续期任务（如果存在）

### Requirement: 分布式锁——看门狗自动续期
加锁成功后，`DistributedLockServiceImpl` SHALL 在后台以 `leaseSeconds / 3` 秒
（向下取整，最小 1 秒）为周期，持续调用 `RedisUtils.compareAndExpire` 将该锁的过期时间
重新续到 `leaseSeconds`，直到该次加锁对应的 `unlock` 被调用为止。若某次续期时发现该 key
当前值已不是这次加锁的 token（`compareAndExpire` 返回 `false`），系统 SHALL 立即停止该
看门狗任务并记录一条 WARN 级别日志，不再继续对一把已经不属于自己的锁做无意义续期。应用
关闭时 SHALL 停止全部仍在运行的看门狗任务，不 SHALL 因为看门狗的后台线程而阻止应用正常
关闭。

#### Scenario: 持锁期间业务逻辑耗时超过 leaseSeconds 仍保持锁
- **WHEN** 调用方以 `leaseSeconds=10` 秒加锁成功后，业务逻辑实际执行了 30 秒才调用
  `unlock`，期间持续能正常访问 Redis
- **THEN** 该锁在这 30 秒期间被看门狗持续续期、始终未过期，其他调用方在这期间尝试加锁
  均失败，直到 30 秒后 `unlock` 被调用

#### Scenario: 调用 unlock 后看门狗停止续期
- **WHEN** 调用方成功 `unlock` 一把此前加锁成功的锁
- **THEN** 系统停止该锁对应的看门狗续期任务，该锁按 `unlock` 的原子删除结果被释放，不会
  再被之后已停止的续期任务重新写回

#### Scenario: 续期时发现锁已不属于自己则停止看门狗
- **WHEN** 看门狗某次续期调用 `compareAndExpire` 时，Redis 中该 key 当前值已经不是这次
  加锁的 token
- **THEN** 系统停止该看门狗任务，记录一条 WARN 日志，不再继续续期

#### Scenario: 应用关闭时停止全部看门狗任务
- **WHEN** 应用正常关闭，此时仍有若干把锁处于持有中、看门狗仍在运行
- **THEN** 系统在应用关闭流程中停止全部看门狗任务，不因看门狗的后台线程而阻塞或延迟
  应用关闭

### Requirement: 分布式锁——便捷执行封装
`DistributedLockService` SHALL 提供 `executeWithLock` 方法（`Runnable`/`Supplier<T>` 两
个重载），内部依次执行"阻塞加锁（`waitSeconds`/`leaseSeconds`）→ 执行调用方传入的业务
逻辑 → 无论业务逻辑正常完成还是抛出异常都释放锁"，加锁在 `waitSeconds` 内超时未获取到时
SHALL 抛出 `LockAcquireTimeoutException`（继承 `BusinessException`，复用现有全局异常
处理链路）且不 SHALL 执行传入的业务逻辑。

#### Scenario: 加锁成功后正常执行业务逻辑并释放锁
- **WHEN** 调用 `executeWithLock(key, waitSeconds, leaseSeconds, action)`，加锁在等待
  时间内成功
- **THEN** 系统执行 `action`，无论其正常返回还是抛出异常，锁都会在方法返回前被释放

#### Scenario: 加锁超时抛出统一异常且不执行业务逻辑
- **WHEN** 调用 `executeWithLock(key, waitSeconds, leaseSeconds, action)`，`waitSeconds`
  内始终未能获取到锁
- **THEN** 方法抛出 `LockAcquireTimeoutException`，`action` 不会被执行
