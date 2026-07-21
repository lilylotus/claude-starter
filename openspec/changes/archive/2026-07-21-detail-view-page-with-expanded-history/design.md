## Context

当前 9 个业务模块（组织、用户、任职、应用、角色、权限点、管理员、菜单、字典类型/字典项）的"详情"都是同一种模式：列表页点击"详情" → 调用 `GET /api/xxx/{id}` → 在 `el-dialog` 内用 `el-descriptions` 展示只读字段 → 弹窗底部嵌入 `OperationHistoryPanel`（`resourceType` + `targetId`）展示该资源实例的操作历史小型分页列表 → 每条历史记录需要点击"查看变更"才会叠加打开第二层 `OperationLogDetailDialog` 弹窗展示字段级变更（旧值→新值）。

用户反馈这种"弹窗 + 二级弹窗"的形态可视区域有限、层级深，希望改成独立页面并把变更明细默认铺开。这是纯粹的展示形态调整：不改变详情数据本身的接口语义（各模块 `GET /api/xxx/{id}` 不变），也不改变操作历史的记录/筛选逻辑（`GET /api/operation-logs` 的筛选参数不变），只是让"变更明细"这一份数据从"点击后单独请求"改成"随列表一次性返回"，并调整前端呈现载体（弹窗 → 页面）与布局（点击展开 → 默认铺开）。

## Goals / Non-Goals

**Goals:**
- 9 个模块的"详情"从弹窗改为独立路由页面，页面左上角有"返回"按钮，回到发起查看的列表页。
- 操作历史列表里每条记录的字段级变更（旧值→新值）默认直接展示，不需要点击。
- `GET /api/operation-logs` 分页接口一次性返回 `changeDetail`，避免前端为了"默认展开"而对每条历史记录发起额外的详情请求（N+1）。

**Non-Goals:**
- 不改变各模块详情数据本身的接口（`GET /api/xxx/{id}`）、不改变操作历史的记录逻辑（`OperationLogRecorder`）、不改变 `GET /api/operation-logs` 已有的筛选参数语义。
- 不改动独立的"操作日志"审计页面（`OperationLogManagementView.vue`）及其 `OperationLogDetailDialog` 点击展开交互——那是面向"全量审计流水"的场景，条目多、需要保持列表紧凑，维持现状更合适；本次只针对"单个资源实例详情页"内嵌的历史面板做默认展开调整。
- 不为详情页面新增权限点资源编码（详情本身的可见性沿用已有的模块查看权限，不因为从弹窗变成页面而拆出新的权限项）。
- 不做"编辑"弹窗到页面的转换——用户明确要求改动的是"详情"，编辑仍保持弹窗表单。

## Decisions

### 1. 后端：`OperationLogVO` 补上 `changeDetail`

调查确认 `tab_operation_log` 表的 `change_detail`（`TEXT`，JSON 数组字符串）与其余列表字段在同一行，列表分页 SQL（`OperationLogMapper.xml`）本来就是 `SELECT *`，这一列已经随每行查出、映射进了 `OperationLogEntity`，只是 `OperationLogVO`/`OperationLogConvert`/`OperationLogQueryServiceImpl.getPage()` 没有把它透出到响应体（详情接口 `getById()` 里已有对应的 JSON 反序列化逻辑，`getPage()` 复用即可）。因此这是一次简单的字段透出：
- `OperationLogVO` 新增 `List<OperationLogFieldChangeVO> changeDetail`。
- `OperationLogConvert` 的 `toVO()`/`toVOList()` 上 `changeDetail` 走 `ignore = true`（entity 是 String、VO 是 List，MapStruct 不能自动转换）。
- `OperationLogQueryServiceImpl.getPage()` 里对每条 record 复用 `getById()` 已有的 `parseChangeDetail(...)` 逻辑，设置到返回的 VO 上。
- `OperationLogMapper.xml` 不需要改动。

