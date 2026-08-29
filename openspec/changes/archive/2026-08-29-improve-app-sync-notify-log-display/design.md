## Context

通知日志接口目前直接把 `tab_app_notify_record.data_type` 和 `biz_id` 映射到 `AppNotifyRecordVO`，前端原样展示。业务名称没有持久化到通知记录中，但 ORG、USER、POSITION、APP、ROLE 均可由 `dataType + bizId` 定位当前业务数据。日志分页必须避免逐行查询导致 N+1。

## Goals / Non-Goals

**Goals:**

- 通知日志数据类型展示稳定的中文名称。
- 接口为每条可解析记录返回业务名称，前端统一展示 `id（名称）`。
- 批量解析一页日志的业务名称，并兼容历史空值、未知类型和已删除数据。

**Non-Goals:**

- 不修改通知发送负载、签名、重试状态机和筛选条件。
- 不新增数据库字段，也不回填历史日志名称快照。
- 不调整拉取日志页面。

## Decisions

### 1. 后端返回可空 `bizName`，前端负责组合展示

在 `AppNotifyRecordVO` 兼容新增 `String bizName`。后端只返回原始 id 和名称，前端展示为 `bizId（bizName）`；名称为空时退化为仅展示 `bizId`，bizId 也为空时展示 `-`。相比直接由后端返回格式化文本，这能保持接口字段可复用，并避免把界面标点固化进 API。

### 2. 按数据类型分组、按 id 批量解析名称

分页查询完成后，将当页记录按 `dataType` 分组并去重 bizId，各类型各执行一次批量查询，然后回填 `bizName`，避免逐条查询。名称规则为：ORG/USER/APP/ROLE 使用实体 `name`；POSITION 使用“用户名称-组织名称”作为可辨识名称。未知类型、空 id、查不到当前业务行时不抛错，返回空名称。

备选方案是在每次通知落库时保存名称快照。该方案对删除记录更完整，但需要迁移表结构并改变通知写入链路；本次仅改善管理端展示，采用无迁移的查询时解析方案。

### 3. 数据类型中文映射保留在前端展示层

前端定义 ORG=组织、USER=用户、POSITION=任职、APP=应用、ROLE=角色的映射函数。未知编码原样显示，空值显示 `-`，避免后端同时返回编码与展示文案造成多语言职责混杂。

## Risks / Trade-offs

- [业务数据被物理删除后无法解析名称] → 保留 bizId 并降级为仅展示 id；本次不引入名称快照迁移。
- [分页补充查询增加数据库访问] → 按类型分组批量查询，一页最多五次主表查询，禁止 N+1。
- [POSITION 没有独立名称字段] → 使用关联用户名称与组织名称组合，任一关联缺失时使用仍可获得的名称，均缺失则返回空。

## Implementation Verification

- `AppNotifyRecordServiceImpl` 按数据类型收集去重 id；ORG/USER/APP/ROLE 使用批量主键查询，POSITION 使用 `UserPositionMapper.selectPositionNamesByIds` 单次 JOIN 查询。
- 后端 `AppNotifyRecordServiceImplTest` 9 项通过，覆盖五类名称、重复 id 无 N+1 及兼容降级。
- 前端格式化测试 2 项通过，`npm run build` 完成 TypeScript 检查和 Vite 生产构建。
