## Why

`app-sync-notify-pull-api` change 已经把对外拉取接口（`/open/api/sync/pull/by-id`、
`/open/api/sync/pull/by-sequence`）落地并合入主干（未归档），但实际联调后发现两个问题：

1. 两个拉取接口的必填查询参数（`dataType`/`bizIds`/`fromSequence`）缺失或格式错误时
   （如 `fromSequence=abc`），Spring MVC 在参数绑定阶段抛出的
   `MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException` 没有被
   `GlobalExceptionHandler` 识别，落入兜底的 `Exception.class` 处理器，统一返回
   `{ code: 500, message: "服务器内部错误" }`，调用方完全不知道是哪个参数出了问题——
   实测复现：`GET /open/api/sync/pull/by-sequence?dataType=ORG`（缺 `fromSequence`）、
   `fromSequence=abc`（非数字）、`GET /open/api/sync/pull/by-id?bizIds=1,2`（缺
   `dataType`）均返回该 500 响应。
2. 两个拉取接口当前返回的 `data` 字段来自 `tab_app_data_change_log.data_snapshot`——
   变更发生那一刻写入的 JSON 快照，而不是组织/用户/任职/应用当前的真实数据。这与
   `design.md` Decision 9 当初"只读变更记录表、不重查业务表"的取舍冲突：该记录一旦
   写入之后业务表若发生了快照之外的变化（如该行后续被别的事件覆盖前，快照本身没问题，
   但产品期望的语义是"拉取到的应该是这条业务记录当前的真实状态"，而不是历史某一时刻的
   冻结快照），需要真正关联组织/用户/任职/应用四张业务表取当前数据。另外，
   `AppDataChangeLogMapper.xml` 的 `selectLatestByBizIds` 使用了 `ROW_NUMBER() OVER
   (PARTITION BY ...)` 窗口函数，本地实际数据库是 MySQL 5.7.44（不支持窗口函数，8.0+
   才支持），实测 `GET /open/api/sync/pull/by-id?dataType=ORG&bizIds=7,8` 同样返回
   `{ code: 500 }`（SQL 语法错误被兜底异常处理器吞掉，看不到真实原因）——这也是本次一并
   修复的动机之一，切到自连接写法后不再依赖窗口函数。

## What Changes

- `GlobalExceptionHandler` 新增 `MissingServletRequestParameterException`/
  `MethodArgumentTypeMismatchException` 的处理器，返回 400 且 `message` 明确指出具体是
  哪个参数缺失/格式不对（如"缺少必填参数：fromSequence"/"参数 fromSequence 格式不正确"）。
- 新增业务对象快照解析组件：按 `dataType`（ORG/USER/POSITION/APP/ROLE）分发到对应业务表
  的 `Mapper#selectById(bizId)`，把查到的实体转换为字段快照 Map（复用既有
  `DomainSnapshotSupport.snapshot`），取代直接解析 `data_snapshot` JSON 列。
- `SyncPullServiceImpl`：`pullByBizIds`/`pullBySequence` 返回结果的 `data` 字段改为从上述
  组件按 `dataType`+`bizId` 现查业务表得到，`sequence`/`dataType`/`operationType`/`bizId`/
  `occurredAt` 仍然来自变更记录表（这几个字段本就是"这次变更"的元信息，与"当前数据"是
  两回事，不受影响）；业务表中已查不到该 `bizId`（理论上不会发生，四张业务表均为逻辑
  删除，行不会被物理删除，此处仅作为防御性兜底）时，跳过该条记录并记录一条警告日志，
  不让调用方拿到残缺/空 `data` 的记录。
- `AppDataChangeLogMapper.xml#selectLatestByBizIds` 改写为不依赖窗口函数的 `GROUP BY
  biz_id HAVING/自连接 MAX(id)` 写法，兼容 MySQL 5.7。
- **BREAKING**: 无（拉取接口的请求/响应字段结构不变，只是 `data` 字段取值来源从"历史快照"
  改为"当前业务表状态"；参数错误响应从 `{code:500}` 变为 `{code:400}` 且 `message` 更
  具体，属于错误信息修复，不改变响应结构）。

## Capabilities

### Modified Capabilities
- `app-sync-notify-pull`：按 id / 按序列号拉取接口的 `data` 字段语义从"变更时刻的字段
  快照"改为"被变更对象当前在组织/用户/任职/应用业务表中的真实数据"。
- `backend-common-utilities`：`GlobalExceptionHandler` 新增对请求参数缺失/类型不匹配的
  专项处理，返回具体是哪个参数的问题，而不是笼统的服务器内部错误。

## Impact

- 后端 · 新增：`cn.nihility.rbac.sync.transform.BizSnapshotResolver`（按 `dataType` 分发到
  `OrgMapper`/`UserMapper`/`UserPositionMapper`/`AppMapper`/`RoleMapper` 现查业务表，输出
  字段快照 Map）。
- 后端 · 修改：`common/exception/GlobalExceptionHandler`（新增两个异常处理器）；
  `sync/openapi/service/impl/SyncPullServiceImpl`（`toVO` 改用 `BizSnapshotResolver`）；
  `resources/mybatis/mapper/AppDataChangeLogMapper.xml`（`selectLatestByBizIds` 去窗口
  函数化）。
- 数据库：无新增迁移（不改表结构）。
- 前端：无改动（拉取接口面向外部系统调用，管理端没有消费这两个接口的页面）。
- 权限资源：无改动。
