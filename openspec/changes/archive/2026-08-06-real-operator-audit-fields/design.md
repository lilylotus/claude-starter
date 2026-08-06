## Context

见 proposal.md - Why。补充技术背景（研究已有代码得到）：

- `CurrentUserContext`（`auth/context/CurrentUserContext.java`）是一个线程级 `ThreadLocal<Long>`，`IdentityAuthFilter` 校验 `identity-token` 通过后 `setUserId`，请求结束 `finally` 里 `clear`。项目里没有任何 `@Async`/线程池/`CompletableFuture` 用法（已检索确认），所有写操作（含 Excel 批量导入 `BatchImportServiceImpl`/`ImportRowExecutor`，逐行同步调用各模块的 `create`/`update`）都运行在同一个被 `IdentityAuthFilter`处理过的请求线程上，因此在这些调用路径里 `CurrentUserContext.getUserId()` 总是有值，没有跨线程丢失的风险。
- `AuthServiceImpl.login` 已确认登录账号就是 `tab_user.code`（"账号使用 `tab_user.code`（用户编号）作为登录标识"），`UserMapper` 是标准 `@Mapper` Spring bean，可跨包注入（`DashboardRecentOperationServiceImpl` 已经是这个模式的先例：`CurrentUserContext.getUserId()` → `UserMapper.selectById` → `.getCode()`）。
- 排查确认另外两处"操作人"相关逻辑不是本次要修的"写死 admin"模式，不动：
  - `auth/service/impl/PasswordServiceImpl.java` 已经有一个 `currentOperator()` 私有方法，从 `CurrentUserContext.getUserId()` 动态取值，取不到时回退到常量 `SYSTEM_OPERATOR = "system"`；它写入的是用户 id 的字符串形式而不是账号编码，这是该类"故意不依赖 `user` 模块，避免模块间反向依赖"这条既有架构约束下的权衡结果，不是本次问题的一部分。
  - `loginlog/service/impl/LoginLogRecorderImpl.java` 记录的 `create_by` 直接来自登录尝试本身携带的账号字符串（`AuthServiceImpl.login` 传入的 `account`），本来就是动态值，不是硬编码。
- 14 个目标模块里，`OperationLogRecorderImpl` 比较特殊：它是本次问题在用户侧最直接的表现——`OperationLogRecorderImpl.record(...)` 目前 `.createBy(DEFAULT_OPERATOR)` 写入操作日志，改掉它之后 `dashboard-real-data` change 新增的"当前用户最近操作"接口（按 `createBy` 过滤）才能对非 `admin` 账号正常返回数据。其余 13 个模块修的是各自实体的 `create_by`/`update_by` 审计字段，属于同一类问题、同一种修法，一并处理。

## Goals / Non-Goals

**Goals:**
- 新增一个统一、可复用的"解析当前登录操作人账号编码"服务，替代 14 个模块里各自硬编码的 `DEFAULT_OPERATOR = "admin"`。
- 修复后，操作日志的 `create_by` 能反映真实登录账号，解决"当前用户最近操作"接口对非 `admin` 账号永远返回空列表的问题。

**Non-Goals:**
- 不迁移/回填历史数据（已经写死为 "admin" 的历史记录保持不变）。
- 不改动 `PasswordServiceImpl`/`LoginLogRecorderImpl`（见 Context，不属于本次问题）。
- 不引入操作人信息的缓存/请求级别复用机制（见 Decision 2，规模不足以需要）。

## Decisions

**新增 `cn.nihility.rbac.auth.service.CurrentOperatorService` 接口 + `impl.CurrentOperatorServiceImpl`，放在 `auth` 模块。**
`auth` 模块已经合法依赖 `user` 模块（`AuthServiceImpl` 已注入 `UserMapper`），且 `CurrentUserContext` 本身就定义在 `auth.context` 下，14 个目标模块要接入这个能力都需要反过来依赖 `auth` 模块——这是被依赖模块该在的位置（不能反过来把它放进 `user` 模块，因为 `auth` 依赖 `user` 是既有方向，放 `user` 里会导致 14 个模块之间出现不必要的耦合面）。实现：
```java
public interface CurrentOperatorService {
    String resolveCode();
}
```
`resolveCode()`：取 `CurrentUserContext.getUserId()`，为 `null` 时抛 `IllegalStateException`（不是 `BusinessException`——这不是一个应该展示给前端用户的业务错误，而是调用方脱离预期上下文的编程错误，`IllegalStateException` 不会被 `GlobalExceptionHandler` 特殊处理，会作为未捕获异常暴露为 500，这是预期行为，能在开发/测试阶段尽早暴露误用）；有值则 `userMapper.selectById(userId)`，取 `.getCode()`。`UserEntity` 查不到（理论上不会发生，`userId` 来自已校验通过的 `identity-token`）时同样抛 `IllegalStateException`，不做静默兜底。

