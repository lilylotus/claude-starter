## Context

`app-sync-notify-pull-api` change 落地的两个对外拉取接口（`SyncNotifyPullController`）
目前的数据来源是 `tab_app_data_change_log.data_snapshot`——每次组织/用户/任职/应用/角色
写操作发布领域事件时，用 `DomainSnapshotSupport.snapshot(entity)` 在写操作发生的那一刻
把实体序列化为 JSON 存进这一列。联调后确认这不符合预期：外部应用调用拉取接口，期望拿到
的是"这个 id 现在长什么样"，而不是"最近一次变更发生时长什么样"（两者在正常情况下应该
一致，但语义上前者才是"当前数据"，后者是"历史快照"，产品侧明确要改成前者）。

同时发现 `AppDataChangeLogMapper.xml#selectLatestByBizIds` 用了 `ROW_NUMBER() OVER
(PARTITION BY ...)` 窗口函数，本地实际数据库版本 MySQL 5.7.44 不支持（8.0+ 才支持），
实测该查询直接抛 SQL 语法错误。这个错误又因为 `GlobalExceptionHandler` 没有覆盖到具体
异常类型，被兜底处理器吞成了一句不知所云的"服务器内部错误"——这本身也是本次要修的参数
报错问题的一个真实案例（虽然不是参数错误，是同一类"异常被吞"问题）。

## Goals / Non-Goals

**Goals:**
- 拉取接口返回的 `data` 字段改为现查组织/用户/任职/应用业务表的当前数据。
- 拉取接口的必填参数缺失/类型错误时，返回明确指出具体参数的 400 错误，而不是 500。
- 顺带修掉 `selectLatestByBizIds` 的 MySQL 8.0 专属语法，兼容项目实际使用的 MySQL 5.7。

**Non-Goals:**
- 不改变 `tab_app_data_change_log.data_snapshot` 列本身的写入逻辑——变更记录表继续按原
  样落库快照（`DomainChangeEventProcessor`/`AppDataChangeLogServiceImpl#record` 不变），
  这一列仍然保留供人工排查问题时对比"当时 vs 现在"，只是拉取接口不再读它当 `data` 用。
- 不改变角色（ROLE）数据域——ROLE 已有 `RoleMapper`，一并纳入按 `dataType` 分发的解析器，
  不需要额外设计。
- 不引入通用的"全局参数校验 AOP/切面"机制，只新增两个具体异常类型的
  `@ExceptionHandler`，与 `GlobalExceptionHandler` 现有的
  `MethodArgumentNotValidException`/`BusinessException` 处理器风格保持一致。

## Decisions

### 1. 新增 `BizSnapshotResolver`：按 `dataType` 分发到业务表现查

**决定**：新增 `cn.nihility.rbac.sync.transform.BizSnapshotResolver`（与
`FieldMappingTransformer` 同包，同属"拉取结果组装"链路上的一环）：

```java
@Component
@RequiredArgsConstructor
public class BizSnapshotResolver {
    private final OrgMapper orgMapper;
    private final UserMapper userMapper;
    private final UserPositionMapper userPositionMapper;
    private final AppMapper appMapper;
    private final RoleMapper roleMapper;

    public Map<String, Object> resolve(String dataType, Long bizId) {
        Object entity = switch (dataType) {
            case SyncDomain.ORG -> orgMapper.selectById(bizId);
            case SyncDomain.USER -> userMapper.selectById(bizId);
            case SyncDomain.POSITION -> userPositionMapper.selectById(bizId);
            case SyncDomain.APP -> appMapper.selectById(bizId);
            case SyncDomain.ROLE -> roleMapper.selectById(bizId);
            default -> null;
        };
        return entity == null ? null : DomainSnapshotSupport.snapshot(entity);
    }
}
```

`dataType` 落在 `SyncDomain.CHANGE_LOG_DOMAINS`（`assertValidDataType` 已经保证，见现有
`SyncPullServiceImpl`），因此 `switch` 的 `default` 分支实际不会被业务路径触发，只作为
穷尽性兜底。返回 `null` 表示业务表里已经查不到这一行（理论上不会发生——四张业务表都是
逻辑删除，见 `OrgStatus`/`UserStatus`/`AppStatus` 等 `DELETED` 状态码，行本身不会物理
消失；仅作为防御性分支）。

复用 `DomainSnapshotSupport.snapshot`（`app-sync-notify-pull-api` change 已引入）而不是
另写一套转换：它已经是"实体 → camelCase 属性名 Map"的标准做法，字段命名与
`tab_metadata_field.field_code`（字段映射的源字段编码）对齐，`FieldMappingTransformer`
不需要跟着改。

**为什么不直接在 `SyncPullServiceImpl` 里 `switch`，而是单独抽一个组件**：
`SyncPullServiceImpl` 已经装配了 3 个协作者（`AppSyncDomainConfigMapper`/
`AppDataChangeLogService`/`FieldMappingTransformer`），再塞 5 个业务表 Mapper 会让这个类
的职责边界模糊（拉取编排逻辑 vs 业务表访问细节）；单独抽出便于未来"角色数据域下线"/
"新增数据域"时改动局部化，也方便单测（可以只 mock `BizSnapshotResolver` 而不用关心
5 个 Mapper 各自的行为）。

### 2. `SyncPullServiceImpl#toVO` 的字段来源拆分：元信息仍来自变更记录，`data` 改为现查

