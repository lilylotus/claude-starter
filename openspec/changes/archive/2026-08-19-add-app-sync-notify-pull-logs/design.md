## Context

见 proposal.md - Why。现状（`cn.nihility.rbac.sync.*` / `cn.nihility.rbac.app.sync.*`）：

- `tab_app_notify_record`（`V1__init_schema.sql`）：`change_log_id`/`app_ref_id`/
  `notify_status`/`http_status`/`error_msg` + 审计字段，只有 `change_log_id`/
  `app_ref_id` 两个索引，没有支持按 `create_time`/`notify_status` 过滤排序的索引。
  写入点 `AppNotifyServiceImpl.notifyOneApp()`（POST 到 `target.getNotifyUrl()`）→
  `saveNotifyRecord()`，`createBy`/`updateBy` 固定为 `"system"`（后台线程，无登录态）。
- 拉取完全无落库：`SyncPullServiceImpl.pullByBizIds`/`pullBySequence` 只读不写。
- 管理端"应用配置页面访问"（`AppManagement:app:config`）是页面级权限，覆盖
  `AppConfigView.vue` 所有标签页；标签页内的写操作各自复用更细的 `:editXxx` 权限点，
  查看类标签页（如即将新增的日志）不需要额外权限点，`UpstreamSourceConfigView.vue`
  的"同步记录"标签页是这个约定的既有先例。
- 前端已有两种日志列表 UI 范式可参考：`OperationLogManagementView.vue`（独立路由页面 +
  过滤表单 + 分页表格 + 详情弹窗）、`UpstreamSourceConfigView.vue`"同步记录"标签页
  （嵌在配置页内的标签页，本地分页状态，无过滤表单）。本次日志标签页嵌在
  `AppConfigView.vue` 内，形态更接近后者，但按用户诉求需要按时间范围（+ 通知日志按
  状态）过滤，所以过滤表单部分参考前者。

## Goals / Non-Goals

**Goals:**
- 通知日志：`tab_app_notify_record` 补充数据类型/bizId/回调地址快照三列，新增支持
  按时间范围、状态过滤的查询接口与索引。
- 拉取日志：新表 + 写入点（覆盖现有 `pullByBizIds`/`pullBySequence` 两个接口）+ 支持
  按时间范围过滤的查询接口。
- 两个标签页嵌入 `AppConfigView.vue`，复用页面级权限，不新增权限点。
- 日志写入失败不阻塞通知/拉取主流程。

**Non-Goals:**
- 不涉及日志保留/清理/归档策略（proposal.md 已声明，留给独立 change）。
- 不涉及尚未实现的分页拉取接口（`paginate-app-sync-pull` change 的范围，二者是并行、
  互不阻塞的独立 change；本 change 完成后如果 `paginate-app-sync-pull` 后续落地，那个
  change 需要自行决定是否要给新的分页拉取接口也接入本 change 建立的拉取日志表，不在本
  change 里预先处理）。
- 不改变通知/拉取的业务逻辑本身（签名校验、组织范围过滤、字段映射转换等），只加记录
  与查询。
- 不做导出（CSV/Excel）功能，只是列表查看。

## Decisions

### 1. `tab_app_notify_record` 新增列：`data_type`/`biz_id`/`notify_url`

三列均可空（历史数据这三列为空，不做历史回填——历史记录本来就查不到这些信息，回填只能
造假数据，不如诚实地留空，前端对空值做"-"占位展示）。写入点
`AppNotifyServiceImpl.saveNotifyRecord()` 新增参数，从触发本次通知的
`AppDataChangeLogEntity`（`dataType`/`bizId`）与 `target.getNotifyUrl()`（回调发起前
读到的地址，不是发起后再查一次，避免并发场景下地址被管理员改过导致快照与实际请求不符）
两处取值。

新增索引：`idx_tab_app_notify_record_app_time (app_ref_id, create_time)` 支持"某应用
按时间倒序查通知日志"这个最常见的查询模式；状态过滤直接在这个索引基础上加
`notify_status` 等值条件（选择性够用，不需要单独复合索引）。

### 2. 新表 `tab_app_pull_record`：记录已有两个拉取接口的调用

```sql
CREATE TABLE tab_app_pull_record (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    app_ref_id      BIGINT NOT NULL COMMENT '发起拉取的应用 id（tab_app.id）',
    pull_mode       VARCHAR(20) NOT NULL COMMENT '拉取方式：BY_ID/BY_SEQUENCE',
    data_type       VARCHAR(20) NULL COMMENT '请求的数据类型，按序列号拉取未传时为空',
    request_summary VARCHAR(255) NULL COMMENT '请求参数摘要：按 id 拉取记 bizId 个数，按序列号拉取记 fromSequence/limit',
    result_count    INT NOT NULL DEFAULT 0 COMMENT '本次返回的记录条数',
    create_by/create_time/update_by/update_time,
    PRIMARY KEY (id),
    KEY idx_tab_app_pull_record_app_time (app_ref_id, create_time)
) COMMENT = '应用拉取变更数据请求记录，仅用于问题排查/展示';
```

