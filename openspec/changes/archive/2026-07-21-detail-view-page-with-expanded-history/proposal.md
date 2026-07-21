## Why

已归档的 `add-operation-history-to-detail` 把"操作历史"嵌入了 9 个业务模块（组织、用户、任职、应用、角色、权限点、管理员、菜单、字典类型/字典项）各自的只读"详情" `el-dialog` 内，`fix-detail-history-panel-refresh` 又修复了弹窗不刷新的问题。但用户反馈：弹窗形态下可视区域有限，操作历史列表默认只显示"操作时间/操作类型/操作人"三列，要看某条记录具体改了哪些字段（旧值→新值）还得再点一次"查看变更"弹出第二层弹窗，层层叠加，变更内容不能一眼看全。本次改动把"详情"从弹窗改成独立页面，获得更大的展示空间；并把操作历史的字段级变更明细从"点击才展开"改成默认铺开展示，减少一次交互，做到打开详情就能看到数据和变更痕迹。

## What Changes

- 9 个业务模块（组织、用户、任职、应用、角色、权限点、管理员、菜单、字典类型/字典项共 10 处）的"详情"从 `el-dialog` 弹窗改为独立路由页面，页面左上角提供"返回"按钮，点击后返回该模块的列表页（即触发查看详情的那个操作页面）。
- 各详情页面内嵌的操作历史列表：字段级变更明细（旧值→新值）默认直接铺开展示在每条历史记录下方，不再需要点击"查看变更"才展开；原先叠加在详情弹窗之上的 `OperationLogDetailDialog` 二级弹窗交互从这 9 处详情页中移除（独立的"操作日志"审计页面 `OperationLogManagementView.vue` 保持不变，仍使用点击展开的弹窗交互，不在本次改动范围）。
- 操作日志分页查询接口 `GET /api/operation-logs` 的响应新增 `changeDetail` 字段（此前只有详情接口 `GET /api/operation-logs/{id}` 才返回），使前端一次分页请求即可拿到每条记录的字段级变更，不需要为"默认展开"额外发起 N 次详情请求。

## Capabilities

### New Capabilities
（无——详情从弹窗改为页面、历史默认展开，都是对已有能力里"详情"相关 Requirement 的行为调整，归入各自既有 capability。）

### Modified Capabilities
- `operation-log-management`：`GET /api/operation-logs` 分页响应新增 `changeDetail` 字段。
- `org-management`：组织详情从弹窗改为页面，历史默认展开。
- `user-management`：用户详情从弹窗改为页面，历史默认展开。
- `position-management`：任职记录详情从弹窗改为页面，历史默认展开。
- `application-management`：应用详情从弹窗改为页面，历史默认展开。
- `role-management`：角色详情从弹窗改为页面，历史默认展开。
- `permission-management`：权限点详情从弹窗改为页面，历史默认展开。
- `admin-management`：管理员详情从弹窗改为页面，历史默认展开。
- `menu-management`：资源详情从弹窗改为页面，历史默认展开。
- `dict-management`：字典类型、字典项详情从弹窗改为页面，历史默认展开。

## Impact

- 后端：`cn.nihility.rbac.operationlog` 包内 `OperationLogVO` 新增 `changeDetail` 字段、`OperationLogConvert` 相应调整、`OperationLogQueryServiceImpl.getPage()` 补上字段变更 JSON 反序列化、`OperationLogController#page` 的 `@Operation` 注解更新说明响应含 `changeDetail`；`OperationLogMapper.xml` 无需改动（列表 SQL 已是 `SELECT *`，字段早已查出，只是此前没有映射进 VO）。`OperationLogQueryServiceImplTest` 新增两个单元测试覆盖分页结果的 `changeDetail` 反序列化（含空字符串场景）。无新增表、无新增依赖。
- 前端：新增 9 个（含字典的 2 个）详情页面组件，路由表 `router/index.ts` 新增对应的非菜单子路由；对应 9 个 `XxxManagementView.vue` 移除详情 `el-dialog`，"详情"按钮改为路由跳转；`components/OperationHistoryPanel.vue` 重构为默认铺开展示字段变更的布局（自定义纵向时间线结构，非 `el-timeline` 组件），不再内部持有 `OperationLogDetailDialog`；`types/operationLog.ts` 的 `OperationLogRow` 补充可选的 `changeDetail` 字段。`components/OperationLogDetailDialog.vue`、独立的操作日志审计页面不受影响。
- 已核实 `权限资源.txt` 不需要同步：全部 10 个"详情"资源编码语义未变（仍是"查看XX详情"这个动作，只是承载形式从弹窗变成页面）。
- 验证方式：`backend/gradlew test` 全量通过；`frontend` 下 `npm run build` 全量通过。本次会话浏览器自动化工具不可用，未做真正的浏览器可视化点击验证，改为启动本地前后端服务后用 `curl` 对关键接口（详情接口、按 `resourceType`+`targetId` 查询的历史接口）做冒烟测试，确认响应结构符合前端组件的实际调用方式；建议后续在浏览器中手动过一遍至少 1-2 个模块确认视觉效果。
