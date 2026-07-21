## 1. 后端：操作日志列表接口补上字段级变更

- [x] 1.1 `OperationLogVO` 新增 `changeDetail: List<OperationLogFieldChangeVO>` 字段
- [x] 1.2 `OperationLogConvert` 的 `toVO()`/`toVOList()` 对 `changeDetail` 做 `ignore = true`
- [x] 1.3 `OperationLogQueryServiceImpl.getPage()` 复用 `getById()` 已有的字段变更 JSON 反序列化逻辑，为每条分页记录设置 `changeDetail`
- [x] 1.4 确认 `OperationLogMapper.xml` 无需改动（`SELECT *` 已包含 `change_detail` 列）
- [x] 1.5 更新 springdoc-openapi 注解，`OperationLogController#page` 的 `@Operation` description 补充说明响应含 `changeDetail`

## 2. 前端：类型与 API 层

- [x] 2.1 `types/operationLog.ts` 的 `OperationLogRow` 新增可选 `changeDetail?: OperationLogFieldChange[]`

## 3. 前端：路由

- [x] 3.1 `router/index.ts` 新增 10 个非菜单子路由（组织/用户/任职/应用/角色/权限点/管理员/菜单 8 个 + 字典类型/字典项 2 个），全部指向真实详情组件

## 4. 前端：`OperationHistoryPanel.vue` 重构

- [x] 4.1 移除内部 `OperationLogDetailDialog` 状态与"查看变更"交互
- [x] 4.2 改为纵向时间线布局（圆点 + 虚线，呼应项目"链式连接"视觉语言），默认展示每条历史记录的字段级变更明细（旧值→新值）
- [x] 4.3 保留分页交互（`el-pagination`，`page-size=5`）

## 5. 前端：9 个模块详情页面组件

- [x] 5.1 `views/identity/org/OrgDetailView.vue`
- [x] 5.2 `views/identity/user/UserDetailView.vue`（含任职记录表格）
- [x] 5.3 `views/identity/position/PositionDetailView.vue`
- [x] 5.4 `views/application/app/AppDetailView.vue`
- [x] 5.5 `views/permission/role/RoleDetailView.vue`
- [x] 5.6 `views/permission/permission/PermissionDetailView.vue`
- [x] 5.7 `views/permission/admin/AdminDetailView.vue`
- [x] 5.8 `views/system/menu/MenuDetailView.vue`
- [x] 5.9 `views/system/dict/DictTypeDetailView.vue`
- [x] 5.10 `views/system/dict/DictItemDetailView.vue`
- [x] 5.11 每个详情页在 id 不存在/已被逻辑删除时展示 `el-alert` 错误提示 + 返回按钮，不白屏

## 6. 前端：9 个列表页改动

- [x] 6.1 `OrgManagementView.vue`
- [x] 6.2 `UserManagementView.vue`
- [x] 6.3 `PositionManagementView.vue`
- [x] 6.4 `AppManagementView.vue`
- [x] 6.5 `RoleManagementView.vue`
- [x] 6.6 `PermissionManagementView.vue`
- [x] 6.7 `AdminManagementView.vue`
- [x] 6.8 `MenuManagementView.vue`（协调者收尾：并行 agent 中途卡住导致模板里"详情"按钮残留旧的 `openDetailDialog` 绑定及未删除的旧详情弹窗标签，已手动修正为 `goToDetail` 路由跳转并删除旧弹窗）
- [x] 6.9 `DictManagementView.vue`

## 7. 验证

- [x] 7.1 `backend/gradlew test` 全量通过（含新增的 `OperationLogQueryServiceImplTest` 两个用例）
- [x] 7.2 `frontend` 下 `npm run build`（`vue-tsc` 类型检查 + `vite build`）全量通过，无类型错误
- [x] 7.3 未能做真正的浏览器可视化点击验证（本次会话浏览器工具不可用）。替代验证：启动本地后端（`bootRun`，端口 48080）与前端 dev server（端口 5173），用 `curl` 对关键接口做冒烟测试——`GET /api/users/13`（详情数据源）与 `GET /api/operation-logs?resourceType=user&targetId=13`（历史面板数据源）均返回预期结构，后者每条记录已带完整 `changeDetail`，与 `UserDetailView.vue` + `OperationHistoryPanel.vue` 的实际调用方式一致。**遗留风险**：未验证过 Vue 组件的实际渲染效果（时间线布局观感、返回按钮跳转、错误态展示等），建议实现者或用户后续在浏览器中手动过一遍至少 1-2 个模块。
- [x] 7.4 核实 `权限资源.txt`：全部 10 个"详情"资源编码（如 `OrgManagement:org:detail`）语义未变（仍是"查看XX详情"这个动作本身，只是承载形式从弹窗变成页面），确认不需要改动

## 8. 收尾

- [x] 8.1 依据实际 diff 核对/调整了本 change 的 proposal.md（Impact 章节补充 OpenAPI 注解、单测、浏览器验证受限说明）与 design.md（决策 4 补充实际用自定义 `<ol>/<li>` 时间线而非 `el-timeline` 组件、旧值样式的后续调整）
- [ ] 8.2 用 `openspec-sync-specs` 把 delta 合并进主 spec，归档该 change