`request_summary` 用一个可读字符串摘要而不是完整参数 JSON——按 id 拉取时调用方可能一次
传几十上百个 `bizIds`，全量记录会让这张本来就会快速增长的表单行更大；只记"本次请求了
N 个 bizId"这种摘要足够排查用（真要看具体是哪些 id，结合 `result_count`/`create_time`
配合应用侧自己的调用日志排查）。`create_by`/`update_by` 固定为 `"open-api"`（开放接口
调用，无管理端登录态，同 `AppNotifyServiceImpl` 固定 `"system"` 的既有模式，只是换一个
更贴切的标识值区分"系统内部产生"与"外部应用调用产生"）。

写入点：`SyncPullServiceImpl.pullByBizIds`/`pullBySequence` 各自在构造完响应结果后、
返回前插入一条记录；插入包在 try/catch 里，记录失败只打 WARN 日志，不影响本次拉取
响应本身返回给调用方（design 目标"日志写入失败不阻塞主流程"）。

*备选方案：像 `tab_app_notify_record` 一样把请求参数也做成结构化列（如
`biz_ids TEXT`/`from_sequence BIGINT`/`limit_value INT` 分列存）。* 放弃原因：两个拉取
接口的参数形状不同（by-id 是变长 id 列表，by-sequence 是两个数字），分列会有大量按
`pull_mode` 区分才有意义的列，不如一个摘要字符串统一处理，简单且足够满足"排查用"这个
定位（同 `tab_app_notify_record` 表注释"仅用于问题排查/展示"的既有措辞）。

### 3. 管理端查询接口：新增 `AppSyncLogController`

放在 `cn.nihility.rbac.app.sync.controller`（与 `AppSyncConfigController` 同包），
复用同样的"接口层不新增权限校验注解，前端路由/按钮层面控制"约定：

- `GET /api/apps/{id}/config/sync/notify-records?page=&pageSize=&notifyStatus=&startTime=&endTime=`
- `GET /api/apps/{id}/config/sync/pull-records?page=&pageSize=&startTime=&endTime=`

均返回 `cn.nihility.rbac.common.result.PageResult<T>`；`page`/`pageSize` 复用管理端
现有分页参数惯例（默认第 1 页，`pageSize` 默认与上限参考现有列表页的既有做法，如
`OperationLogController` 的默认值）；`startTime`/`endTime` 为可选的 ISO-8601 时间，
省略时不做时间范围限制。

### 4. 前端：`AppConfigView.vue` 新增两个标签页

新增"通知日志""拉取日志"两个 `el-tab-pane`，与"基础信息""同步配置""认证管理"同级；
每个标签页内：顶部过滤表单（时间范围 `el-date-picker type="datetimerange"`，通知日志
额外加状态下拉）+ 查询/重置按钮 + `el-table`（通知日志列：数据类型/bizId/状态 tag/
HTTP 状态码/回调地址/错误摘要/时间；拉取日志列：拉取方式/数据类型/请求摘要/返回条数/
时间）+ `el-pagination`，交互模式对齐 `OperationLogManagementView.vue`（复用
`frontend/src/constants/pagination.ts` 的默认分页大小/可选项常量）。两个标签页首次
激活时才发起查询（懒加载，与"数据范围"区块下"字段映射"等子标签页的既有"首次激活加载"
模式一致），不在页面初始 `onMounted` 里一起拉取。

## Risks / Trade-offs

- [`tab_app_pull_record` 由外部应用调用驱动写入，写入频率不受管理端控制，如果某个
  应用轮询过于频繁（比如每秒拉一次），这张表的增长速度可能比 `tab_app_notify_record`
  更快] → 已在 Non-Goals 声明保留策略留给独立 change；本 change 至少保证查询接口有
  `(app_ref_id, create_time)` 索引支撑，分页查询不会因为全表扫描而变慢。
- [历史通知记录的 `data_type`/`biz_id`/`notify_url` 三列为空] → 前端按空值展示"-"，
  proposal.md 已声明不做历史回填。
- [`notify_url` 快照与"发起回调那一刻"绑定，如果同一次同步事件因为网络重试等原因被
  多次调用 `notifyOneApp`（当前代码没有重试逻辑，这个风险在现状下不成立，仅在未来引入
  重试机制时需要重新评估）] → 当前不涉及，记录于此供后续留意。
