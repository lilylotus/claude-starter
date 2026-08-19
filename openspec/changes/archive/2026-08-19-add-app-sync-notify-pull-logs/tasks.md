## 1. 数据库迁移

- [x] 1.1 新增 Flyway 迁移脚本 `V5__add_app_sync_notify_pull_logs.sql`：
  `ALTER TABLE tab_app_notify_record ADD COLUMN data_type VARCHAR(20) NULL, ADD COLUMN
  biz_id BIGINT NULL, ADD COLUMN notify_url VARCHAR(255) NULL` + 新增索引
  `idx_tab_app_notify_record_app_time (app_ref_id, create_time)`；新建
  `tab_app_pull_record` 表（`id`/`app_ref_id`/`pull_mode`/`data_type`/
  `request_summary`/`result_count`/审计字段 + `idx_tab_app_pull_record_app_time
  (app_ref_id, create_time)`），列名核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字。

## 2. 通知日志：写入侧

- [x] 2.1 `cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity` 新增
  `dataType`/`bizId`/`notifyUrl` 三个字段。
- [x] 2.2 `AppNotifyServiceImpl.saveNotifyRecord()`（及其调用方 `notifyOneApp()`）
  补充这三个值：`dataType`/`bizId` 取自触发本次通知的 `AppDataChangeLogEntity`，
  `notifyUrl` 取自发起请求前读到的 `target.getNotifyUrl()`。

## 3. 拉取日志：新增实体/写入侧

- [x] 3.1 新增 `cn.nihility.rbac.sync.pull.record` 包（或类似命名）：
  `AppPullRecordEntity`（对应 `tab_app_pull_record`）、`AppPullRecordMapper`
  （`extends BaseMapper`）、`AppPullRecordService`/`AppPullRecordServiceImpl`
  （写入方法 + 分页查询方法）。
- [x] 3.2 `SyncPullServiceImpl.pullByBizIds`：构造完响应后、返回前，try/catch 包裹
  写入一条拉取日志（`pullMode=BY_ID`，`requestSummary` 记 bizId 个数，
  `resultCount` 为实际返回条数），写入失败只记 WARN 日志，不影响响应。
- [x] 3.3 `SyncPullServiceImpl.pullBySequence`：同上，`pullMode=BY_SEQUENCE`，
  `requestSummary` 记 `fromSequence`/`limit`，`dataType` 未传时记为空。

## 4. 管理端查询接口

- [x] 4.1 `AppNotifyRecordMapper`/`AppPullRecordMapper` 各新增分页查询方法，支持
  `app_ref_id` 精确匹配 + `create_time` 范围过滤（+ 通知日志额外支持
  `notify_status` 精确匹配），复用 `idx_..._app_time` 索引。
- [x] 4.2 新增 `cn.nihility.rbac.app.sync.controller.AppSyncLogController`：
  `GET /api/apps/{id}/config/sync/notify-records`、
  `GET /api/apps/{id}/config/sync/pull-records`，均返回
  `cn.nihility.rbac.common.result.PageResult<T>`，补充 springdoc-openapi 注解。
- [x] 4.3 新增对应的响应 VO（`AppNotifyRecordVO`/`AppPullRecordVO`）。

## 5. 前端

- [x] 5.1 `frontend/src/api/app.ts` 新增 `getAppNotifyRecordPage`/
  `getAppPullRecordPage` 接口封装；`frontend/src/types/app.ts` 新增对应类型。
- [x] 5.2 `AppConfigView.vue` 新增"通知日志""拉取日志"两个标签页（与"基础信息"
  "同步配置""认证管理"同级），懒加载（首次激活才请求），过滤表单（时间范围 +
  通知日志额外的状态下拉）+ 分页表格，交互模式参考
  `OperationLogManagementView.vue`（过滤表单/分页组件用法）与
  `UpstreamSourceConfigView.vue`"同步记录"标签页（嵌入配置页的标签页布局）。

## 6. 文档同步

- [x] 6.1 确认 `权限资源.txt` 无需更新（复用现有 `AppManagement:app:config` 页面级
  权限，不新增权限码）。

## 7. 测试

- [x] 7.1 通知日志写入：验证落库记录包含正确的 `dataType`/`bizId`/`notifyUrl`；
  验证日志写入异常不影响通知请求本身完成。
- [x] 7.2 拉取日志写入：按 id / 按序列号两种拉取方式各验证落库记录的
  `pullMode`/`requestSummary`/`resultCount`；验证日志写入异常不影响拉取响应。
- [x] 7.3 管理端查询接口：按应用 id 查询、按时间范围过滤、通知日志按状态过滤，
  分页参数校验。
