## Why

应用数据同步（通知模式 `syncMode=NOTIFY`、拉取模式 `syncMode=PULL`）目前对管理员完全不
可观测：通知模式虽然每次回调尝试都落了一条 `tab_app_notify_record`，但没有任何管理端
接口/界面能查看这些记录，且该表字段过于简单（只有状态、HTTP 状态码、错误摘要），排查
"当时到底回调了哪个地址、是哪条数据的变更触发的"这类问题时信息不够；拉取模式则完全没有
任何记录机制，管理员无法知道外部应用有没有在正常拉取、拉的是什么数据、拉了多少条。本次
在应用配置页面新增"通知日志"与"拉取日志"两个可查看的日志列表，让管理员能够自助排查同步
问题，不用再靠翻数据库或联系开发。

## What Changes

- `tab_app_notify_record` 新增三列：`data_type`（数据类型）、`biz_id`（被变更对象 id）、
  `notify_url`（本次回调实际使用的地址快照，不再依赖 `tab_app_config.notify_url` 的
  当前值——避免管理员事后改了回调地址，历史记录却显示成新地址，产生误导）；新增按
  `create_time` 排序/过滤、按 `notify_status` 过滤所需的索引。
- 新增 `tab_app_pull_record` 表，记录每一次外部应用调用现有拉取接口（按 id 拉取、按
  序列号批量拉取）的请求：归属应用、拉取方式（按 id / 按序列号）、请求参数摘要（数据
  类型、`bizIds` 或 `fromSequence`/`limit`）、返回记录条数、请求时间。本次只对接现有的
  两个拉取接口加日志，不涉及尚未实现的分页拉取接口（`paginate-app-sync-pull` change 的
  范围）。
- 新增两个管理端只读查询接口：按应用 id 分页查询通知日志、按应用 id 分页查询拉取日志，
  均支持按时间范围过滤，通知日志额外支持按状态（成功/失败）过滤。
- 应用配置页面新增"通知日志""拉取日志"两个标签页（与"基础信息""同步配置""认证管理"
  同级），复用现有的"应用配置页面访问"权限点，不新增权限点（与页面上其余标签页的访问
  控制方式一致）。
- 日志写入失败不 SHALL 影响通知/拉取主流程本身——通知记录已经是这个模式（写入放在
  实际回调之后），拉取日志新写入点同样遵循这个约定。
- 不涉及日志保留/清理策略（历史数据会持续增长，保留策略留给后续独立的 change 处理）。

## Capabilities

### New Capabilities

（无新增能力域，通知/拉取日志归入现有 `app-sync-notify-pull` 能力域下的新增需求）

### Modified Capabilities

- `app-sync-notify-pull`: 新增"通知日志记录与查询"需求（`tab_app_notify_record` 字段
  增强 + 管理端查询接口）；新增"拉取日志记录与查询"需求（新表 + 写入 + 管理端查询接口）。

## Impact

- 后端：`cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity`/
  `service.impl.AppNotifyServiceImpl`（写入时补充新增三列）、新增
  `cn.nihility.rbac.sync.pull.record`（或类似包）下的 `AppPullRecordEntity`/Mapper/
  Service、`cn.nihility.rbac.sync.openapi.service.impl.SyncPullServiceImpl`（两个既有
  拉取方法各补一次日志写入）、新增管理端查询 Controller（如
  `AppSyncLogController`，或挂在现有 `AppSyncConfigController` 下）、Flyway 迁移脚本
  `V5__*.sql`（`tab_app_notify_record` 新增列+索引、新建 `tab_app_pull_record` 表）。
- 前端：`frontend/src/views/application/app/AppConfigView.vue` 新增两个标签页（列表 +
  时间范围/状态过滤 + 分页，参考 `OperationLogManagementView.vue`/
  `UpstreamSourceConfigView.vue`"同步记录"标签页的既有交互模式）、
  `frontend/src/api/app.ts` 新增两个查询接口封装、`frontend/src/types/app.ts` 新增
  对应类型。
- 文档：`openspec/specs/app-sync-notify-pull/spec.md` 同步新增需求。
- 数据库：`tab_app_notify_record` 新增列+索引（不改变现有列语义），新建
  `tab_app_pull_record` 表。
- 无新增第三方依赖。
