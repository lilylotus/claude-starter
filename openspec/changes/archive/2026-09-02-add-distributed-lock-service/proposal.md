## Why

后续会陆续出现"同一资源可能被并发请求同时修改"的场景（如应用同步、审批流程状态流转等），
需要一个可复用的分布式锁公共服务提前就绪，避免每个业务模块各自手写加锁逻辑、各自踩一遍
"忘记解锁"“误删别人的锁"之类的坑。当前项目 `common/` 下已有 `RedisUtils` 等 Redis 基础
设施，但没有任何加锁能力。本次先把这个公共服务补上，具体业务接入是后续 change 的事，本次
不绑定任何具体调用方。

## What Changes

- 在 `common/` 下新增 `DistributedLockService`（Spring 注入式服务，风格对齐
  `SsoSessionService`），基于项目现有 `StringRedisTemplate`/`RedisUtils` 实现，不引入
  Redisson 等新依赖。
- 提供非阻塞 `tryLock`（立即返回是否加锁成功）与阻塞版 `tryLock`（带等待超时、内部轮询
  重试）两种加锁方式，以及配套的 `unlock`（持有者校验后原子删除，防止误删他人的锁）。
- 提供 `executeWithLock` 便捷封装：加锁 → 执行业务逻辑 → `finally` 解锁，加锁超时抛出
  统一的运行时异常，减少调用方忘记解锁的风险。
- 新增看门狗（watchdog）自动续期：锁加锁成功后，只要调用方还没有显式 `unlock`，系统
  SHALL 在后台按固定周期自动续期该锁的过期时间，使调用方不再需要精确预估业务逻辑耗时来
  设置 `leaseSeconds`；`unlock` 时停止续期；进程崩溃/失联导致续期任务也一并停止时，锁
  仍会在最后一次续期设置的 `leaseSeconds` 后自然过期，作为兜底，不会永久占用。
- `RedisUtils` 新增三个通用原子操作：`setIfAbsent`（`SET key value NX PX`，供加锁使用）、
  `compareAndDelete`（按预期值原子比较后删除，Lua 脚本实现，供解锁使用）、
  `compareAndExpire`（按预期值原子比较后重设过期时间，Lua 脚本实现，供看门狗续期使用），
  均可被本次以外的未来场景复用。
- 明确不支持的能力（见 design.md Non-Goals）：不支持锁重入、不是跨多个独立 Redis 实例的
  Redlock 强一致算法，只是基于项目现有单一 Redis 实例的建议性（advisory）锁，足够满足
  当前项目内"避免同一资源并发写"的需求。

## Capabilities

### Modified Capabilities
- `backend-common-utilities`：新增分布式锁公共服务能力，`RedisUtils` 新增
  `setIfAbsent`/`compareAndDelete` 两个通用原子操作。

## Impact

- 新增代码：`backend/src/main/java/cn/nihility/rbac/common/lock/`
  （`DistributedLockService` 接口 + `impl/DistributedLockServiceImpl`，内含看门狗续期用的
  后台 `ScheduledExecutorService`）、`common/exception/LockAcquireTimeoutException`。
- 修改代码：`common/util/RedisUtils.java`（新增 `setIfAbsent`/`compareAndDelete`/
  `compareAndExpire`）。
- 无数据库改动、无前端改动、无新增第三方依赖。
- 本次不改动任何现有业务模块的调用方（当前没有调用方），纯新增基础设施，不影响现有功能。
