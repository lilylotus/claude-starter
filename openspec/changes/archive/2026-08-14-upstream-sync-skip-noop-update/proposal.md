## Why

`OrgService`/`UserService`/`PositionService` 的 `update()` 方法无条件写库、记一条操作日志、发布 `DomainChangeEvent`——即使编辑前后的字段快照完全一致（没有任何实际变化）。上游同步（`UpstreamRowUpserter`）命中一条已有记录时，无论这次拉取到的数据是否与本地当前值相同，都会调用对应的 `.update()`。结果是：只要上游数据没有变化，定时轮询每次重新拉取到相同数据时，仍然会：
1. 无意义地刷新 `tab_org`/`tab_user`/`tab_user_position` 的 `update_time`/`update_by`，掩盖记录真实的最后编辑时间；
2. 在该资源自己的"操作历史记录"（`tab_operation_log`）里追加一条 `changeDetail=[]`（无字段变更明细）的 UPDATE 记录，随着定时同步频繁执行迅速淤积，把管理员真正做过的编辑淹没在噪音里（用户报告的现象）；
3. 发布 `DomainChangeEvent`，可能触发下游 app-sync 出站变更通知，给实际没有变化的记录发送不必要的通知。

## What Changes

- 仅在上游同步模块内新增"落库前比较是否有实际差异"的判断，不改动 `OrgService`/`UserService`/`PositionService` 的 `update()` 方法本身——那是全应用共用的方法（手动 UI 编辑、Excel 批量导入都在用），管理员手动提交编辑表单/导入一行通常代表明确的变更意图，"提交了但其实没变化"不是这些场景里的普遍问题；本次范围收窄到"定时轮询会反复重放同一批上游数据"这个高频重复触发、天然容易产生大量无意义更新的场景。
- `UpstreamRowUpserter` 命中一条已有记录、按现有逻辑构造好 Update 请求（含 `bindProperties` 设置的普通字段与 `parentId`/`orgId` 等特殊字段）之后，先与该记录当前的实际字段值逐一比较；完全一致时跳过调用 `xxxService.update()`——不写库、不追加操作日志、不发布 `DomainChangeEvent`。该行仍然计入本次同步的成功数，行明细状态仍为 `SUCCESS`（数据本身是"一致"这个正确结果，不是失败）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `identity-upstream-data-sync`：
  - "数据落库匹配与新增/更新语义"需求：补充"匹配到一条记录时，若该行数据与记录当前值完全一致则跳过实际写入（仍计入成功）"的规则。

## Impact

- 后端：`UpstreamRowUpserter` 新增一个通用的"请求对象与已匹配实体逐属性比较"辅助方法，在 `upsertOrg`/`upsertUser`/`upsertPosition` 的更新分支里，构造好 Update 请求后先判断是否有变化，无变化时直接返回（不调用 `xxxService.update()`）。
- 不涉及数据库迁移、不涉及接口契约变化、不涉及前端改动。
- 测试：`UpstreamRowUpserterTest` 新增用例覆盖"匹配到的记录数据与本次同步数据完全一致时跳过更新，不调用 service.update()"（组织/用户/任职各至少一个场景），以及"存在任一字段差异时仍然正常调用更新"的既有行为不受影响。
