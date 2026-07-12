## 1. 后端：通用分页响应

- [x] 1.1 新增 `cn.nihility.rbac.common.PageResult<T>`（字段：`records`、`total`、`page`、`pageSize`）

## 2. 后端：直属子组织接口分页化

- [x] 2.1 `OrgService#getChildren` / `OrgServiceImpl#getChildren` 改为接收 `page`、`pageSize`，用 MyBatis-Plus `Page<OrgEntity>` 做 `selectPage` 查询，返回 `PageResult<OrgVO>`（复用现有 `toVOListWithParentName` 转换当前页数据）
- [x] 2.2 `OrgController#children` 新增 `page`（默认 `1`）、`pageSize`（默认 `10`）请求参数，返回类型改为 `PageResult<OrgVO>`，更新 Swagger `@Parameter` 描述
- [x] 2.3 手动验证：`GET /api/orgs/children`、`GET /api/orgs/children?parentId=0`、带/不带 `page`/`pageSize` 参数均返回预期的分页结构与 `total`（复核时发现本地实际有可用 MySQL，已用 `bootRun` + 真实数据 curl 验证；过程中发现并修复了一个关键 bug——见下方 2.4）
- [x] 2.4（复核新增）修复分页不生效的 bug：MyBatis-Plus 3.5.16 把 `PaginationInnerInterceptor` 依赖的 JSQLParser 拆到了独立的 `com.baomidou:mybatis-plus-jsqlparser` 模块，项目里一直没有引入这个依赖、也没有注册 `MybatisPlusInterceptor` bean，导致 `selectPage` 实际不分页（`total` 恒为 0，换页拿到同一批数据）。已征得用户同意后在 `backend/build.gradle` 新增 `mybatis-plus-jsqlparser:3.5.16` 依赖，并新增 `common/config/MybatisPlusConfig.java` 注册分页插件；修复后用真实数据（3 条顶级组织、`pageSize=2`）验证了 `page=1`/`page=2` 返回不同记录且 `total=3`，`./gradlew test` 全部通过

## 3. 前端：类型与接口封装

- [x] 3.1 `types/org.ts` 新增分页响应类型（如 `PageResult<T>`：`records`、`total`、`page`、`pageSize`）
- [x] 3.2 `api/org.ts` 的 `getOrgChildren` 改为接收 `parentId`、`page`、`pageSize` 参数，返回 `PageResult<OrgRow>`

## 4. 前端：store 与分页状态

- [x] 4.1 `stores/org.ts` 的 `fetchChildren` 增加 `page`/`pageSize` 状态（默认 `page=1`、`pageSize=10`）与 `total`，改为调用新的分页接口
- [x] 4.2 `selectNode` 切换选中节点时重置 `page` 为 `1`
- [x] 4.3 `refreshAfterMutation` 刷新时保持当前 `page`；若刷新后 `page` 超出新的总页数则回退到最后一页
- [x] 4.4 `onMounted`（组件侧）触发一次隐式的顶级组织查询（`parentId = 0`），但不设置 `selectedId`（保持 `null`）

## 5. 前端：右侧面板默认态与动态标题

- [x] 5.1 `OrgManagementView.vue` 中把面板标题从静态 `<h2>下级组织</h2>` 改为按 `selectedId === null ? '' : \`[${选中节点名称}]下级组织\`` 计算的动态文案（后续复核调整为方括号包裹节点名称）
- [x] 5.2 新增一个从 `orgStore.tree` 按 `selectedId` 查找节点名称的辅助函数（递归查找）
- [x] 5.3 `el-table` 下方接入 `el-pagination`（`page-size` 默认 10，展示 `total`），切换页码时调用 `orgStore.fetchChildren` 并保持当前 `parentId`、不改变标题

## 6. 前端：新增/编辑上级组织选择器

- [x] ~~6.1 `treeSelectData` 里的虚拟"顶级组织"节点加 `disabled: true` 字段~~（已在 6.5 反转，虚拟节点恢复为可选中）
- [x] ~~6.2 `el-tree-select` 的 `:props` 补充 `disabled: 'disabled'` 映射，使虚拟顶级节点渲染为不可选中，真实顶级组织节点不受影响~~（已在 6.5 反转，移除该映射）
- [x] 6.3 `openCreateDialog`：未选中任何左侧树节点时，上级组织字段不预填默认值（不再兜底为 `0`）
- [x] 6.4 修复真实顶级组织节点"选不了"的 bug：`el-tree-select` 默认单选模式下只有叶子节点点击才会真正选中，非叶子节点点击只会展开/收起——凡是已经有子组织的真实组织节点（含顶级组织）都无法被选为上级组织，违反了 6.2 的设计意图。修复方式是给 `el-tree-select` 加 `check-strictly`，使任意节点（无论是否有子节点）点击都能被选中；同时把展示表格的 `el-table` 去掉 `border` 属性，隐藏表格网格线
- [x] 6.5（用户反馈后新增，反转 6.1/6.2）恢复虚拟顶级组织节点的可选中能力：用户实测发现禁用虚拟节点导致完全无法新增第一层级组织，明确要求恢复。移除 `treeSelectData` 虚拟节点的 `disabled: true` 字段，移除 `el-tree-select` 的 `:props` 里 `disabled: 'disabled'` 映射；选中虚拟顶级节点后 `form.parentId` 为 `0`，新增/编辑后的组织成为第一层级组织。`pruneSubtree`（防止选自身/子孙节点为上级）逻辑不受影响

## 7. 文档与收尾

- [x] 7.1 实现完成后核对 `openspec/changes/org-mgmt-tree-ux-tweaks` 下的 proposal/design/tasks 与实际实现是否一致（交给 `openspec-doc-sync`）
