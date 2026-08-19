## Why

现有同步拉取能力依赖 `tab_app_data_change_log`——每次组织/用户/任职/应用/角色数据变更时，先按候选应用（数据域启用+组织范围+总开关）物化出多条变更记录，拉取接口再按这些记录做"按 id 查询最新一条"或"按序列号游标增量查询"。这套机制维护成本高：应用同步配置一会儿停用一会儿启用时，变更记录的产生/缺失会和实际数据状态逐渐脱节，难以保证一致性；变更记录本身只是"指针"，最终展示的数据仍然要现查业务表，等于维护了一份并不必要的中间状态。改为拉取接口直接分页查询组织/用户/任职/应用/角色业务表的当前数据，能从根本上消除这个一致性维护负担——业务表本身就是唯一数据源，不会存在"记录表说有变更、业务表却查不到/对不上"的情况。

## What Changes

- **删除 `tab_app_data_change_log` 表及相关代码**（`AppDataChangeLogEntity`/`Mapper`/`Service`/`ServiceImpl`、`NotifyTargetMapper` 中依赖它的部分保留但改造用途）：不再有任何"变更记录"的持久化。
- **拉取接口从"按 id / 按序列号查变更记录"改为"直接分页查询业务表当前数据"**：
  - 合并原来的两个接口（`GET /open/api/sync/pull/by-id`、`GET /open/api/sync/pull/by-sequence`）为一个新接口 `GET /open/api/sync/pull`，**不兼容旧接口**（旧两个路径直接下线）。
  - 分页：`page`（默认 1）、`pageSize`（默认取该应用该数据域配置的拉取分页大小，调用方可显式传入覆盖），按 `updateTime ASC, id ASC` 稳定排序；翻到最后一页后返回空列表，作为"拉取完了"的标识。
  - 增量拉取方式从"按序列号游标"改为"按更新时间范围"（`updateTimeFrom`/`updateTimeTo`），直接对应业务表的 `update_time` 列。
  - 支持按关键字段精确查询：主键 id 列表（`ids`）、业务编码列表（`codes`，组织编码/用户编码/应用编码/角色编码，任职数据类型不支持——没有编码字段）、用户手机号（`mobile`，仅用户数据类型）。
  - **返回结果不过滤 status**：状态为停用/已删除的记录照常返回，外部应用靠 `data` 里的 `status` 字段自行判断记录已停用/删除，不需要额外的"删除通知"机制。
  - 拉取响应不再携带 `sequence`/`operationType`（这两个字段的语义完全绑定在变更记录上，业务表的一次直接查询本身不携带"发生了什么操作"这个信息，该信息改由通知接口实时提供）。
