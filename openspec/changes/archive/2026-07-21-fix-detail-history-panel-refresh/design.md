## Context

`OperationHistoryPanel.vue`（`frontend/src/components/OperationHistoryPanel.vue`）是一个通用的操作历史面板组件，通过 `targetId` prop 接收被查看资源的主键 id，内部用：

```ts
watch(
  () => props.targetId,
  () => { page.value = 1; fetchList() },
  { immediate: true }
)
```

来加载/刷新历史列表。它被嵌入到 9 个业务管理页面、共 10 处"详情" `el-dialog` 中（用户、组织、任职、应用、管理员、权限点、角色、菜单资源、字典类型、字典项）。

后端排查（`backend/src/main/java/cn/nihility/rbac/operationlog/service/impl/OperationLogRecorderImpl.java`）确认 `recordCreate`/`recordUpdate`/`recordStatusChange`/`recordDelete` 均在 Controller 返回前同步调用 `record(...)` 写入 `tab_operation_log` 表，不存在异步延迟；查询接口按 `resourceType` + `targetId` 精确匹配、按操作发起时间降序排列，字段与索引均无问题。因此问题不在数据写入或查询链路，而在前端组件的挂载/刷新时机。

根因：这些"详情" `el-dialog` 均未设置 `destroy-on-close`，Element Plus 默认行为下弹窗关闭只是隐藏（`v-if`/`v-show` 语义上组件实例保留），并不销毁内部组件树。在"打开详情查看一次 → 关闭 → 编辑保存 → 再打开同一条记录的详情"这条路径中，`props.targetId` 数值前后没有变化，`watch` 的回调不会因值未变而重新触发，`OperationHistoryPanel` 停留在上次挂载时拉取的旧数据，导致用户看不到刚编辑产生的新记录。

## Goals / Non-Goals

**Goals:**
- 保证任意一处"详情"弹窗每次重新打开都能展示该资源截至当前的最新操作历史，覆盖"编辑后立即查看详情"这一典型路径。
- 修复方式对现有 9 个页面保持一致、改动面小、不引入新的状态管理或跨组件通信。

**Non-Goals:**
- 不改动后端日志写入/查询逻辑（已确认无问题）。
- 不改动 `OperationHistoryPanel.vue` 组件内部实现（其 `watch` 逻辑本身没有问题，只是从未被重新触发）。
- 不改动任何"编辑"弹窗的行为。
- 不处理详情弹窗内除操作历史外的其他数据是否需要刷新（本次范围仅限操作历史面板未刷新这一具体问题）。

## Decisions

**采用 `destroy-on-close` 而非在父组件里手动调用刷新方法**

给每处详情 `el-dialog` 加上 Element Plus 原生支持的 `destroy-on-close` 属性，使弹窗关闭时销毁其内部组件树；下次打开时 `OperationHistoryPanel` 重新挂载，触发已有的 `watch(..., { immediate: true })`，自动以最新 `targetId` 重新拉取历史列表。

考虑过的替代方案：

1. **`defineExpose` 暴露 `refresh()` 方法，父组件在编辑保存成功、下次打开详情前手动调用** —— 需要在 9 个父组件里分别维护"何时该刷新"的判断逻辑（编辑保存后 vs 单纯重复打开详情两种路径处理不一致），跨组件调用时机容易出错，且无法覆盖"详情数据因其他原因（如另一个管理员并发编辑）而过期"的场景。放弃。
2. **在 `OperationHistoryPanel` 内部同时 `watch` 宿主弹窗的 `visible` 状态，弹窗每次由 false 变 true 时强制刷新** —— 需要把宿主弹窗的可见性状态作为新 prop 传入这个通用组件，让一个本应只关心"查看哪个资源的历史"的组件反过来耦合宿主弹窗的显隐语义，改动侵入到共享组件本身，影响面更大。放弃。
3. **`destroy-on-close`（采用）** —— Element Plus 内置能力，声明式、一行属性即可生效，复用组件里已经写好的 `watch(targetId, ..., { immediate: true })`，不需要新增任何 JS 逻辑或跨组件契约，9 处改动方式完全一致，风险可控。

## Risks / Trade-offs

- **[风险] 弹窗销毁重建带来的短暂空白/loading 闪烁** → 详情弹窗本身在数据未加载完成前已有 `v-loading` 状态（`detailLoading` / `typeDetailLoading` / `itemDetailLoading` 等），操作历史面板内部同样有自己的 loading 态，用户体验上与首次打开详情弹窗时一致，不是新增的问题。
- **[风险] 弹窗内除操作历史外的其他内部状态（如展开的"查看变更"子面板）也会因销毁重建被重置** → 这是预期且期望的行为：每次重新打开详情都应该是一次全新的只读查看会话，不应保留上一次的临时 UI 状态。
- **[取舍] 该方案未解决"如果哪天新增第 10 类资源详情弹窗但忘记加 `destroy-on-close`"的问题** —— 目前没有对 `el-dialog` 用法做统一封装或 lint 规则强制这一属性，依赖开发者遵循现有约定；本次修复范围只覆盖已存在的 9 个视图，未来新增详情弹窗需要人工留意同样加上该属性。

## Migration Plan

纯前端 UI 属性改动，无数据结构、接口、配置变更，随前端正常构建发布（`npm run build`）上线即可，无需数据迁移或特殊回滚步骤；如需回滚，直接回退这 9 个文件的改动。

## Open Questions

无——问题已定位、方案已实现并通过 `npm run build` 验证。
