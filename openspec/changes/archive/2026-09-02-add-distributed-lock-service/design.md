## Context

项目 `common/` 目录下已有 `RedisUtils`（静态工具类，封装 `opsForValue()`/`opsForHash()` 等
样板代码）、`RedisObjectUtils`（对象读写），供各业务模块直接复用，见 `backend-common-
utilities` 能力。项目里目前已经有多处"用 Redis 存短生命周期状态、UUID 做 opaque token"的
既定模式（`SsoSessionService`、`CasTicketService`、`OAuthTokenService`、
`QrcodeSessionService`），但还没有任何"互斥锁"语义的能力——现有这些都是"存一份状态、按
key 查询/覆盖"，不需要处理"同一时刻只能有一个调用方持有"的并发互斥问题。

本次要补的是一个通用的分布式锁公共服务，服务于*未来*会出现的并发资源竞争场景（如应用同步
过程中避免同一应用被两个定时任务同时同步、审批流程状态流转避免并发提交），本次不绑定任何
具体调用方，只把公共服务准备好。

项目当前只有一个逻辑 Redis 实例（`spring.data.redis.*` 单机配置，非 Redis Cluster/哨兵
多实例部署），没有 Redisson 依赖。

## Goals / Non-Goals

**Goals:**
- 提供非阻塞、阻塞（带超时重试）两种加锁方式，以及安全的解锁（只能释放自己持有的锁）。
- 提供 `executeWithLock` 便捷封装，降低调用方忘记解锁的风险。
- 加锁成功后由后台看门狗（watchdog）自动续期，调用方不需要精确预估业务逻辑耗时来设置
  `leaseSeconds`，只要还没有 `unlock`、进程仍存活且能访问 Redis，锁就不会因为业务逻辑
  执行时间较长而被提前释放。
- 不引入新的第三方依赖，基于项目现有 `StringRedisTemplate`（经 `RedisUtils`）实现。
- API 风格对齐项目现有 Spring 注入式服务（如 `SsoSessionService`），而不是静态工具类，
  方便未来调用方通过构造器注入使用，测试时也更容易替换/校验。

**Non-Goals:**
- 不支持锁重入（同一线程/调用方对同一 key 重复加锁会像陌生调用方一样正常参与竞争、可能
  自己等自己超时）——如果未来某个场景明确需要重入语义，届时再评估是否引入 Redisson 或在
  本服务上叠加一层基于 `ThreadLocal` 计数的重入包装，本次不预先设计。
- 看门狗只做"续期"，不做更复杂的健康检查/主动上报（如探测持锁方所在业务逻辑是否仍在正常
  运行、锁状态可视化面板等）——这些超出"防止锁因为耗时估算不准被提前释放"这个核心诉求，
  本次不做。
- 不是跨多个独立 Redis 实例的 Redlock 强一致算法，只是基于项目现有单一 Redis 实例
  （逻辑上单点）的建议性（advisory）锁：所有调用方都必须遵守"先加锁再操作"的约定才有效，
  不能防止绕过本服务直接操作资源的代码路径；在 Redis 主从切换等极端场景下理论上存在锁语义
  被破坏的可能，这是单实例方案的已知局限，接受该权衡（与项目现有 SSO 会话、票据类状态同样
  只存在单一 Redis 实例、不做多实例容错是一致的既有假设）。
- 不在本次绑定任何具体业务调用方，不修改任何现有业务模块代码。

## Decisions

### Decision 1：不引入 Redisson，基于现有 `StringRedisTemplate` 手写
加锁用 `SET key value NX PX <leaseMillis>`（`StringRedisTemplate.opsForValue().
setIfAbsent(key, value, timeout, unit)`，Spring Data Redis 原生支持的原子操作，一次
往返即可完成"不存在则设置并带过期时间"，不需要额外 Lua 脚本）；解锁用 Lua 脚本原子比较
"当前值等于加锁时生成的 token 才删除"，避免 A 持有的锁因为业务超时被服务端判定过期、
被 B 重新获取后，A 的 `unlock` 误删 B 的锁。

**备选方案**：引入 Redisson——功能更完整（内置 watchdog 自动续期、可重入、公平锁），但
需要新增 `build.gradle` 依赖，按项目约定改动前需要用户确认；用户本次明确选择"先用现有
`StringRedisTemplate` 手写，不引入新依赖"。自动续期本身通过 Decision 6 在不引入 Redisson
的前提下自行实现；锁重入、公平锁等 Redisson 的其余能力仍不在本次范围，如果后续需要，再
单独评估引入 Redisson（那会是一次独立的 change，因为涉及依赖变更需要重新走确认流程）。

