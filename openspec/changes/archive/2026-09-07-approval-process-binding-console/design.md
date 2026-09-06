## Context

后端已经完整实现"业务绑定"能力（`production-approval-lifecycle` change 4.5/4.6 节，见 `openspec/specs/approval-design-release/spec.md`"精确版本绑定及显式回滚"需求）：`tab_wf_process_binding` 按 `(bizType, operationType, scopeType, scopeId)` 唯一，`WorkflowProcessBindingController` 提供列表/详情/新建/切换版本/启停接口，`ProcessBindingResolutionService` 按"精确组织 → 最近祖先组织 → 全局"顺序解析生效绑定。数据库种子数据已经为全部 4 类业务对象 × 5 种操作类型的 20 种组合插入了指向内置流程 `MASTER_DATA_APPROVAL` 的全局绑定（`enabled=1`）。权限点 `WorkflowDesign:binding:view`/`WorkflowDesign:binding:edit` 已在 `权限资源.txt` 登记并完成种子数据，但从未被任何前端页面实际使用。

本 change 的唯一目标是把这套已存在的后端能力做成一个可用的前端管理页面，不改动任何后端接口或数据结构。

## Goals / Non-Goals

**Goals:**

1. 管理员能在页面上看到组织/用户/任职/应用四类业务、五种操作类型当前各自生效哪个流程（模型名+版本号）、执行模式、启用状态。
2. 管理员能新建一条组织范围覆盖绑定，或修改全局绑定指向的版本，均通过下拉选择已发布的流程模型版本完成，不需要手写 `definitionId`。
3. 管理员能启用/禁用某条绑定。

**Non-Goals:**

- 不在本页面内创建/编辑/发布流程模型，只能选择已发布版本（用户已确认此范围）。
- 不支持 `RELIABLE_ASYNC` 执行模式的配置入口（后端 `resolveForStart` 目前对该模式一律拒绝，暴露出来只会让管理员配置出一个提交即报错的绑定）。
- 不做绑定删除功能——后端 `WorkflowProcessBindingController` 本身没有物理删除接口，只有启停，遵循既有后端能力边界，不额外要求新增删除接口。
- 不做"绑定的流程模型被下线/草稿变更后主动告警推送"这类通知机制，只在页面渲染时如实展示当前状态（若绑定指向的定义已不是 PUBLISHED，选择器/详情里如实标注，不隐藏问题）。

## Decisions

### 1. 页面路径与菜单

新增路由 `/workflow/bindings`，视图 `frontend/src/views/workflow/binding/ProcessBindingView.vue`，挂在 `frontend/src/router/menu.ts` 现有的"流程设计"一级分组下（`frontend/src/router/menu.ts:85` 附近，与"流程模型"同级），菜单标题"业务绑定"，`permissionKey: 'WorkflowDesign:binding:view'`（与已登记的权限点一致，`权限资源.txt` 无需新增编码）。新增按钮受 `WorkflowDesign:binding:edit` 门控。

### 2. 页面布局：按 bizType 分 Tab + 操作类型下拉选择（而非五种操作类型平铺列出）

采用"四个业务类型 Tab（组织/用户/任职/应用）+ Tab 内一个操作类型下拉选择器 + 选中操作类型对应的一张表"的布局，不再把 CREATE/UPDATE/ENABLE/DISABLE/DELETE 五种操作类型的卡片同时全部平铺展示（初版实现曾一次性列出全部 5 个卡片，用户反馈信息量过大、不需要同时看到五种操作，改为按需切换）。每个 Tab 内固定一个"操作类型"下拉（默认选中 `CREATE`，切换业务类型 Tab 时重置回 `CREATE`，不带着上一个 Tab 选中的操作类型），切换下拉只重新渲染下方这一张表：先展示该 `operationType` 的全局绑定（永远存在），再展示该 `operationType` 下的组织范围覆盖绑定列表（可能为空），"全局兜底 + 精确覆盖"的解析优先级展示方式不变，只是从"五个都展示"改为"一次看一个"。

### 3. 流程版本选择：级联下拉，而非直接输入 definitionId

