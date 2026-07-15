## Why

组织管理页面（`views/identity/org/OrgManagementView.vue`）目前一进入页面就无条件请求全量组织树接口 `GET /api/orgs/tree`（`onMounted` 里调用 `orgStore.fetchTree()`），且每次新增/编辑/启停用/删除成功后（`refreshAfterMutation()`）都会再请求一次；但这份全量树数据唯一的消费者是新增/编辑弹窗里的"上级组织"选择器，绝大多数页面访问根本不会打开这个弹窗。左侧导航树已经是懒加载（`GET /api/orgs/tree/children`），右侧面板标题目前依赖对这份全量树做递归查找（`findNodeName`）来展示节点名称，这个查找可以直接用点击事件里节点自带的 `name` 代替，不需要依赖全量树。这次改动消除页面加载和每次增删改后都触发的不必要全量树请求。

## What Changes

- 组织管理页面进入时不再预先请求全量组织树接口；改为在用户实际打开新增/编辑弹窗时才请求，请求完成后再展示弹窗。
- 增删改成功后不再无条件刷新全量树缓存；下次打开弹窗时会重新请求，天然保证数据是最新的。
- 右侧面板标题（"[组织名称]下级组织"）改为直接使用左侧树点击事件里节点自带的名称，不再依赖对全量树的递归查找。

## Capabilities

### Modified Capabilities
- `org-management`: 前端组织管理页面里"上级组织"全量树的加载时机从"页面进入即加载 + 每次增删改后刷新"改为"仅在打开新增/编辑弹窗时按需加载"；右侧面板标题的数据来源从全量树递归查找改为左侧树点击事件自带的节点名称。不涉及后端接口变化。

## Impact

- 前端：`stores/org.ts`（`fetchTree()` 的调用时机、`selectNode` 增加记录节点名称、`refreshAfterMutation()` 去掉全量树刷新）、`views/identity/org/OrgManagementView.vue`（`onMounted` 去掉 `fetchTree()`、`openCreateDialog`/`openEditDialog` 改为先 `await` 全量树加载完成再展示弹窗、`rightPanelTitle` 改用节点自带名称、移除不再需要的 `findNodeName`）。
- 后端：无变化，复用既有的 `GET /api/orgs/tree`。
- 数据库：无变化。