**14 个目标模块的改法：删除 `DEFAULT_OPERATOR` 常量，注入 `CurrentOperatorService`，每个写方法内解析一次、复用到该方法内的 `createBy`/`updateBy`（不做跨方法/跨请求缓存）。**
例如 `RoleServiceImpl.create()` 原来是：
```java
entity.setCreateBy(DEFAULT_OPERATOR);
entity.setUpdateBy(DEFAULT_OPERATOR);
```
改为：
```java
String operator = currentOperatorService.resolveCode();
entity.setCreateBy(operator);
entity.setUpdateBy(operator);
```
同一个方法内如果需要给多个实体（如 `UserServiceImpl.create()` 同时创建用户和若干条任职记录）设置操作人，只调用一次 `resolveCode()`，把结果变量传给后续逻辑复用，不必每个实体各查一次。对于只更新单个字段的场景（如 `updateBy` 单独赋值一次），直接内联调用
`entity.setUpdateBy(currentOperatorService.resolveCode())`，不额外声明局部变量——同样是"每个写方法解析一次"，只是省去了只用一次的变量名。
若写方法内部委托给了同类的私有辅助方法（如 `AdminServiceImpl.create()`/`update()` 内部调用的
`syncRoles(adminId, roleIds, operator)`/`syncOrgScopes(adminId, orgScopes, operator)`，
`RoleServiceImpl` 的 `syncPermissions(roleId, permissionIds, operator)`，`UserServiceImpl` 的
`syncPositions(userId, positions, operator)`），改法是把调用方法已经解析好的 `operator`
作为参数传给这些辅助方法，而不是让辅助方法自己再调用一次 `resolveCode()`——既保持"每个写方法只解析一次"的约束在跨内部方法调用时依然成立，也避免了给这些私有辅助方法重复注入
`CurrentOperatorService` 依赖。取舍：不引入请求级缓存（如 `RequestScope` bean 或额外的
ThreadLocal），因为 `UserMapper.selectById` 是主键查询、开销很小（现有 debug 日志显示单次查询在
1ms 内完成），单个请求内最多重复调用几次，不构成性能问题；引入缓存层的复杂度收益不成比例。

**`OperationLogRecorderImpl` 同样改法，但影响面是"新产生的操作日志"而不是某个业务实体表。**
`OperationLogRecorderImpl.record(...)` 内 `.createBy(DEFAULT_OPERATOR)` 改为 `.createBy(currentOperatorService.resolveCode())`。这个改动是本次修复"当前用户最近操作接口对非 admin 账号返回空列表"问题的直接原因。

**受影响的单元测试改法：给测试类的 mock 依赖新增一个 `CurrentOperatorService` mock，`when(currentOperatorService.resolveCode()).thenReturn("test-operator")`（或每个测试场景自定义的账号编码字符串），断言从"等于 `DEFAULT_OPERATOR`/"admin""改为"等于 mock 返回的值"。**
不需要真的去操心 `CurrentUserContext` 的 ThreadLocal 生命周期——测试直接 mock 服务接口本身，比 mock `CurrentUserContext` + `UserMapper` 两层更简单、也更符合"单元测试只关心被测类自身逻辑，不关心它的依赖内部怎么实现"的原则。

## Risks / Trade-offs

- [`IllegalStateException` 会在真的发生"脱离已登录上下文调用"时让请求以 500 失败，而不是优雅降级] → 这是有意为之：静默降级成一个固定字符串正是当前问题的根源，本次要避免重蹈覆辙；测试代码里必须显式 mock `CurrentOperatorService`，这是修复本身要求的，不是缺陷。
- [14 个模块 + 对应测试的改动面较大，容易漏改或改出不一致的写法] → 每个模块的改法完全一致（删常量、注入服务、方法开头解析一次），机械性强，实现时逐个模块过一遍并跑 `./gradlew build` 兜底，不是需要逐个模块单独设计的问题。
- [历史操作日志的 `create_by` 仍然是 "admin"，修复只对新数据生效] → 已在 proposal.md 里明确声明不做数据回填，符合"无法逆向还原真实操作人"的现实约束。