新建/切换绑定弹窗内，`definitionId` 通过两级级联下拉获得，不直接暴露给用户：

1. 第一级：调用 `GET /api/workflow/process-models`，过滤展示（前端过滤，复用现有"流程模型"列表接口不新增查询参数，与已废弃提案 `approval-process-biztype-binding` Decision 6 的既有做法一致）`status` 含有至少一个已发布版本的模型（简化判断：直接展示全部模型，第二级下拉为空时提示"该模型尚无已发布版本"，不在第一级做后端没有提供的过滤能力）。
2. 第二级：选定模型后调用 `GET /api/workflow/process-models/{id}/versions`，过滤 `status === 'PUBLISHED'` 的版本，下拉选项展示"v{version}（发布人 {publishedBy}，{publishedTime}）"，选中项的 `id` 字段即为提交给后端的 `definitionId`。

若某条已有绑定当前 `definitionId` 对应的版本已被下线（`status !== 'PUBLISHED'`），列表/详情页仍需要能展示这条绑定原本指向的模型名+版本号（通过 `GET /api/workflow/process-models/{id}/versions` 拿全量版本历史，不只取已发布的，用于展示；只有"新建/切换"弹窗的下拉选项才过滤成仅已发布），并在状态列标红提示"绑定指向的版本已下线"，不隐藏这一问题（呼应已废弃提案 `approval-process-biztype-binding` Risk 1 的思路：把发现时机从"提交人提交失败"提前到"管理员打开本页面"）。

### 4. 组织范围选择：复用 el-tree-select 既有模式

`scopeType=ORG` 时的组织选择器直接复用项目里 `UserManagementView.vue`/`PositionManagementView.vue` 等已有表单中 `el-tree-select` 绑定组织树的写法（数据源为已有的组织树查询接口），不新建独立的可复用组件——本页面是目前第二个需要选组织的表单场景，暂不构成"三处重复才抽取公共组件"的门槛，維持现状写法，避免为一个新组件过度设计。`scopeType=GLOBAL` 时组织选择器隐藏/禁用。

### 5. definitionId → 展示名称的解析在前端做，不新增后端聚合接口

`ProcessBindingVO` 只返回 `definitionId`，不返回流程模型名称/版本号；`ProcessDefinitionVersionVO` 也不包含所属模型的 `processName`/`processCode`。页面加载绑定列表后，对每个用到的 `bizType` 需要展示的流程模型/版本信息，前端自行按需调用 `GET /api/workflow/process-models` + 各模型的 `versions` 接口做内存 join（`definitionId -> {processName, version, status}` 的 Map），不新增后端接口聚合这几张表——绑定数量级很小（至多 20+ 条组织覆盖），几次额外请求可接受，不值得为此改动后端只读查询接口。

## Risks / Trade-offs

- [前端需要自行 join 绑定与流程版本两组接口的数据，多几次网络请求] → 数据量小（≤ 20 条全局绑定 + 少量组织覆盖），可接受；如后续绑定量增长明显，再评估是否需要后端聚合接口。
- [页面允许配置组织覆盖绑定，但后端接口本身没有校验"该组织必须存在于组织树中"以外的业务约束] → 沿用后端既有校验（`WorkflowProcessBindingService.createBinding` 内部逻辑），前端不重复实现，出错时透传后端错误提示。
- [`executionMode` 目前只暴露 `LEGACY_SYNC`] → 是有意的范围收窄（见 Non-Goals），待 `RELIABLE_ASYNC` 生产路由放开后再补充选项，不在本 change 里为一个当前不可用的模式做 UI。

## Migration Plan

纯前端新增页面+路由+菜单项，不涉及数据库迁移、不涉及后端接口改动，可独立于后端发布节奏上线；上线后不影响任何存量绑定的运行时解析行为（本页面是只读+管理操作，不改变 `ProcessBindingResolutionService` 的解析逻辑本身）。

## Open Questions

无阻塞性未决问题；如后续需要支持 `RELIABLE_ASYNC` 执行模式配置，待该模式生产可用后作为独立小改动追加。
