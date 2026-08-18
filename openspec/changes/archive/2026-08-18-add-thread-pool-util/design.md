## Context

后端已有一批全局工具类放在 `common/util/` 下（`RedisUtils`、`JacksonUtils`、
`HttpClientUtils` 等），均以静态方法/静态持有实例的方式对外暴露，不注册为 Spring
bean、不做构造器注入，业务代码直接静态调用。线程池工具类沿用同样的风格，保持
`common/util/` 包内一致的使用方式。

## Goals / Non-Goals

**Goals:**
- 提供一个进程内唯一的、固定大小（核心=最大=4）线程池，供业务代码统一提交异步任务。
- 队列容量固定为 2048（有界队列），避免任务无限堆积导致 OOM。
- 队列满且线程数达到上限时立即拒绝新任务（`AbortPolicy`），让调用方能感知到过载，
  而不是无声丢弃或无限阻塞。
- 提供简洁的静态方法：`execute(Runnable)`、`submit(Runnable)`、
  `submit(Callable<T>)`。

**Non-Goals:**
- 不做多套线程池（如 IO 密集型/CPU 密集型分开），只做一个统一的全局池，后续如有
  差异化需求再拆分。
- 不接入 Spring 的 `ThreadPoolTaskExecutor`/`@Async`，保持和现有 `RedisUtils` 等
  工具类一致的“静态单例、非 Spring bean”风格。
- 不做动态调参（如通过配置中心调整核心线程数），当前是固定常量。

## Decisions

1. **实现方式：JDK 原生 `ThreadPoolExecutor` + 静态单例持有**
   - 与仓库里 `RedisUtils`/`JacksonUtils` 等工具类保持同样的“静态字段持有实例 +
     静态方法暴露”写法，不引入 Spring 生命周期管理，调用方直接
     `ThreadPoolUtils.execute(...)`。
   - 备选方案（Spring `ThreadPoolTaskExecutor` 并注册为 bean、构造器注入）与项目
     现有全局工具类的风格不一致，故不采用。

2. **队列类型：`ArrayBlockingQueue(2048)`**
   - 需求明确"队列为 2048"（有界），`ArrayBlockingQueue` 是标准的有界阻塞队列实现，
     容量固定、语义清晰。
   - 不使用 `LinkedBlockingQueue`（虽然也可指定容量上限，但默认无界，语义上容易
     被后续误改成无界，风险更高）。

3. **拒绝策略：自定义 `RejectedExecutionHandler`（记录日志后抛出 `RejectedExecutionException`）**
   - 对应需求"超过拒绝"：队列满 + 线程数达上限时，先以 WARN 级别记录一条拒绝日志
     （包含线程池当前活跃线程数、队列积压数、已完成任务数等信息，便于定位过载原因），
     再抛出 `RejectedExecutionException`，调用方能第一时间感知过载并做相应处理
     （重试/降级），而不是静默丢弃（`DiscardPolicy`）或改由调用线程执行
     （`CallerRunsPolicy`，可能拖慢业务主线程）。
   - 不直接用 JDK 内置的 `ThreadPoolExecutor.AbortPolicy`，因为它只抛异常、不记录任何
     诊断信息，运维排查过载原因时缺少上下文；自定义 handler 在抛出前记录日志，异常
     行为与 `AbortPolicy` 保持一致。

4. **线程命名与 daemon 属性**
   - 自定义 `ThreadFactory`，线程名加 `thread-pool-` 前缀 + 序号，便于日志和线程
     dump 定位问题线程。
   - 设为非 daemon 线程，避免应用还有异步任务在跑时 JVM 提前退出（与业务线程池
     的常规实践一致）。

5. **keepAliveTime**
   - 核心线程数=最大线程数=4（固定大小池），keepAliveTime 对该配置不生效
     （核心线程默认不回收），设为 0 即可，语义上更清晰地表明这是一个固定大小池。

## Risks / Trade-offs

- [风险] 固定 4 个线程 + 2048 队列容量是写死的常量，无法根据业务量动态调整
  → 缓解：当前需求就是固定参数；如后续需要按环境区分，再引入配置项，不在本次范围。
- [风险] 队列满时 `AbortPolicy` 会抛出未受检异常 `RejectedExecutionException`，
  调用方若不处理会导致调用链路报错
  → 缓解：这是需求明确要求的行为（"超过拒绝"），由调用方按需 try/catch 并做降级
  处理；工具类本身不吞异常。
- [风险] 单一全局线程池被多个业务场景共用，某个耗时任务可能挤占其余任务的执行
  资源（无法隔离）
  → 缓解：属于 Non-Goals 中明确排除的"多线程池隔离"场景，后续如有需要再拆分独立
  线程池。

## Open Questions

（无）
