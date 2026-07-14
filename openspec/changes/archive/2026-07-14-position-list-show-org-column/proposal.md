## Why

任职管理页面右侧表格当前只显示"所属人员"（用户姓名），组织信息完全依赖左侧当前选中的树节点隐式表达。当同一批任职记录里混有跨组织的历史数据，或用户想在不切换左侧选中节点的情况下快速确认每一行具体挂在哪个组织下时，表格本身看不出组织名称，需要额外点开详情才能确认。

## What Changes

- 右侧任职记录表格的"所属人员"列表头文案改为"姓名"（数据源不变，仍是 `userName`）。
- 在"姓名"列之后新增一列"组织"，展示该条任职记录的组织名称（`orgName`，`PositionVO` 已包含该字段，无需新增/调整接口）；表头文案精简为"组织"而不是"所属组织"，避免和左侧"组织架构"面板标题的"组织"重复啰嗦。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `position-management`：任职管理前端界面的表格列描述更新（"所属人员"→"姓名"，姓名后新增"组织"列）。

## Impact

- 前端：`frontend/src/views/identity/position/PositionManagementView.vue`（表格列定义）。
- 不涉及后端接口变更（`PositionVO` 已包含 `orgName` 字段）。
