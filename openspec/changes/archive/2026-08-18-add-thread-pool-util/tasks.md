## 1. 线程池工具类实现

- [x] 1.1 新建 `backend/src/main/java/cn/nihility/rbac/common/util/ThreadPoolUtils.java`：
      静态持有一个 `ThreadPoolExecutor` 单例（核心线程数=最大线程数=4，
      keepAliveTime=0，`ArrayBlockingQueue(2048)` 作为工作队列）。
- [x] 1.2 自定义 `ThreadFactory`：线程命名带 `thread-pool-` 前缀 + 递增序号，
      设置为非 daemon 线程。
- [x] 1.3 暴露静态方法 `execute(Runnable)`、`submit(Runnable)`、
      `submit(Callable<T>)`，内部委托给单例线程池。
- [x] 1.4 类注释、方法注释按 `java-code-style` skill 规范补全（4 空格缩进、
      K&R 大括号、小驼峰命名等）。
- [x] 1.5 自定义 `RejectedExecutionHandler`（替换 `ThreadPoolExecutor.AbortPolicy`）：
      拒绝任务时先以 WARN 级别记录日志（包含拒绝原因、线程池当前活跃线程数、
      队列积压数等状态信息），再抛出 `RejectedExecutionException`，日志用
      `org.slf4j.Logger` 静态字段（参考 `RsaJdkUtils`/`Sm2JdkUtils` 的写法）。

## 2. 验证

- [x] 2.1 `./gradlew build` 编译通过（`backend/` 目录下执行）。
- [x] 2.2 视情况补充/运行单元测试，验证：正常提交任务能被执行；
      队列与线程都饱和时提交任务会抛出 `RejectedExecutionException`（已有测试
      `ThreadPoolUtilsTest` 覆盖，拒绝时的日志记录已人工确认输出，未额外做日志断言）。