权衡：这个字段现在会随着**所有**分页调用返回，包括独立操作日志审计页面的列表查询——那边目前不会用到这份数据（仍用点击详情接口的方式展示），等于白白增加了响应体体积。评估后认为可接受：`change_detail` 内容通常是几个字段的 diff，不是大字段；审计页面默认 `pageSize` 也不大。不引入"是否返回 changeDetail"的开关参数——按项目"不为假设的未来需求做设计"的原则，真出现体积问题再单独优化，不在本次预先做。

### 2. 前端：详情路由设计

在 `router/index.ts` 里新增 9 个（含字典拆成 2 个共 10 个）不出现在侧边栏菜单（`menu.ts`）里的子路由，路径为对应列表路径 + `/:id`：

| 模块 | 列表路径 | 详情路径 | 详情组件（新增） |
|---|---|---|---|
| 组织 | `/identity/orgs` | `/identity/orgs/:id` | `OrgDetailView.vue` |
| 用户 | `/identity/users` | `/identity/users/:id` | `UserDetailView.vue` |
| 任职 | `/identity/positions` | `/identity/positions/:id` | `PositionDetailView.vue` |
| 应用 | `/application/list` | `/application/list/:id` | `AppDetailView.vue` |
| 角色 | `/permission/roles` | `/permission/roles/:id` | `RoleDetailView.vue` |
| 权限点 | `/permission/points` | `/permission/points/:id` | `PermissionDetailView.vue` |
| 管理员 | `/permission/admins` | `/permission/admins/:id` | `AdminDetailView.vue` |
| 菜单 | `/system/menus` | `/system/menus/:id` | `MenuDetailView.vue` |
| 字典类型 | `/system/dicts` | `/system/dicts/type/:id` | `DictTypeDetailView.vue` |
| 字典项 | `/system/dicts` | `/system/dicts/item/:id` | `DictItemDetailView.vue` |

字典类型和字典项本来就是两套独立数据（`GET /api/dict-types/{id}` / `GET /api/dict-items/{id}`），沿用之前拆成两个详情弹窗的做法，这里也拆成两个详情路由，用路径段 `type`/`item` 区分，不用 query 参数区分——路径段更符合 RESTful 习惯，也让浏览器地址/浏览历史更直观。

各详情路由 `name` 沿用现有菜单路由的命名规则（`path.slice(1).replace(/\//g, '-')`），例如 `/identity/users/:id` → `identity-users-detail`。这些路由**不**加入 `menu.ts`，因此不会出现在侧边栏，只能通过列表页"详情"按钮跳转或直接访问带 id 的 URL 进入，`meta.requiresAuth` 沿用父级布局路由已有的 `true`。

"详情"按钮的点击行为从"设置 `detailData` + 打开 `el-dialog`"改为 `router.push({ name: '<module>-detail', params: { id: row.id } })`。

### 3. 前端：详情页面组件结构与返回按钮

每个详情页面组件（如 `UserDetailView.vue`）结构：
- 顶部：返回按钮（`el-icon` 左箭头 + "返回"文案）+ 页面标题（如"用户详情"），返回按钮点击后 `router.push({ name: '<module-list-route-name>' })`——显式跳回该模块列表路由，而不是 `router.back()`：详情页可能是用户直接通过 URL/刷新进入的，`router.back()` 在这种场景下浏览器历史栈可能为空或指向登录页之外的无关页面，显式指定目标路由能保证"返回"永远落到"当前操作页面"（即该模块的列表页），语义更贴合用户的原始诉求。
- 中间：从原 `el-dialog` 内的 `el-descriptions`（及用户模块额外的任职记录表格）原样迁移过来的只读字段展示区块，不改变字段内容和布局逻辑，只是从弹窗容器换成页面容器（`el-card` 或类似）。
- 下方：重构后的 `OperationHistoryPanel`（见下一条）。
- `onMounted` 时按 `route.params.id` 调用对应模块的详情接口加载数据；不再有"父组件传入 targetId"的 props 依赖关系（原先弹窗里 `targetId` 来自父级列表页的 `detailData`，现在页面自己独立请求）。