**决定**：`sequence`（`= log.getId()`）、`dataType`、`operationType`、`bizId`、
`occurredAt`（`= log.getCreateTime()`）继续从 `AppDataChangeLogEntity` 取——这些字段
描述的是"这一次变更事件"本身的元信息，与"当前数据长什么样"是两个独立的问题，变更历史
仍然只能从变更记录表得到。只有 `data` 字段的来源从 `JacksonUtils.toObj(log.getDataSnapshot(),
...)` 改为 `bizSnapshotResolver.resolve(log.getDataType(), log.getBizId())`，再送进
`FieldMappingTransformer.transform` 做字段映射转换（这一步不变）。

`resolve` 返回 `null`（业务表查不到该行）时，跳过这条记录——实际实现中 `toVO` 的返回类型
改为 `Optional<SyncPullRecordVO>`，`resolve` 返回 `null` 时 `toVO` 内部记一条 `log.warn`
（`changeLogId`/`dataType`/`bizId`）后直接返回 `Optional.empty()`；`pullByBizIds`/
`pullBySequence` 的 stream 处理统一用 `.filter(Optional::isPresent).map(Optional::get)`
排除掉这些空值（而不是让 `resolve` 的 `null` 直接流到 stream 里再用
`filter(Objects::nonNull)` 过滤）。这样不让调用方拿到一条 `data` 为 `null`/空 Map 的
"看起来正常但没数据"的记录，语义上更清晰（"这条要么给你完整当前数据，要么因为异常情况
被跳过，不会给你半条"），同时 `Optional` 返回类型让"这条记录可能被跳过"这件事在
`toVO` 的方法签名上就可见，不用翻方法体才知道。

### 3. `selectLatestByBizIds` 去窗口函数化

**决定**：把

```sql
SELECT t.* FROM (
    SELECT l.*, ROW_NUMBER() OVER (PARTITION BY l.biz_id ORDER BY l.id DESC) AS rn
    FROM tab_app_data_change_log l WHERE ...
) t WHERE t.rn = 1
```

改写为自连接 + `GROUP BY MAX(id)`（MySQL 5.1+ 通用写法，不依赖窗口函数）：

```sql
SELECT l.id, l.data_type, l.biz_id, l.operation_type, l.data_snapshot,
       l.create_by, l.create_time, l.update_by, l.update_time
FROM tab_app_data_change_log l
INNER JOIN (
    SELECT biz_id, MAX(id) AS max_id
    FROM tab_app_data_change_log
    WHERE data_type = #{dataType} AND biz_id IN (...)
    GROUP BY biz_id
) latest ON latest.biz_id = l.biz_id AND latest.max_id = l.id
ORDER BY l.biz_id ASC
```

`data_snapshot` 列本身仍然 `SELECT` 出来（`AppDataChangeLogEntity` 的既有字段不变，
`selectBySequence` 等其余查询不受影响），只是 `SyncPullServiceImpl` 不再读取这个字段
的值来组装 `data`（见 Decision 2）——继续查出来是为了保持 `AppDataChangeLogEntity` 映射
不需要拆两套 `resultMap`，也方便日后人工核对"当时快照 vs 现在数据"。

### 4. `GlobalExceptionHandler` 新增两个异常处理器

**决定**：新增

```java
@ExceptionHandler(MissingServletRequestParameterException.class)
public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
    return Result.error(VALIDATION_ERROR_CODE, "缺少必填参数：" + ex.getParameterName());
}

@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return Result.error(VALIDATION_ERROR_CODE, "参数 " + ex.getName() + " 格式不正确");
}
```

`VALIDATION_ERROR_CODE`（400）复用现有 `MethodArgumentNotValidException` 处理器的同一个
状态码常量，风格与既有的"参数校验失败统一 400"保持一致；两个处理器都放在兜底的
`handleException(Exception.class)` 之前（Spring 按异常类型精确匹配优先于父类/兜底，
声明顺序不影响匹配结果，但沿用文件里其余处理器"具体异常在前、兜底异常在后"的排列习惯）。

这两个异常类型不是 `sync` 模块专属——任何 Controller 的 `@RequestParam` 缺失/类型不匹配
都会走到这里，属于 `common/` 全局基础设施的正确性修复，不是给某个模块开小灶。

## Risks / Trade-offs

- **[Trade-off] 拉取到的 `data` 与变更记录时的快照可能不完全一致** → 这正是本次改动想要
  的效果（"当前数据"而不是"历史快照"），但需要在 spec 里把措辞从"变更时的字段快照"改成
  "被变更对象当前的业务数据"，避免调用方按旧文档理解产生歧义。
- **[Risk] 现查业务表意味着拉取接口的 QPS 会直接压到 `tab_org`/`tab_user` 等业务表** →
  之前是纯读变更记录表，现在每条记录都多一次业务表 `selectById`（按主键查，有索引，单次
  开销很小）；`by-sequence` 批量拉取时是 N 次单行查询而不是一次 `IN` 查询，N 通常
  ≤ `pageSize`（默认 20），可接受，不在本次做批量化优化（如按 `dataType` 分组后一次
  `selectBatchIds`），后续如果实测有性能问题再单独优化。

## Migration Plan

无数据库结构变更，纯代码修复；上线即时生效，不需要迁移已有数据（`data_snapshot` 列的
历史数据不受影响，只是不再被拉取接口读取）。
