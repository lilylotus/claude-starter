## 1. 组织管理左侧组织树缩进收紧

- [x] 1.1 `OrgManagementView.vue` 的 `<el-tree>` 新增 `indent="8"`（Element Plus 默认 18px）
- [x] 1.2 `.org-tree :deep(.el-tree-node__children)`：`margin-left: 8px → 4px`、`padding-left: 14px → 6px`
- [x] 1.3 对应圆点 `.org-tree :deep(.el-tree-node__children > .el-tree-node::before)`：`left: -14px → -6px`

## 2. 任职管理左侧组织树缩进收紧（与 1 同幅度）

- [x] 2.1 `PositionManagementView.vue` 的 `<el-tree>` 新增 `indent="8"`
- [x] 2.2 `.position-tree :deep(.el-tree-node__children)`：`margin-left: 8px → 4px`、`padding-left: 14px → 6px`
- [x] 2.3 对应圆点 `.position-tree :deep(.el-tree-node__children > .el-tree-node::before)`：`left: -14px → -6px`

## 3. 侧边栏二级菜单缩进收紧

- [x] 3.1 `SideNav.vue` 的 `.side-nav__menu :deep(.el-menu)`：`margin-left: 26px → 14px`、`padding-left: 16px → 8px`
- [x] 3.2 对应圆点 `.side-nav__menu :deep(.el-sub-menu .el-menu-item::before)` 的 `left` 值：用 Playwright 实测 `.el-sub-menu .el-menu` 边框线的实际屏幕坐标（`borderLineX=22`）与菜单项自身左边界（`itemLeft=31`），得出精确值 `left: -21px → -9px`（`22-31=-9`，不是按比例估算的 -10~-13，而是量出来的精确值），截图确认圆点准确落在虚线上

## 4. 验证

- [x] 4.1 `npm run build`（`vue-tsc` + `vite build`）通过，无类型错误
- [x] 4.2 真实浏览器验证（Playwright，`bootRun` 48080 + `vite --host 127.0.0.1` 5173）：用测试数据 `机构01→机构04→机构05→机构09→机构10→机构11`（6 层链路）分别在组织管理、任职管理页面展开到第 5 层并截图对比：
  - 收紧前：单层增量 41px（el-tree 内联 `padding-left` 每层 +18px + 自定义装饰每层 +23px），depth5（机构11）节点内容框左边界 x=482，面板可用右边界约 x=516，仅剩 30～40px——截图显示"机构10"已局促、"机构11"完全被裁掉只剩残缺字符。
  - 收紧后：单层增量 19px（el-tree `indent=8` 每层 +8px + 自定义装饰每层 +11px），depth5 节点内容框左边界降到 x=372，可用宽度余量增至约 144px——截图显示"机构11"完整可见，虚线圆点与层级关系清晰。
  - 任职管理页面（`position-tree`）用同一条链路截图确认与组织管理页面效果一致。
- [x] 4.3 真实浏览器验证：展开侧边栏"身份管理"一级菜单分组并截图，确认二级菜单整体缩进从 43px 收紧到约 22px（`margin-left 14 + padding-left 8 + border 1`），圆点精确落在虚线上（无偏移），菜单项文字（"组织管理"/"用户管理"/"任职管理"）无裁切。验证完毕后已停止两个临时进程，清理了 scratchpad 里的验证脚本、临时 npm 依赖与截图