- **通知触发方式从"消费已落库的变更记录"改为"数据变更时直接判定候选应用并发起通知"**：候选应用判定逻辑本身不变（数据域允许同步 + 应用同步总开关开启 + 同步方式为通知 + 组织范围匹配），只是不再有中间的持久化步骤；通知请求体新增被变更对象的业务编码字段（`bizCode`，任职数据类型无业务编码字段时为空），新增/编辑/启用/停用/删除五种操作类型的区分保留不变。
- **拉取日志（`tab_app_pull_record`）去掉"拉取方式"字段**：原来区分"按 id / 按序列号"两种拉取方式，现在只有一种统一的分页拉取，该字段失去意义，一并从表结构、后端 `PullMode` 常量、管理端"拉取日志"表格列中移除。
- 应用配置页面"拉取日志"子 tab 的表格去掉"拉取方式"列，其余列（数据类型、请求摘要、返回条数、时间）不变；请求摘要文案改为反映新的分页/过滤参数。
- **实现完成后基于反馈的两轮修正**：① 拉取响应从裸记录数组改为带分页信息的整页对象（顶层 `dataType`/`page`/`pageSize` + `records`），每条记录直接是合并了 `bizId`/`bizCode`/`bizStatus`/`updateTime` 四个固定键的业务字段 Map，不再是"元信息+data"的嵌套结构；② 新增字典（DICT）作为第六个可拉取数据域（拉取字典项，合并 `dictTypeCode` 固定键），任职（POSITION）记录额外合并关联用户编码 `userCode` 固定键（详见 design.md Decision 2/7/8）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-sync-notify-pull`：核心能力重写——移除"变更记录落库与全局递增序列号"需求；「组织/用户/任职/应用/角色数据变更产生同步事件」「通知模式下的变更通知」两个需求改为描述"直接判定候选应用并发起通知"而非"落库后消费"；移除「按数据类型与 id 拉取变更数据」「按序列号批量拉取变更数据」两个需求，替换为新的「分页拉取数据域当前数据」需求；「拉取日志记录与查询」需求去掉"拉取方式"维度。「拉取结果按字段映射转换」「通知与拉取请求的签名与验签」两个需求不变。
- `app-api-credentials`：「应用管理前端"配置"入口与页面」需求中"拉取日志"子 tab 的表格列描述去掉"拉取方式"列。

## Impact

- **数据库**：新增 Flyway 迁移脚本（`V7__...sql`），`DROP TABLE tab_app_data_change_log`；`tab_app_notify_record` 去掉 `change_log_id` 列及其索引（改用已有的 `data_type`+`biz_id` 定位）；`tab_app_pull_record` 去掉 `pull_mode` 列。
- **后端删除**：`backend/.../sync/changelog/**`（entity/mapper/service/serviceImpl 整包）、`backend/.../sync/pull/record/constant/PullMode.java`、`backend/src/main/resources/mybatis/mapper/AppDataChangeLogMapper.xml`。
- **后端改造**：
  - `NotifyTargetMapper`（候选应用查询）：语义从"变更记录候选应用"改为"通知候选应用"，SQL 增加 `sync_mode='NOTIFY'` 条件（PULL 模式应用不再需要参与候选匹配）。
  - `DomainChangeEventProcessor`：不再调用 `AppDataChangeLogService.record()`，直接对候选应用逐个调用通知（复用原 `AppDataChangeLogServiceImpl.filterByOrgScope` 的组织范围过滤逻辑，迁移到新的判定组件里）。
  - `AppNotifyServiceImpl`/`NotifyPayload`：payload 去掉 `sequence`，新增 `bizCode`；触发入口从"传入一条已落库的变更记录"改为"传入 `DomainChangeEvent` + 目标应用"。
  - `AppNotifyRecordEntity`：去掉 `changeLogId` 字段。
  - 新增分页拉取服务/查询组件：为组织/用户/任职/应用/角色五个业务表分别提供"按数据域启用+组织范围+可选过滤条件+分页"的查询（新的 MyBatis Mapper XML，标准可移植 SQL，不使用 MySQL 8.0+ 专属语法）。
  - `SyncPullService`/`SyncPullServiceImpl`/`SyncPullController`：合并为一个分页拉取方法/接口；`SyncPullRecordVO` 字段调整为 `dataType`/`bizId`/`bizCode`/`updateTime`/`data`。
  - `AppPullRecordEntity`/`AppPullRecordService`：去掉 `pullMode` 字段，`requestSummary` 内容改为反映新参数。
  - `SyncDomain.CHANGE_LOG_DOMAINS` 常量重命名（不再与"变更记录"绑定，如改名 `SYNC_PULL_DOMAINS`）。
- **前端**：`frontend/src/types/app.ts`（`AppPullRecordRow` 去掉 `pullMode`，去掉 `PULL_MODE_LABELS`）、`frontend/src/views/application/app/AppConfigView.vue`（"拉取日志"表格去掉"拉取方式"列）、`frontend/src/api/app.ts`（如有 `getAppPullRecordPage` 请求参数变化需同步，预期不需要——该接口是管理端查日志，不是对外拉取接口本身）。
- **对外契约（破坏性）**：`GET /open/api/sync/pull/by-id`、`GET /open/api/sync/pull/by-sequence` 下线，替换为 `GET /open/api/sync/pull`；已接入的外部系统需要同步改造调用方式（本次不做过渡期兼容）。