### Decision 2：`RedisUtils` 新增两个通用原子操作，锁的具体逻辑放在新的 `DistributedLockService`
`RedisUtils` 新增：
- `setIfAbsent(String key, String value, long timeout, TimeUnit unit)`：包一层
  `opsForValue().setIfAbsent(...)`，返回是否设置成功（`true` = 之前不存在、本次设置生效；
  `false` = key 已存在，未做任何修改）。这是一个通用原子操作，不止加锁场景能用（如"首次
  执行标记"之类的幂等控制），放在 `RedisUtils` 里符合它"收敛各业务模块重复样板代码"的既有
  定位。
- `compareAndDelete(String key, String expectedValue)`：Lua 脚本
  `if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1])
  else return 0 end`，通过 `StringRedisTemplate.execute(RedisScript, keys, args)`
  执行，返回是否实际删除。同样是通用原子操作（"按预期值比较后删除"），不专属于锁场景。
- `compareAndExpire(String key, String expectedValue, long timeout, TimeUnit unit)`：
  Lua 脚本 `if redis.call('get', KEYS[1]) == ARGV[1] then return
  redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end`，与 `compareAndDelete`
  同样的比较模式、只是把"删除"换成"重设过期时间"，返回是否实际续期成功；供 Decision 6
  的看门狗续期使用，同样是通用原子操作，不专属于锁场景（未来其他"按 token 续期一个短生命
  周期状态"的场景可直接复用）。

`DistributedLockService`/`DistributedLockServiceImpl` 放在新增的 `common.lock` 包下，
只调用上述两个 `RedisUtils` 方法组合出加锁/解锁/阻塞重试/便捷封装的业务逻辑，不直接持有
`StringRedisTemplate`（与 `SsoSessionService` 只通过 `RedisUtils` 访问 Redis 的既有风格
一致）。

**备选方案**：把 Lua 脚本执行逻辑直接写在 `DistributedLockServiceImpl` 里、注入
`StringRedisTemplate`——不采用，`compareAndDelete` 是通用原语，未来其他"按 token/版本号
比较后删除"的场景可以直接复用 `RedisUtils`，没必要每个用到的模块各自维护一份 Lua 脚本
字符串常量。

### Decision 3：Key 命名与 token 生成
所有锁 key 落地时统一加前缀 `lock:`（调用方只需传业务含义的 key 后缀，如
`app:sync:{appId}`，服务内部拼成 `lock:app:sync:{appId}`），与项目现有 `sso:`/`chat:`
等前缀风格一致，避免与其他模块的 key 空间冲突。加锁成功时生成的 token 复用项目既有
"UUID 去横线"风格（同 `SsoSessionService`/`QrcodeSessionService`），作为该次加锁的凭证，
`unlock` 时必须提供同一个 token 才能释放成功。

### Decision 4：阻塞版 `tryLock` 的重试策略
阻塞版 `tryLock(key, waitSeconds, leaseSeconds)` 内部固定用 100ms 轮询间隔重试
`setIfAbsent`，直到成功或总等待时长超过 `waitSeconds`（用 `System.nanoTime()` 记录
截止时间，每次循环判断是否已超过，不用简单的"重试次数 × 间隔"避免因单次 Redis 调用耗时
累积导致总等待时间明显偏离调用方预期）。轮询期间线程处于可中断的 `Thread.sleep`，若被
中断则恢复中断标志位并立即返回加锁失败（不 SHALL 吞掉 `InterruptedException`）。100ms
是一个固定的实现细节常量，不做成可配置项——这是当前没有具体调用方、无法预判合理配置范围
下的保守选择，真正接入业务后如果这个间隔不合适，可以在接入的那次 change 里按需调整或
改造成可配置。

### Decision 5：`executeWithLock` 便捷封装与异常
新增 `LockAcquireTimeoutException extends BusinessException`（放
`common/exception` 下，复用现有全局异常处理链路，不需要新增
`@RestControllerAdvice` 处理分支），`executeWithLock(key, waitSeconds, leaseSeconds,
action)` 内部调用阻塞版 `tryLock`，超时未获取到锁时抛出该异常；获取到锁后在
`try { action.run/get } finally { unlock }` 中执行，保证无论业务逻辑正常返回还是抛异常
都会释放锁。提供 `Runnable`（无返回值）与 `Supplier<T>`（有返回值）两个重载。

