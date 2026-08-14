## Why

`identity-upstream-data-sync` 的"同步执行记录"能力目前有三个体验/信息缺口，均由本次真实使用后反馈：
1. 数据域即使本轮取数结果为空（上游没有新数据），也照样写入一条 `total=0/success=0/fail=0` 的执行记录——定时轮询频繁触发时，这类"空跑"记录会迅速淹没真正有意义的记录，管理员需要的是"这次同步到底处理了什么"，不是"系统又空转了一次"。
2. 同步记录列表（`GET .../sync-records`）一次性返回该数据源全部数据域的历史全量，没有分页；随着运行时间变长，这个列表会无限增长，前端表格全量渲染既拖慢页面也不好翻看。
3. 记录里只有汇总计数（`total_count`/`success_count`/`fail_count`）与截断到 5 条的失败摘要文本，看不到"具体是哪些数据被同步了""每一行的处理结果与原因"——无论成功还是失败，管理员都无法回溯某一次同步实际拉取、处理的原始数据。

## What Changes

- **跳过空结果记录**：数据域成功取数但本轮拉取到 0 行数据时，SHALL NOT 写入一条同步执行记录（不产生"空跑"历史噪音）；该数据域的"上次同步时间"仍然更新，只是不落一条记录。已有的两类"真正的问题"仍然照常记录且不受影响：取数阶段异常（`FAILED`，`total=0`）、数据域未配置主键字段被前置拦截（`FAILED`，`total=0`）——这两类都是需要管理员关注的实际问题，不是"没有数据"，语义上不应该和"这次啥也没有"混为一谈。
- **同步记录列表分页**：`GET /api/identity/upstream-sources/{id}/sync-records` 改为分页查询（`page`/`pageSize` 请求参数，返回 `PageResult`），前端"同步记录"分区表格底部增加分页控件，与仓库里其余管理列表页（组织/用户/角色等）的分页交互保持一致。
- **记录每行处理明细**：新增"同步执行记录明细"，粒度为"一次执行记录下的每一行原始数据"，无论该行处理成功还是失败都记录（行序号、该行的原始上游数据、处理状态、失败原因）。新增分页查询接口按记录 id 查看明细列表；前端同步记录表格每行增加"查看明细"操作，弹出对话框展示该次执行的分页明细表格。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `identity-upstream-data-sync`：
  - "同步执行记录"需求：补充"取数结果为空时不记录"的规则；查询接口从返回全量列表改为分页返回；新增"记录每行处理明细（成功/失败均记录，含原始数据）"的规则与对应的按记录查询明细列表能力。

## Impact

- 数据库：新增 `tab_upstream_sync_record_detail` 表（新开一个 Flyway 版本号迁移 `V7`，不回改已执行过的 `V1`~`V6`），记录 `sync_record_id`/冗余的 `source_id`（供按数据源级联删除）、行序号、行原始数据（JSON 文本）、行状态、失败原因。
- 后端：`UpstreamSyncExecutor.syncDomain` 改造——取数结果为空时提前返回、不写记录；处理每一行时收集明细实体列表，与汇总记录一起写入（先插入汇总记录拿到自增 id，再批量插入明细，沿用 `UpstreamFieldMappingServiceImpl.replace` 已有的"逐行 insert"风格，不引入批量插入框架）；`UpstreamSourceServiceImpl.delete` 级联删除新增按 `source_id` 删除明细表。`UpstreamSyncRecordService`/`UpstreamSyncRecordServiceImpl`/`UpstreamSourceController` 的同步记录查询接口改为分页；新增同步记录明细的分页查询接口。
- 前端：`upstreamSource.ts` 新增明细相关类型；`upstreamSource.ts` API 封装、`UpstreamSourceConfigView.vue` 的"同步记录"分区改为分页表格并新增"查看明细"对话框。
- 不涉及新增第三方依赖。
