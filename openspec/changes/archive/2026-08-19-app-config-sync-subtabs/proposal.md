## Why

应用配置页面的“同步配置”一级 tab 目前是纵向堆叠的两块内容（“基础同步配置”表单 + “数据范围”左侧纵向 tabs），而“通知日志”“拉取日志”却被放在与“同步配置”平级的两个独立一级 tab 里，与它们实际描述/服务的对象（该应用的同步行为）在导航层级上割裂，用户需要在顶层 tab 之间跳转才能把“同步怎么配的”和“同步实际跑得怎样”对照着看。把四者收进同一层级的子 tab，能让“同步配置”一级 tab 内部的信息结构更一致，也更符合“配置”与“配置产生的日志”应当就近展示的直觉。

## What Changes

- 应用配置页面（`AppConfigView.vue`）的顶层 `el-tabs`（基础信息/同步配置/认证管理）不再包含“通知日志”“拉取日志”这两个一级 tab-pane。
- “同步配置”一级 tab-pane 内部新增一层 `el-tabs`，包含四个平级子 tab：基础同步配置、数据范围、通知日志、拉取日志，互斥展示，默认展示“基础同步配置”。
- “通知日志”“拉取日志”子 tab 首次被激活时才发起首次数据加载（沿用既有的按需加载策略），加载触发时机从原顶层 tab-change 事件迁移到新增的子级 tab-change 事件。
- 通知日志、拉取日志各自的过滤表单、表格列、分页交互、权限点（沿用页面级权限）均不变，仅做展示位置的迁移。
- 不涉及任何后端接口、数据模型或业务规则变更，纯前端 UI 结构调整。

## Capabilities

### New Capabilities

（无——不引入新能力）

### Modified Capabilities

- `app-api-credentials`：「应用管理前端"配置"入口与页面」需求里关于页面顶层 tab 结构、"同步配置"分区内部布局的描述需要更新——顶层从"两个分区"更正为当前实际的"基础信息/同步配置/认证管理"三个一级 tab（这处描述在此前 `app-auth-protocol-config`、`add-app-sync-notify-pull-logs` 变更时已经和实现脱节，借这次调整一并订正），并把"通知日志""拉取日志"补充为"同步配置"一级 tab 内部与"基础同步配置""数据范围"平级的第三、第四个子 tab。`app-sync-notify-pull` 能力本身的日志记录/查询行为（何时落库、如何过滤查询）不变，不涉及该 spec 的需求变化。

## Impact

- `frontend/src/views/application/app/AppConfigView.vue`：
  - `activeTab` 的可选值收窄为 `basic`/`sync`/`auth`（去掉 `notifyLog`/`pullLog`）。
  - 新增子级 `activeSyncSubTab`（或同名局部状态），承载“基础同步配置/数据范围/通知日志/拉取日志”四个子 tab 的激活态。
  - 模板结构调整：把现有“基础同步配置”表单、“数据范围”左侧纵向 tabs、“通知日志”“拉取日志”两个 tab-pane 的内容分别包进新增子级 `el-tabs` 的四个 `el-tab-pane` 里。
  - `handleActiveTabChange`（顶层 tab-change 处理函数）不再触发通知/拉取日志的按需加载；新增子级 tab-change 处理函数接管 `ensureNotifyLogLoaded`/`ensurePullLogLoaded` 的触发时机。
  - 样式上复用已有 `.app-config__domain-sub-tabs` 一类子级 tabs 的写法，保持视觉一致。
- 不涉及 `backend/` 任何改动。
