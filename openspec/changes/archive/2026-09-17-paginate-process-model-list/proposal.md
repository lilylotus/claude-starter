## Why

流程模型列表当前通过全量接口加载并展示所有模型，数量增多时页面过长且请求成本持续增长。用户要求改成分页查询，每次仅展示当前页。

## What Changes

- 增加数据库分页查询接口，返回 records、total、page、pageSize。
- 流程模型列表默认每页 10 条，支持翻页及切换 10/20/50/100 条，操作后刷新当前页。
- 保留业务绑定页正在使用的全量模型选择接口，避免下拉选项只剩第一页。
- 使用既有查看权限，分页控件不新增业务权限点。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `workflow-process-designer`: 增加流程模型列表服务端分页与页面交互要求。

## Impact

涉及 WorkflowProcessModelController、WorkflowProcessModelService 及其实现，前端 api/workflow.ts、相关类型和 ProcessModelListView.vue。复用 MyBatis-Plus 与 PageResult，无新增依赖和数据库迁移。不改变先前 fix-workflow-immediate-completion 方案的待确认状态。
