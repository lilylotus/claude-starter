## Why

已归档的 `add-operation-log` 变更实现了一个独立的"操作日志"页面，可以按模块/资源类型/操作人/时间范围查询全量操作日志，但用户想追溯"某一条具体的组织/用户/角色……记录自己发生过哪些变更"时，仍需要离开当前详情弹窗、跳转到操作日志页面手动填筛选条件查找，路径较长。本次改动把"操作历史"直接嵌入组织、用户、任职、应用、角色、权限点、管理员、菜单、字典（字典类型/字典项）这 9 个业务模块各自的只读详情弹窗内，打开详情即可看到该条记录自己的新增/编辑/启用/停用历史，按操作时间降序排列，无需跳转、无需重新筛选。

## What Changes

- 操作日志查询接口 `GET /api/operation-logs` 新增可选筛选参数 `targetId`（配合已有的 `resourceType` 一起使用），用于查询"某个具体资源实例"的操作历史，不影响已有的分页/模块/操作类型/操作人/时间范围筛选行为。
- 新增两个可复用前端组件：`OperationLogDetailDialog.vue`（把现有操作日志页面里内联的"字段变更详情"只读弹窗抽取为独立组件，避免后续被重复实现 9+1 次）、`OperationHistoryPanel.vue`（接收 `resourceType` + `targetId`，展示该资源实例的操作历史小型分页列表——操作时间/操作类型/操作人 + "查看变更"按钮，点击后打开 `OperationLogDetailDialog`）。
- 在组织、用户、任职、应用、角色、权限点、管理员、菜单这 8 个模块已有的只读详情弹窗内嵌入 `OperationHistoryPanel`。
- 字典管理目前没有"详情"弹窗（只有新增/编辑），本次为字典类型、字典项各新增一个只读详情弹窗（复用已存在的 `GET /api/dict-types/{id}`、`GET /api/dict-items/{id}` 接口，无需新增后端接口），并同样嵌入 `OperationHistoryPanel`。
- 因软删除机制的自然结果：一条记录被逻辑删除后其详情弹窗本身不可访问，所以嵌入的操作历史列表天然只会出现新增、编辑、启用、停用四类记录，不会出现删除记录，不需要额外的过滤逻辑。

## Capabilities

### New Capabilities
（无——本次是在已有能力上追加"在详情弹窗内展示操作历史"这一新行为，归入各自既有 capability 的新增 Requirement，不构成独立的新能力。）

### Modified Capabilities
- `operation-log-management`：`GET /api/operation-logs` 新增 `targetId` 可选筛选参数。
- `org-management`：组织详情弹窗新增操作历史展示。
- `user-management`：用户详情弹窗新增操作历史展示。
- `position-management`：任职记录详情弹窗新增操作历史展示。
- `application-management`：应用详情弹窗新增操作历史展示。
- `role-management`：角色详情弹窗新增操作历史展示。
- `permission-management`：权限点详情弹窗新增操作历史展示。
- `admin-management`：管理员详情弹窗新增操作历史展示。
- `menu-management`：资源详情弹窗新增操作历史展示。
- `dict-management`：新增字典类型、字典项的只读详情弹窗（此前不存在），并展示操作历史。

## Impact

- 后端：`cn.nihility.rbac.operationlog` 包内 `OperationLogQueryRequest`/`OperationLogController`/`OperationLogMapper.xml` 新增 `targetId` 筛选条件；无新增表、无新增依赖。
- 前端：新增 `src/components/OperationLogDetailDialog.vue`、`src/components/OperationHistoryPanel.vue`；重构 `OperationLogManagementView.vue` 使用抽取后的详情弹窗组件；`OrgManagementView.vue`/`UserManagementView.vue`/`PositionManagementView.vue`/`AppManagementView.vue`/`RoleManagementView.vue`/`PermissionManagementView.vue`/`AdminManagementView.vue`/`MenuManagementView.vue` 的详情弹窗内嵌入操作历史面板；`DictManagementView.vue` 新增字典类型、字典项详情弹窗（含操作历史面板）及对应"详情"按钮；`types/operationLog.ts` 的 `OperationLogQueryParams` 新增 `targetId` 字段。
- 完成后需要同步更新仓库根目录 `权限资源.txt`：新增 `DictManagement:dictType:detail`、`DictManagement:dictItem:detail` 两条编码；并追加一份 `tab_menu` 种子数据迁移（`V16`）把这两条编码写入。
