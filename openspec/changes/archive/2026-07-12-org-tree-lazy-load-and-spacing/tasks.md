## 1. 后端：树懒加载接口

- [x] 1.1 `OrgService` 新增 `getChildrenTreeNodes(Long parentId)`，返回 `List<OrgTreeNodeVO>`（不分页，`children` 固定空数组），排序/过滤条件与现有 `getChildren`/`listAllUndeletedOrdered` 一致（排除逻辑删除、`showOrder` 降序 + `id` 升序）
- [x] 1.2 `OrgServiceImpl` 实现该方法，复用现有查询条件构造逻辑，避免重复代码（新增私有方法 `childrenQueryWrapper(long parentId)`，供 `getChildren` 与 `getChildrenTreeNodes` 共用）
- [x] 1.3 `OrgController` 新增 `GET /api/orgs/tree/children`，`parentId` 可选（缺省 `0`），返回 `List<OrgTreeNodeVO>`，加 `@Operation`/`@Parameter` 说明用途（树懒加载专用，区别于分页版 `/api/orgs/children`）
- [x] 1.4 编译通过（`./gradlew compileJava`），现有测试全部通过（`./gradlew test`），并为 `getChildrenTreeNodes` 新增两个单元测试（默认 `children` 为空列表、`parentId` 为 `null` 时按顶级处理）。本地手动 curl 验证未能完成：端口 48080 已被 IDE 中一个用户自己启动的调试实例占用，未做端口互斥的额外验证，改为以单元测试覆盖排序/过滤/空 children 契约

## 2. 前端：类型与接口封装

- [x] 2.1 `types/org.ts` 补充/复用树懒加载接口的类型（响应即 `OrgTreeNode[]`，无需新类型，只需新增 api 封装）——确认无需新类型，未改动该文件
- [x] 2.2 `api/org.ts` 新增 `getOrgTreeChildren(parentId?: number): Promise<OrgTreeNode[]>` 调用 `GET /api/orgs/tree/children`

## 3. 前端：orgStore 拆分左侧导航树与弹窗全量树

- [x] 3.1 保留现有 `tree`/`fetchTree()` 不变，继续给弹窗"上级组织"选择器使用
- [x] 3.2 新增左侧导航树用的懒加载方法 `loadNavTreeChildren(parentId)`，内部调用 `getOrgTreeChildren`
- [x] 3.3 新增 `refreshNavTreeBranch(parentId)` 方法，供增删改之后刷新左侧树对应分支使用；`parentId === 0` 时额外把结果写入 `navTreeTopLevel`（绑定为 `el-tree` 的 `:data`），因为顶级不是树上任何真实节点的 `node-key`，`updateKeyChildren` 对它不生效

## 4. 前端：左侧 el-tree 改为懒加载

- [x] 4.1 `OrgManagementView.vue` 左侧 `el-tree` 去掉 `default-expand-all`，改为 `lazy` + `:load="loadNode"`，`loadNode(node, resolve)` 按 `node.level === 0 ? 0 : node.data.id` 调用 store 的懒加载方法并 `resolve(children)`。`:data` 未完全去掉，而是改绑定到新增的 `orgStore.navTreeTopLevel`（初始为空数组）：Element Plus 的懒加载树在 `store.initialize()` 时无论 `:data` 是否已有内容都会对根节点调用一次 `load`，所以初次挂载不会因为这个绑定产生重复请求；`navTreeTopLevel` 只在 `refreshNavTreeBranch(0)` 时被写入，用于顶级层面增删改后的局部刷新（见 3.3、design.md 决策 3）
- [x] 4.2 `treeRef` 引用 `el-tree` 实例（类型 `TreeInstance`），增删改成功后新增 `refreshNavTreeAfterMutation()`（在 `refreshAfterMutation` 之后调用）：`currentParentId !== 0` 时调用 `treeRef.value.updateKeyChildren(currentParentId, freshChildren)` 局部刷新受影响节点的子节点；`currentParentId === 0` 时依赖 store 内部把 `navTreeTopLevel` 整体替换、通过 `:data` 响应式生效（见 3.3 的退化方案）

## 5. 前端：操作列按钮间距

- [x] 5.1 在 `OrgManagementView.vue` 的 `<style scoped>` 里为操作列的相邻按钮增加更小的 `margin-left`（6px）覆盖规则

## 6. 验证

- [x] 6.1 `npm run build`（`vue-tsc` + `vite build`）通过
- [x] 6.2 复核阶段发现 48080 端口实际已空出（之前占用它的 IDE 调试实例已不在），补充做了真实的端到端 API 验证：`bootRun` 启动后端，用真实的多层级测试数据（`机构01→机构02→机构03→机构04→机构05`，共 5 层）对比新接口 `GET /api/orgs/tree/children` 与既有全量树接口 `GET /api/orgs/tree` 的返回结果，确认逐层查询（`parentId=7`/`10`/`13` 等）与全量树中对应子树完全一致，叶子节点返回空数组 `[]`；又启动 `npm run dev`，通过 vite 的 `/api` 代理确认前端到新接口的链路打通（`curl http://localhost:5173/api/orgs/tree/children?parentId=7` 返回正确数据）。验证完毕后已停止这两个临时启动的进程。仍未做的是浏览器可交互验证（实际点击展开节点观察懒加载渲染、增删改后观察左侧树局部刷新效果），因为环境里没有 Playwright/chromium-cli；这部分依赖对 Element Plus `tree-store`/`node` 源码（`node_modules/element-plus/es/components/tree/src/model/{tree-store,node}.mjs`）的逐行走读来确认 `load`/`updateKeyChildren`/`setData` 的行为符合预期（懒加载树的 `load` 在 `store.initialize()` 时对根节点无条件调用一次、`updateKeyChildren` 依赖真实 `node-key`、`:data` 变化触发 `store.setData` 重建对应层级），未做可视化确认
