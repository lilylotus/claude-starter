## Context

`AppConfigView.vue`（`frontend/src/views/application/app/AppConfigView.vue`，约 1780 行单文件组件）当前用外层 `el-tabs`（`activeTab`：`basic`/`sync`/`auth`/`notifyLog`/`pullLog`，共 5 个一级 tab-pane）划分"基础信息/同步配置/认证管理/通知日志/拉取日志"五个分区。"同步配置"分区内部是纵向堆叠的两块：先是"基础同步配置"表单（同步方式、通知地址/参数、签名校验开关、签名算法），紧接着是"数据范围"区块（左侧纵向 `el-tabs`，`syncDomainTab`，在组织/用户/任职/应用/角色/字典六个数据域之间切换，每个数据域面板内部再是二级 `el-tabs` 展示"是否启用/同步范围/字段映射"）。"通知日志""拉取日志"则各自是与"同步配置"平级的独立一级 tab-pane，各自维护一套过滤表单 + 分页表格 + 按需加载状态（`notifyLogLoaded`/`pullLogLoaded` 及对应 `ensureNotifyLogLoaded`/`ensurePullLogLoaded`），加载时机挂在外层 `el-tabs` 的 `@tab-change="handleActiveTabChange"` 上。

本次调整只动导航结构：把"基础同步配置""数据范围""通知日志""拉取日志"四块内容收进"同步配置"一级 tab-pane 内部新增的一层 `el-tabs`，作为四个平级子 tab；"通知日志""拉取日志"不再是外层 `el-tabs` 的一级 tab-pane。通知/拉取日志各自的过滤表单、表格列、分页组件、`fetchNotifyLogList`/`fetchPullLogList`、按需加载策略均不改动，只搬迁模板位置和触发按需加载的 tab-change 挂载点。

## Goals / Non-Goals

**Goals:**
- 外层 `el-tabs`（`activeTab`）收窄为 `basic`/`sync`/`auth` 三个一级 tab-pane。
- "同步配置"一级 tab-pane 内部新增一层 `el-tabs`（新状态变量，如 `syncSectionTab`），包含"基础同步配置""数据范围""通知日志""拉取日志"四个平级子 tab-pane，默认展示"基础同步配置"，互斥展示。
- "通知日志""拉取日志"子 tab 首次激活时才触发首次加载，加载触发时机从外层 `handleActiveTabChange` 迁移到新增子级 `el-tabs` 的 `@tab-change` 处理函数。
- 样式复用页面已有的二级/子级 tabs 写法（参照 `.app-config__domain-sub-tabs`），新增一个同类的样式类名用于这层新的"同步配置"子级 tabs 容器，保持视觉一致，不引入新的视觉语言。

**Non-Goals:**
- 不改动通知日志、拉取日志各自的查询参数、表格列、分页交互、权限点（沿用页面级"应用配置页面访问"权限，不新增权限点）。
- 不改动"数据范围"内部左侧纵向数据域 tabs 及其二级 tabs（是否启用/同步范围/字段映射）的既有结构，这层结构原封不动地整体挪进新的"数据范围"子 tab-pane。
- 不改动任何后端接口、请求/响应结构、数据库表结构。
- 不改动"基础信息""认证管理"两个一级 tab-pane 的内部结构。

## Decisions

### Decision 1：新增一层子级 `el-tabs`，而不是用其他导航控件（如分段器/手风琴）承载四个板块
"数据范围"区块内部已经有先例——数据域面板内部用二级 `el-tabs` 区分"是否启用/同步范围/字段映射"三个子项（`domainSubTab`，见 `app-config-page-ux-refine` 变更）。这次"同步配置"一级 tab 内部新增的这层子 tabs 是同一种模式在更外一层的复用：默认横排（不设 `tab-position`，与 `domainSubTab` 一致），天然带出"同一时刻只展示一个板块"的语义，不需要额外写展开/收起状态，并且和页面里已有的多层 tabs 用法习惯一致，不引入新组件、新交互模式。
- **备选方案**：继续保持"基础同步配置"和"数据范围"纵向堆叠，只把"通知日志""拉取日志"作为新增的第三、第四块也纵向堆叠在下方。未采纳——这不满足用户明确要求的"平级子 tab、互斥展示、彼此覆盖"效果，且"数据范围"区块本身内容较高（六个数据域左侧 tabs + 表格），纵向堆叠会让"通知日志""拉取日志"需要向下滚动很远才能看到，用户体验不如互斥 tab 切换。

### Decision 2：新子级 tabs 的状态变量与激活值命名
新增 `syncSectionTab` 作为这层子 tabs 的激活态（类型 `'basicSync' | 'domainScope' | 'notifyLog' | 'pullLog'`），默认值 `'basicSync'`。沿用现有 `notifyLog`/`pullLog` 这两个字面量（原本是外层 `activeTab` 的取值），减少无谓的重命名成本；"基础同步配置"用 `basicSync`、"数据范围"用 `domainScope`，与模板里已有的 h4 标题文案（"基础同步配置"/"数据范围"）语义对应，不复用 `activeTab` 原来的 `sync` 值（避免和外层 `activeTab` 的 `sync` 混淆）。
- **备选方案**：复用/扩展外层 `activeTab` 的枚举类型，让它同时承载"哪个一级 tab"和"同步配置下选中哪个子 tab"两层信息（如 `sync:basicSync`）。未采纳——把两层导航状态压缩进一个变量会让 `handleActiveTabChange` 里的按需加载判断逻辑变复杂，不如两个独立状态变量职责清晰，也更贴近"数据范围"区块里 `syncDomainTab`/`domainSubTab` 两层分离状态的既有写法。

### Decision 3：按需加载触发时机的迁移方式
现有 `handleActiveTabChange`（外层 `el-tabs` 的 `@tab-change`）里判断 `activeTab.value === 'notifyLog'`/`'pullLog'` 触发 `ensureNotifyLogLoaded`/`ensurePullLogLoaded`；这两行判断整体挪到新增的子级 `el-tabs` 的 `@tab-change` 处理函数里（可以复用同一个 `ensureNotifyLogLoaded`/`ensurePullLogLoaded` 函数体，只改判断依据的变量从 `activeTab.value` 换成子级 tabs 的 `tab-change` 回调参数），`handleActiveTabChange` 本身不再需要处理这两个分支，只保留和"通知日志/拉取日志"无关的逻辑（当前该函数本来就只做这一件事，迁移后 `handleActiveTabChange` 若变为空函数体，直接移除该函数与外层 `@tab-change` 绑定即可，减少无用代码）。

## Risks / Trade-offs

- [页面首次打开默认停在"基础同步配置"子 tab，用户需要多点一次才能看到通知/拉取日志，而不是像现在一级 tab 那样一步到达] → 这是用户明确要求的目标布局本身的取舍（把日志和它所属的同步配置分组到一起），不做兜底；如果后续用户反馈"进入配置页想直接看日志"的场景更高频，可以再考虑给"同步配置"一级 tab 增加一个记忆用户上次停留的子 tab 的能力，本次不做。
- [现有 e2e/手工测试路径如果依赖 `activeTab` 直接等于 `notifyLog`/`pullLog` 来定位元素，会因为这两个值从外层 tab 移除而失效] → 检索仓库内是否有依赖这两个字面量的测试代码，若有需要同步更新选择器路径（改为先切"同步配置"一级 tab，再切子级 tab）。

## Migration Plan

纯前端结构调整，无数据迁移。部署即生效，无需灰度或回滚脚本；如需回滚，直接回退该次前端构建产物到上一版本即可。

## Open Questions

（无）
