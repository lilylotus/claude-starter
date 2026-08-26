## Context

`SsoProtocolLogQueryServiceImpl.getPage` 目前用 `LambdaQueryWrapper` 动态拼接筛选条件，调用
`SsoProtocolLogMapper`（直接继承 `BaseMapper`，无自定义 XML）的 `selectPage`，查出
`SsoProtocolLogEntity` 分页结果后用 `SsoProtocolLogConvert`（MapStruct）转成
`SsoProtocolLogVO`，服务层再补一个 `resultLabel` 字段。本次要新增的 `userName`/
`deniedPolicyName` 两个字段来自另外两张表（`tab_user`、`tab_app_access_policy`）的关联查询，
不落在 `tab_sso_protocol_log` 本表，`LambdaQueryWrapper` 无法表达跨表 JOIN。项目既有约定
（见 `AppAccessEffectiveMapper.xml`、`OperationLogMapper.xml`）是这类多表关联查询写在
`resources/mybatis/mapper/*.xml` 里，不在 Java 服务层做批量查询再手工合并。见 proposal.md - Why。

## Goals / Non-Goals

**Goals:**
- 分页查询一次数据库往返内，通过 LEFT JOIN 把 `userName`、`deniedPolicyName` 一并查出，不引入
  N+1 查询或服务层批量查询再合并的写法。
- 保留现有分页查询的全部筛选条件（应用、协议类型、事件类型、结果、会话标识、时间范围）与排序
  行为（按调用时间降序）不变。
- 关联的用户/策略不存在时，两个新字段返回空，不影响原有字段（如 `userId`、`deniedPolicyId`
  本身）的取值。

**Non-Goals:**
- 不改变 `GET /api/sso-protocol-logs` 的请求参数、Controller 方法签名。
- 不改变 `tab_sso_protocol_log`/`tab_user`/`tab_app_access_policy` 的表结构，`userName`/
  `deniedPolicyName` 均为查询时关联得出的只读展示字段，不落库、不做快照。
- 不改动 `SsoProtocolLogRecorderImpl`/`SsoProtocolLogRecorder`（写入侧逻辑不变，本次只涉及
  查询/展示侧）。

## Decisions

### 1. 分页查询改为自定义 XML + MyBatis-Plus 分页插件，直接产出 VO

`SsoProtocolLogMapper` 新增方法：

```java
IPage<SsoProtocolLogVO> selectSsoProtocolLogPage(IPage<?> page, @Param("query") SsoProtocolLogQueryRequest query);
```

对应 XML（`resources/mybatis/mapper/SsoProtocolLogMapper.xml`）：`resultType` 直接是
`SsoProtocolLogVO`，主表 `tab_sso_protocol_log t` 上 `LEFT JOIN tab_user u ON u.id = t.user_id`、
`LEFT JOIN tab_app_access_policy p ON p.id = t.denied_policy_id`，`SELECT t.*, u.name AS
user_name, p.name AS deniedPolicyName`（下划线转驼峰由项目全局 `mybatis.conf` 的
`map-underscore-to-camel-case` 设置负责，`deniedPolicyName` 无对应下划线列、SQL 层直接给
驼峰别名）。筛选条件（应用/协议/事件类型/结果/会话标识/时间范围）与排序照搬现有
`LambdaQueryWrapper` 里的逻辑，改写成 `<where>`/`<if>` 动态 SQL，参考
`OperationLogMapper.xml` 的写法。方法签名沿用 `OperationLogMapper.selectOperationLogPage`
同款模式：第一个参数 `IPage<?> page` 触发 MyBatis-Plus 分页插件自动拼接
`LIMIT`/`COUNT`，不需要手写分页 SQL。

**为什么直接产出 VO、不产出 Entity 再转换**：`userName`/`deniedPolicyName` 不是
`SsoProtocolLogEntity` 的字段，如果 XML resultType 仍用 Entity 会装不下这两个字段，还是得在
服务层另外合并；直接让 XML 产出 VO（类似 `AppAccessEffectiveMapper.xml` 产出专用的
`EffectiveRawRow` DTO 的做法）可以把"关联查询"这一步完整留在 SQL 层，服务层不再需要
`SsoProtocolLogConvert`（MapStruct）做 Entity→VO 转换。

**替代方案考虑**：曾考虑保留 `LambdaQueryWrapper` 查 Entity，服务层再按 `userId`/
`deniedPolicyId` 批量查 `tab_user`/`tab_app_access_policy` 后在内存里合并——放弃，因为这正是
项目既有约定明确要求避免的"Java 服务层批量查询再手工合并"模式，且分页场景下这种模式还要
先拿到当页的 id 集合才能发起批量查询，多一次查询往返。

### 2. 移除 `SsoProtocolLogConvert`（MapStruct 转换器）

改造后 `SsoProtocolLogQueryServiceImpl.getPage` 不再需要 Entity→VO 转换（XML 直接产出
VO），`SsoProtocolLogConvert` 除本次改造前的 `getPage` 外没有其他调用方，改造后成为死代码，
直接删除该类（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/mapstruct/
SsoProtocolLogConvert.java`），不保留未使用的转换器。

### 3. 用户/策略缺失时的兜底行为：返回空，不回退展示原始 id

`userName`/`deniedPolicyName` 用 `LEFT JOIN`（而非 `INNER JOIN`），关联不到记录时该列为
`NULL`，`SsoProtocolLogVO` 对应字段保持 `null`；前端复用 `SsoProtocolLogDialog.vue` 已有的
`displayValue` 兜底函数展示为 `-`。不额外拼接"该用户/策略已删除"之类的提示文案，也不回退
展示 `userId`/`deniedPolicyId` 原始数值——`userId`/`deniedPolicyId` 字段本身仍然保留在 VO
里、前端如需排查仍可见，两个新字段的职责单纯是"能展示姓名/名称时展示"。

## Risks / Trade-offs

- [自定义 XML 分页 SQL 手写筛选条件，与原 `LambdaQueryWrapper` 的动态条件逻辑需要逐条对照
  搬迁，存在遗漏筛选条件或排序规则的风险] → 迁移时逐条比对 `SsoProtocolLogQueryServiceImpl`
  现有 `LambdaQueryWrapper` 的 6 个条件（appRefId/protocol/eventType/result/sessionId/
  startTime~endTime）与排序字段，现有单元测试
  `SsoProtocolLogQueryServiceImplTest`（如存在）覆盖到的筛选场景需要保持通过；若无现成测试
  覆盖，任务中补充针对新 XML 查询的筛选条件验证。
- [`LEFT JOIN tab_app_access_policy` 在 `deniedPolicyId` 为空的绝大多数记录（成功调用、或
  失败但拒绝原因与策略无关）上是无效关联] → 数据量级上可接受（协议调用日志按会话查询场景
  下单次分页结果集很小，且 `LEFT JOIN` 走主键索引，不是全表扫描），不做额外优化。

