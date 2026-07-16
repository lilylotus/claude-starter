## Context

用 Playwright 在真实浏览器里对当前实现做了像素级测量（数据：`机构01(depth0)→机构04(depth1)→机构05(depth2)→机构09(depth3)→机构10(depth4)→机构11(depth5)`）：

- 组织管理左侧树面板（`.org-panel--tree`）实测宽度 280px（左边界 256px，右边界 536px，视口 1400px 宽时）。
- `el-tree-node__content` 的内联 `padding-left`（Element Plus 默认 `indent`，项目未覆盖）逐层 +18px：depth0=0px、depth1=18px……depth5=90px。
- 节点内容框自身左边界（`getBoundingClientRect().left`）逐层再 +23px（来自 `.org-tree :deep(.el-tree-node__children)` 的 `margin-left: 8px` + `padding-left: 14px` + `border-left: 1px`）：depth0=277px、depth1=300px……depth5=392px。
- 两者叠加，depth5 节点文本起始位置约在 x=482（392+90），而面板右边界减去右内边距后可用边界约 x=516，只剩 30～40px——"机构11"三个汉字加展开箭头已经放不下，与用户报告的"5 级组织看不全"完全对应。
- `position-tree` 的 CSS 规则（`.position-tree :deep(.el-tree-node__children)` 等）与 `.org-tree` 完全同构，同样的问题会在任职管理页面复现。

侧边栏二级菜单实测（`SideNav.vue`）：`.side-nav` 容器宽度 232px；`.el-sub-menu__title` 左边界相对侧边栏为 8px（`.side-nav__menu` 自身 `padding: 8px`）；展开后 `.el-sub-menu .el-menu-item` 的内容框左边界为 51px——即 `margin-left: 26px + padding-left: 16px + border-left: 1px = 43px` 的自定义缩进，在 232px 宽的容器里占比约 19%。这里不存在文本裁切（菜单项文案短，展开后还有约 140px 可用宽度），是纯粹的视觉比例问题。

## Goals / Non-Goals

**Goals:**
- 组织管理、任职管理左侧组织树：展开到第 5 层时，节点名称、展开箭头在默认面板宽度（280px）下完整可见，不发生裁切、不需要面板整体横向滚动。
- 侧边栏二级菜单缩进收紧，视觉比例更紧凑。
- 两处的"虚线 + 圆点"链式连接视觉语言保持不变，只调整间距数值，不改变交互行为（展开/收起、点击选中等逻辑完全不动）。

**Non-Goals:**
- 不改变组织树/任职树的懒加载、展开收起等交互逻辑（本次纯样式调整）。
- 不改变左侧面板本身的宽度（280px）或侧边栏宽度（232px 展开态）。
- 不引入"深层级自动截断文本 + tooltip 显示完整名称"之类的新交互兜底方案——先通过收紧缩进解决当前实测到的 5 层裁切问题，是否需要更深层级的兜底留待以后有实际需求再评估。

## Decisions

### 1. 组织树/任职树：`el-tree` 新增 `:indent="8"`，自定义装饰间距从 `8+14+1=23px` 收紧到 `4+6+1=11px`
- `OrgManagementView.vue`、`PositionManagementView.vue` 的 `<el-tree>` 标签新增 `indent="8"`（Element Plus 默认 18px，这里直接调用组件自带的 prop，不用额外 CSS 覆盖内联样式）。
- `.org-tree`/`.position-tree` 的 `:deep(.el-tree-node__children)` 规则：`margin-left: 8px → 4px`、`padding-left: 14px → 6px`；对应的圆点 `::before` 的 `left: -14px → -6px`（圆点位置与 `padding-left` 严格对应，改一处另一处必须同步改，否则圆点会偏离虚线）。
- 收紧后单层增量从约 41px（18 + 23）降到约 19px（8 + 11），depth5 累计缩进从约 205px 降到约 95px，面板可用宽度 240px 下能留出约 145px 展示文本+图标，相比收紧前的 30～40px 有数倍余量。
- 理由：分别调小两个独立来源（组件自带缩进 + 自定义装饰间距）比只调其中一个更均衡——只调装饰间距的话圆点/虚线会显得和实际树形层级脱节；只调组件缩进的话链式连接的视觉密度会显得过密。

### 2. 侧边栏二级菜单：`.side-nav__menu :deep(.el-menu)` 从 `margin-left: 26px + padding-left: 16px` 收紧到 `margin-left: 14px + padding-left: 8px`
- 圆点 `::before` 的 `left` 值（当前 `-21px`，与 `padding-left` 不是简单的 1:1 对应关系，是此前实现时针对 `el-menu-item` 自身默认内边距一起调出来的经验值）不能按比例换算，改为用 Playwright 直接测量 `.el-sub-menu .el-menu`（虚线所在的边框盒）与 `.el-menu-item`（圆点定位参照的自身盒子）两者在屏幕上的实际左边界坐标，取差值得到精确的 `left` 值：实测边框线 x=22、菜单项自身左边界 x=31，差值 `22-31=-9`，即最终 `left: -9px`，并用截图确认圆点准确落在虚线上（结果记录在 tasks.md 3.2）。
- 理由：`margin-left`（整个子菜单容器相对父级的偏移）和 `padding-left`（虚线与菜单项之间的留白）各collapse 大约一半，是"肉眼可感知变紧凑但仍保持层级可辨识"和"两者的相对比例”之间的折衷取值；不做到全部为 0 是因为完全贴边会让虚线和菜单项之间失去呼吸空间，看起来拥挤。

## Risks / Trade-offs

- [Risk] 组织树缩进收紧后，层级之间的视觉区分度降低（相邻两层之间的横向距离变小）→ Mitigation：虚线 + 圆点的链式连接视觉语言保留，即使横向缩进变小，用户仍能通过连接线和圆点辨认父子关系，不是纯靠留白区分层级。
- [Risk] 若未来组织层级继续加深（6 层及以上），即使收紧后也可能重新出现裁切 → Mitigation：本次目标是解决已实测到的 5 层裁切问题，不是消灭"任意深度都不裁切"这个更强的保证；更深层级的兜底（如虚拟滚动、文本省略+tooltip）留到有实际业务数据出现更深层级时再评估，属于本次 Non-Goals。
- [Risk] 侧边栏圆点的 `left` 偏移量需要凭截图手动调，不是严格公式计算，未来如果再改 `padding-left`/`margin-left` 容易忘记同步调圆点位置 → Mitigation：在对应 CSS 规则旁补充注释说明两者需要一起改（沿用组织树那两条规则已有的写法）。

## Open Questions

无。
