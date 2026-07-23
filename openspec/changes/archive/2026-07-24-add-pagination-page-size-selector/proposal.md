## Why

全项目 13 处主列表分页（组织、用户、任职、应用、菜单、角色、管理员、权限点、字典类型 + 字典项、表单字段定义、操作日志、导入字段配置、元数据字段）在前端都把每页条数写死为 `10`（各 Pinia store 或组件本地状态里的 `pageSize = ref(10)`），`el-pagination` 的 `layout` 也都只有 `"prev, pager, next, total"`，没有暴露任何调整每页条数的入口。后端分页接口本身早已支持任意 `pageSize`（`@RequestParam(required = false, defaultValue = "10") Integer pageSize`，全仓库排查未发现任何上限校验），纯粹是前端缺一个交互入口。用户希望在分页按钮前加一个每页条数下拉选择器，默认 10 条，可选 10/20/50/100。

## What Changes

- 新增前端共享常量模块 `frontend/src/constants/pagination.ts`，导出 `PAGE_SIZE_OPTIONS = [10, 20, 50, 100]` 与 `DEFAULT_PAGE_SIZE = 10`，供全部 13 处分页复用，避免各处重复写死同一份选项数组。
- 全部 13 处主列表分页的 `el-pagination` 统一：`layout` 由 `"prev, pager, next, total"` 改为 `"sizes, prev, pager, next, total"`（每页条数选择器在分页按钮之前，`total` 保留原有的最后位置），新增 `:page-sizes="PAGE_SIZE_OPTIONS"` 与 `@size-change` 事件绑定；对应的 8 个 Pinia store（`org`/`user`/`position`/`app`/`menu`/`role`/`admin`/`permission`）新增 `changePageSize(newSize)` action，`dict` store 因左右两侧字典类型/字典项各自独立分页，新增两个独立的 `changeTypesPageSize`/`changeItemsPageSize` action；4 个本地状态视图（`FormFieldDefinitionPanel.vue`/`OperationLogManagementView.vue`/`ImportFieldConfigPanel.vue`/`MetadataFieldListView.vue`）新增 `handleSizeChange(newSize)` 函数。切换每页条数后统一"设置新的每页条数、重置到第一页、重新查询"，与现有"搜索后重置到第一页"的既有模式保持一致。
- 明确排除 `frontend/src/components/OperationHistoryPanel.vue`（详情页内嵌的"操作历史"小面板，`layout="prev, pager, next"`、固定 `PAGE_SIZE = 5`，规格里已明确"每页 5 条"，属于不同的展示场景，不在本次范围内）。
- 不改动后端：现有 `pageSize` 查询参数直接复用，无需新增字段或校验规则。

## Capabilities

### New Capabilities
（无。）

### Modified Capabilities
- `org-management`：组织管理页面右侧子组织列表分页新增每页条数选择器。
- `user-management`：用户管理页面用户列表分页新增每页条数选择器。
- `position-management`：任职管理页面任职记录列表分页新增每页条数选择器。
- `application-management`：应用管理页面应用列表分页新增每页条数选择器。
- `menu-management`：菜单管理页面右侧子资源列表分页新增每页条数选择器。
- `role-management`：角色管理页面角色列表分页新增每页条数选择器。
- `admin-management`：管理员管理页面管理员列表分页新增每页条数选择器。
- `permission-management`：权限管理页面权限点列表分页新增每页条数选择器。
- `dict-management`：字典管理页面左侧字典类型列表、右侧字典项列表分别新增独立的每页条数选择器。
- `form-field-definition-management`：表单管理页面字段定义列表分页新增每页条数选择器。
- `operation-log-management`：操作日志管理页面日志列表分页新增每页条数选择器。
- `excel-import-export`：表单管理页面"导入模板配置"tab 的字段配置列表分页新增每页条数选择器。
- `metadata-field-management`：元数据配置页面字段列表分页新增每页条数选择器。

## Impact

- **前端代码**：新增 `frontend/src/constants/pagination.ts`；修改 13 个视图/面板组件文件（详见 tasks.md 逐一列出）；修改 9 个 Pinia store 文件（`org.ts`/`user.ts`/`position.ts`/`app.ts`/`menu.ts`/`role.ts`/`admin.ts`/`permission.ts`/`dict.ts`）。
- **后端代码**：无改动。
- **规格**：13 个能力的前端界面需求各自新增"切换每页条数"相关表述与场景。
- **风险**：13 处近乎重复的机械改动，主要风险是遗漏其中一处或 layout 字符串写错；已在 tasks.md 中逐一列出文件路径便于逐项核对。纯前端可加性改动（新增交互入口，不改变现有默认行为），不影响任何现有功能。
