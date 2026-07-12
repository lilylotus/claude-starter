## Why

组织管理页面当前要求用户先手动点击左侧组织树的某个节点，右侧才会展示数据，首次进入是空白的；子组织数量较多时右侧表格一次性全量加载，缺少分页会影响可用性；选中节点后右侧标题文案是固定的"下级组织"，看不出当前展示的是哪个组织下的子组织；此外，新增组织时"上级组织"选择器一度尝试禁用代表顶级组织的虚拟节点（防止顶级结构被意外破坏），但用户实测后明确要求恢复该能力——否则完全无法新增第一层级组织，业务上是必需的。这几点在真实使用中体验不佳，需要一并调整。

## What Changes

- 组织管理页面进入时自动展示顶级组织列表（无需先点击左侧树节点），首次进入且未点击任何节点时右侧标题保持空白；一旦点击左侧树的具体节点，标题变为"[组织名称]下级组织"。
- 右侧子组织列表（含默认的顶级组织列表）改为后端真分页：`GET /api/orgs/children` 新增 `page`、`pageSize`（默认第 1 页、每页 10 条）参数，返回总条数，前端用 `el-pagination` 展示分页控件。**BREAKING**：`GET /api/orgs/children` 的响应结构从裸数组变为 `{ records, total, page, pageSize }` 分页包装对象。
- 新增组织弹窗的"上级组织"选择器中，代表"顶级组织（parentId=0）"的虚拟节点可正常被选中，用于新增第一层级组织；树中已存在的真实组织节点（无论是否已有子组织）也均可被选为上级组织。新增时若已选中左侧树某节点，上级组织默认预填为该节点，且该字段保持可编辑，可手动改选为其他任意组织（含顶级组织）。
- 右侧表格隐藏网格线（去掉 `el-table` 的 `border`）。

## Capabilities

### Modified Capabilities
- `org-management`: 直属子组织查询接口从全量返回改为分页返回（新增 `page`/`pageSize` 请求参数与 `total`/分页包装响应）；前端组织管理界面的默认展示状态（进入即显示顶级组织分页列表而非空白）、右侧标题文案规则、新增时上级组织选择器的可选节点范围（含顶级组织在内的任意节点均可选）均发生变化。

## Impact

- 后端：`OrgController#children`、`OrgService#getChildren`、`OrgServiceImpl#getChildren`、`OrgMapper`（改用 MyBatis-Plus `Page` 查询）；新增通用分页响应 DTO（`common/` 包下）。
- 前端：`views/identity/org/OrgManagementView.vue`、`stores/org.ts`、`api/org.ts`、`types/org.ts`（子组织列表类型改为分页结构）。
- 无数据库结构变更；不影响组织树接口 `GET /api/orgs/tree`（该接口仍然一次性返回完整树，不分页）。
