## Why

用真实数据（`机构01→机构04→机构05→机构09→机构10→机构11`，6 层深度）在浏览器里实测组织管理页面左侧组织树的当前缩进：Element Plus `el-tree` 默认逐层缩进 18px（未设置 `:indent`），叠加项目自定义的"链式连接"装饰（`.el-tree-node__children` 的 `margin-left: 8px` + `padding-left: 14px` + 1px 边框），每下探一层节点内容框整体再右移约 23px，两者相加约 41px/层。左侧面板固定宽度 280px（减去左右各 20px 内边距后可用宽度 240px），展开到第 5 层时节点文本起始位置已经逼近面板右边界，只剩约 30～40px 空间，组织名称基本被裁切看不全，与用户报告一致。任职管理页面左侧组织树复用完全相同的结构与样式（`position-tree` 与 `org-tree` 的 CSS 规则一一对应），存在同样的问题。

同时侧边栏二级菜单（`SideNav.vue`）的"链式连接"装饰把整个 `<el-menu>` 子菜单容器右移 `margin-left: 26px + padding-left: 16px + 1px 边框 = 43px`，在仅 232px 宽的侧边栏里占比接近 19%，视觉上偏松散，用户反馈需要收紧（这一处不是信息裁切问题，是缩进比例观感问题）。

## What Changes

- 组织管理、任职管理页面左侧组织树：降低 `el-tree` 的逐层缩进（新增 `:indent` 属性，从默认 18px 减小）并同步收紧自定义链式连接装饰的间距，使展开到第 5 层时组织名称仍能在默认面板宽度下完整可见。
- 侧边栏二级菜单：收紧链式连接装饰的缩进间距。

## Capabilities

### Modified Capabilities
- `org-management`: 左侧组织树的逐层缩进收紧，深层级节点名称在默认面板宽度下保持完整可见，不做业务行为变更。
- `position-management`: 左侧组织树的逐层缩进收紧（与 `org-management` 采用同一收紧幅度），不做业务行为变更。

### Added Capabilities
- `navigation`: 首次为侧边导航（`AppLayout.vue`/`SideNav.vue`）建立独立的 spec 能力，覆盖二级菜单缩进这一视觉规范；此前侧边导航整体作为应用外壳未被任何 capability 覆盖。

## Impact

- 前端：`views/identity/org/OrgManagementView.vue`（`el-tree` 新增 `:indent`，`.org-tree` 相关 CSS 数值调整）、`views/identity/position/PositionManagementView.vue`（同上，`.position-tree`）、`layout/components/SideNav.vue`（`.side-nav__menu` 相关 CSS 数值调整）。
- 后端：无变化。
- 数据库：无变化。
