## Why

后端目前没有统一的异步任务线程池，各处若需要异步执行（如同步任务、通知回调等）只能各自
`new Thread` 或临时创建 `ExecutorService`，缺乏统一的线程数/队列容量/拒绝策略管控，
存在线程泛滥、任务无界堆积的风险。需要一个全局线程池工具类，提供统一、可控的异步任务执行入口。

## What Changes

- 新增 `common/util/ThreadPoolUtils.java` 全局线程池工具类：
  - 核心线程数 = 4，最大线程数 = 4（固定大小）。
  - 有界任务队列，容量 = 2048（`ArrayBlockingQueue`）。
  - 队列满且线程数达到上限时，采用拒绝策略（`ThreadPoolExecutor.AbortPolicy`），
    直接抛出 `RejectedExecutionException`，不静默丢弃任务。
  - 线程池以静态单例形式持有，提供 `execute(Runnable)` / `submit(Runnable)` /
    `submit(Callable<T>)` 等方法供业务代码直接调用，无需自行管理线程池生命周期。
  - 自定义线程工厂，线程命名带前缀（便于日志/线程 dump 定位），并设置为非
    daemon 线程。

## Capabilities

### New Capabilities
（无，归入下方已有的 `backend-common-utilities` 能力）

### Modified Capabilities
- `backend-common-utilities`: 新增统一的全局固定大小线程池工具类 `ThreadPoolUtils`
  （4 核心/最大线程、2048 有界队列、超限拒绝策略），供业务代码提交异步任务。
(无)

## Impact

- 新增文件：`backend/src/main/java/cn/nihility/rbac/common/util/ThreadPoolUtils.java`。
- 不涉及数据库、API、前端改动。
- 不新增第三方依赖（仅使用 JDK `java.util.concurrent` 标准库）。
