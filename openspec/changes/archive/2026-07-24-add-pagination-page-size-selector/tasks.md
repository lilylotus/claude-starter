## 1. 共享常量模块

- [x] 1.1 新增 `frontend/src/constants/pagination.ts`，导出 `PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const` 与 `DEFAULT_PAGE_SIZE = 10`

## 2. Store 承载分页状态的 8 处（单一分页）

- [x] 2.1 `frontend/src/stores/org.ts`：`pageSize` 初始值改用 `DEFAULT_PAGE_SIZE`；新增 `changePageSize(newSize)` action（设置 `pageSize`、调用 `fetchChildren(currentParentId.value, 1)`）；`frontend/src/views/identity/org/OrgManagementView.vue` 的 `el-pagination` 加 `:page-sizes="PAGE_SIZE_OPTIONS"`、`layout` 加入 `sizes`、绑定 `@size-change="orgStore.changePageSize"`
- [x] 2.2 `frontend/src/stores/user.ts` + `frontend/src/views/identity/user/UserManagementView.vue`：同上模式，新增 `changePageSize`
- [x] 2.3 `frontend/src/stores/position.ts` + `frontend/src/views/identity/position/PositionManagementView.vue`：同上模式，新增 `changePageSize`
- [x] 2.4 `frontend/src/stores/app.ts` + `frontend/src/views/application/app/AppManagementView.vue`：同上模式，新增 `changePageSize`
- [x] 2.5 `frontend/src/stores/menu.ts` + `frontend/src/views/system/menu/MenuManagementView.vue`：同上模式，新增 `changePageSize`
- [x] 2.6 `frontend/src/stores/role.ts` + `frontend/src/views/permission/role/RoleManagementView.vue`：同上模式，新增 `changePageSize`
- [x] 2.7 `frontend/src/stores/admin.ts` + `frontend/src/views/permission/admin/AdminManagementView.vue`：同上模式，新增 `changePageSize`
- [x] 2.8 `frontend/src/stores/permission.ts` + `frontend/src/views/permission/permission/PermissionManagementView.vue`：同上模式，新增 `changePageSize`

## 3. dict store（左右两个独立分页）

- [x] 3.1 `frontend/src/stores/dict.ts`：`typesPageSize`/`itemsPageSize` 初始值改用 `DEFAULT_PAGE_SIZE`；新增 `changeTypesPageSize(newSize)`、`changeItemsPageSize(newSize)` 两个独立 action
- [x] 3.2 `frontend/src/views/system/dict/DictManagementView.vue`：左侧字典类型 `el-pagination` 加 `:page-sizes="PAGE_SIZE_OPTIONS"`、`layout` 加入 `sizes`、绑定 `@size-change="dictStore.changeTypesPageSize"`；右侧字典项 `el-pagination` 同样处理，绑定 `@size-change="dictStore.changeItemsPageSize"`

## 4. 本地状态承载分页的 4 处

- [x] 4.1 `frontend/src/views/system/formfields/FormFieldDefinitionPanel.vue`：`pageSize` 初始值改用 `DEFAULT_PAGE_SIZE`；新增 `handleSizeChange(newSize)`（设置 `pageSize.value`、`page.value = 1`、调用查询函数）；`el-pagination` 加 `:page-sizes="PAGE_SIZE_OPTIONS"`、`layout` 加入 `sizes`、绑定 `@size-change="handleSizeChange"`
- [x] 4.2 `frontend/src/views/system/log/OperationLogManagementView.vue`：同上模式，新增 `handleSizeChange`
- [x] 4.3 `frontend/src/views/system/formfields/ImportFieldConfigPanel.vue`：同上模式，新增 `handleSizeChange`
- [x] 4.4 `frontend/src/views/system/metadatafields/MetadataFieldListView.vue`：同上模式，新增 `handleSizeChange`

## 5. 验证

- [x] 5.1 `npm run build`（`vue-tsc -b && vite build`）通过，无类型错误
- [x] 5.2 本地启动前端，任选一个 store 版本页面（如组织管理）实测：切换每页条数后列表按新条数从第一页重新加载，且默认打开时仍是 10 条/页
- [x] 5.3 任选一个本地状态版本页面（如操作日志管理）实测：同上
- [x] 5.4 字典管理页面实测：左侧字典类型、右侧字典项的每页条数选择器互相独立，切换一侧不影响另一侧当前的页码/每页条数
- [x] 5.5 确认 `OperationHistoryPanel.vue`（详情页内嵌操作历史面板）未被改动，仍固定每页 5 条