### Decision 6：看门狗（watchdog）自动续期，加锁即启动、解锁即停止
`tryLock`（非阻塞、阻塞两个重载）加锁成功后，`DistributedLockServiceImpl` SHALL 立即为
该次加锁启动一个后台续期任务：以 `leaseSeconds / 3` 为周期（向下取整，最小 1 秒），周期性
调用 `RedisUtils.compareAndExpire(lockKey, token, leaseSeconds, SECONDS)` 把该锁的过期
时间重新续到 `leaseSeconds`；`unlock` 成功或失败（无论 token 是否匹配，只要调用了
`unlock` 就意味着调用方认为自己不再需要这把锁）时 SHALL 停止对应的续期任务。若某次续期
调用返回 `false`（说明 Redis 中的值已经不是这次加锁的 token——可能是锁已自然过期并被其他
调用方重新获取，理论上不应该发生但作为防御性判断），看门狗 SHALL 立即停止自身、记录一条
WARN 日志，不再继续对一把已经不属于自己的锁做无意义续期。

续期任务由 `DistributedLockServiceImpl` 内部持有的一个单线程 `ScheduledExecutorService`
统一调度，具体用 `scheduleAtFixedRate`（而不是 `scheduleWithFixedDelay`）——续期任务本身
耗时极短（一次 Redis 往返），固定速率调度能让续期节奏更贴近预期周期，不会因单次任务执行
耗时被动顺延、逐渐偏离预期节奏（新增守护线程，非 `ThreadPoolUtils` 现有的固定大小业务线程
池——续期任务是周期性的维护性质任务，语义与"业务异步任务"不同，且需要能够按 key/token
单独取消，不适合复用 `ThreadPoolUtils` 仅支持一次性 `execute`/`submit` 的接口）；线程设置为 daemon（与
`ThreadPoolUtils` 特意使用非 daemon 线程的既有约定相反——续期任务不是必须跑完才能让 JVM
退出的关键业务工作，不应该反过来阻止应用关闭），并通过 `@PreDestroy` 在应用关闭时
`shutdownNow()`，避免应用重启/测试场景下的线程泄漏。每个进行中的加锁用一个
`ConcurrentHashMap<String token, ScheduledFuture<?>>` 记录对应的续期任务句柄，供
`unlock` 时查找并 `cancel`。

**备选方案 A（续期周期等于 `leaseSeconds`，快到期才续）**：不采用，如果续期请求恰好因为
网络抖动等原因失败或延迟，`leaseSeconds` 内没有第二次机会重试，锁可能意外过期；
`leaseSeconds / 3` 是 Redisson 看门狗采用的同一经验值，留出至少两次重试机会。
**备选方案 B（复用 `ThreadPoolUtils` 全局线程池 + 显式 `Thread.sleep` 循环模拟周期任务）**：
不采用，`ThreadPoolUtils` 是为一次性异步任务设计的固定大小池，用它模拟长期占用的周期性
任务会长期占满其中一个宝贵的核心线程槽位，且没有现成的"取消"语义，不如用专门的
`ScheduledExecutorService`。

## Risks / Trade-offs

- [看门狗续期任务本身依赖进程存活、能访问 Redis；进程崩溃/GC 长停顿/网络分区导致续期
  任务来不及执行时，锁仍会在最后一次成功续期设置的 `leaseSeconds` 后自然过期] → 这是
  "不做强一致 Redlock、只做单实例建议性锁"的既定取舍下的合理兜底行为：正常情况下锁不会
  因为业务逻辑耗时长而被提前释放，只有在持锁方本身不健康时才会退化为"过期兜底"，与不加
  看门狗时相比是纯粹的能力增强，没有引入新的更差情形。
- [看门狗为每把持有中的锁额外占用一个 `ScheduledFuture` 与一次周期性 Redis 调用，锁数量
  很多时会增加后台负载] → 当前无具体调用方、预期并发持锁数量不大，暂不做批量合并优化；
  真正接入高频场景后如果续期调用量成为瓶颈，可以在那次接入的 change 里评估优化（如按
  `leaseSeconds` 分桶批量续期）。
- [单 Redis 实例故障切换场景下的锁语义弱一致] → 与项目现有 SSO 会话等状态的既有假设
  一致，接受该风险，本次不解决。
- [100ms 固定轮询间隔在真正接入某个高频竞争场景后可能不是最优值] → 当前无具体调用方，
  先用一个保守的固定值起步，后续接入时按实际场景调整，不预先做成配置项造成"没人用的
  配置项"。

## Migration Plan

纯新增代码，无数据库迁移、无需灰度、无回滚顾虑（没有现有调用方依赖它）。合并后即可用，
后续业务模块按需注入 `DistributedLockService` 使用。

## Open Questions

无。所有关键技术选型（实现方式、封装形式、阻塞/非阻塞）已在提出本 change 前与用户确认。