### 4. 前端：`OperationHistoryPanel.vue` 重构为默认铺开展示

- 不再持有内部的 `OperationLogDetailDialog` 弹窗状态、不再有"查看变更"按钮。
- `fetchList()` 拿到的 `OperationLogRow[]`（现在已包含 `changeDetail`，来自后端改动第 1 点）直接渲染：每条历史记录展示为一个区块——头部是操作时间 + 操作类型标签 + 操作人（保留原有的 `el-tag` 颜色映射），下方紧跟一个只读的字段变更列表（字段名、旧值→新值），新增/删除操作时旧值或新值为空的情况保持原样展示（沿用 `OperationLogFieldChange` 里 `oldValue`/`newValue` 可为 `null` 的语义）。
- 视觉上采用项目里贯穿登录页动画、侧边栏子菜单连接线、面包屑分隔符、概览页时间线的"链式连接"视觉语言（圆点 + 虚线）——用类似 `el-timeline` 的纵向时间线布局承载"多条历史记录纵向排列、每条展开展示变更明细"的内容，而不是继续用紧凑的 `el-table`，这样在详情页更宽敞的空间里天然贴合"一眼看全"的诉求，也和项目既有的视觉语言保持一致。具体样式细节由实现时参考 `frontend-design` skill 调整，不在本设计文档里锁死像素级样式。
  - 实际实现未直接使用 Element Plus 的 `el-timeline` 组件，而是自定义 `<ol>/<li>` 结构 + `::before` 伪代码/CSS 手写圆点与虚线（`history-timeline__dot`/`history-timeline__item` 等类名），以便更贴合项目既有的连接线视觉规格（复用 `--chain-line-color`/`--chain-dot-size` 等设计令牌）。
  - 字段变更的旧值展示颜色实现后又做了一次调整：初版用 `--color-text-tertiary` + 删除线表示"已作废的旧值"，用户反馈删除线+置灰的视觉太重，改为 `--color-text-secondary`（比新值的 `--color-ink` 浅一档，不加删除线），仅靠颜色深浅和箭头区分新旧值。
- 分页交互不变（`el-pagination`，`page-size=5`，`prev, pager, next`）。
- Props 不变（`resourceType`、`targetId`），只是宿主从"弹窗内的过渡态（`targetId` 可能为 `null`）"变成"页面级、`onMounted` 后才渲染，`targetId` 恒为已加载到的 id"——不再需要 `targetId === null` 的空状态分支，但保留也无害，简化实现时可以直接沿用现有判断逻辑。

### 5. 列表页 `XxxManagementView.vue` 改动

移除详情 `el-dialog` 及其关联的 `detailVisible`/`detailData`/`fetchDetail` 状态和方法，"详情"按钮的 `@click` 改为 `router.push(...)`。编辑弹窗、新增弹窗、启用/停用、删除等其余交互不变。

## Risks / Trade-offs

- `OperationLogVO` 新增字段会让独立操作日志审计页面的列表响应体略微变大，但不影响其现有交互（评估已在决策 1 中说明，接受这个代价）。
- 10 个新增路由 + 10 个新增页面组件 + 9 个列表页改动，改动面较大，建议实现时按模块拆分、逐个验证（`npm run build` 全量类型检查 + 抽样在浏览器里跑一遍"列表 → 详情 → 编辑 → 返回详情看到最新历史"的路径），而不是一次性改完所有文件再统一验证。
- 直接通过 URL 访问详情页（不经过列表页"详情"按钮跳转）时，id 不存在或已被逻辑删除的情况需要有明确的错误态展示（复用各模块详情接口对不存在/已删除资源返回的业务错误码，页面侧展示错误提示 + 返回按钮，不能白屏）。

## Migration Plan

纯代码改动，无数据库迁移、无需要停机或分批发布的步骤；前后端可以同批上线（后端新增字段是纯 additive 的响应体扩展，不影响现有调用方）。

## Open Questions

无——范围已通过用户确认（9 个模块全部转为页面；操作历史默认全部展开，不保留点击交互）。
