## 1. 状态变量调整

- [x] 1.1 `activeTab` 类型收窄为 `'basic' | 'sync' | 'auth'`（去掉 `'notifyLog' | 'pullLog'`）
- [x] 1.2 新增 `syncSectionTab` 状态（类型 `'basicSync' | 'domainScope' | 'notifyLog' | 'pullLog'`，默认值 `'basicSync'`），承载"同步配置"一级 tab 内部四个子 tab 的激活态

## 2. 模板结构调整

- [x] 2.1 在"同步配置"（`name="sync"`）一级 `el-tab-pane` 内部，把原本紧挨着的"基础同步配置"表单、"数据范围"（含左侧纵向数据域 tabs 及其二级 tabs）整体包进新增子级 `el-tabs`（`v-model="syncSectionTab"`）的两个 `el-tab-pane`（`name="basicSync"` / `name="domainScope"`）
- [x] 2.2 把原本作为一级 `el-tab-pane`（`name="notifyLog"` / `name="pullLog"`）的通知日志、拉取日志模板整体搬迁为同一层子级 `el-tabs` 下的另外两个 `el-tab-pane`（`name="notifyLog"` / `name="pullLog"`），从外层 `el-tabs` 中移除这两个一级 `el-tab-pane`
- [x] 2.3 子级 `el-tabs` 上绑定新的 `@tab-change` 处理函数（如 `handleSyncSectionTabChange`），移除或清空外层 `el-tabs` 上 `handleActiveTabChange` 里判断 `notifyLog`/`pullLog` 的分支

## 3. 按需加载迁移

- [x] 3.1 `handleSyncSectionTabChange` 内根据切换到的子 tab 名调用既有的 `ensureNotifyLogLoaded()` / `ensurePullLogLoaded()`，保持"首次激活对应子 tab 才发起首次查询"的既有语义不变
- [x] 3.2 确认外层 `handleActiveTabChange` 移除通知/拉取日志分支后是否仍有其他职责；若已无职责，一并移除该函数定义与外层 `el-tabs` 上的 `@tab-change` 绑定

## 4. 样式调整

- [x] 4.1 参照已有 `.app-config__domain-sub-tabs` 的写法，为新增的"同步配置"子级 `el-tabs` 补一个同类命名的样式类（如 `.app-config__sync-section-tabs`），保持顶部横排子 tabs 的视觉与页面既有多层 tabs 一致
- [x] 4.2 检查搬迁后通知日志/拉取日志的过滤表单、表格、分页在新的子 tab-pane 容器下间距/对齐是否需要微调（原样式类 `.app-config__log-filter-form`、`.app-config__log-pagination` 预期无需改动，仅确认视觉无回归）

## 5. 验证

- [x] 5.1 `npm run build`（`vue-tsc` 类型检查 + `vite build`）通过
- [ ] 5.2 本地 `npm run dev` 手工验证：进入应用配置页，"同步配置"一级 tab 内默认展示"基础同步配置"；依次点击"数据范围""通知日志""拉取日志"子 tab，确认互斥展示、内容与迁移前一致；首次点开"通知日志""拉取日志"子 tab 时各自发起一次查询请求（用浏览器网络面板确认，且切回再切入不重复请求）（本次未起后端服务，未做浏览器手工验证，仅完成 build 通过 + 代码走查）
- [x] 5.3 确认"基础信息""认证管理"两个一级 tab 行为不受影响（模板/脚本均未改动这两个一级 tab-pane 的内部结构）

## 6. OpenSpec 收尾

- [ ] 6.1 实现完成后运行 `openspec-doc-sync` 对齐 `proposal.md`/`design.md`/`tasks.md` 与实际改动
- [ ] 6.2 视用户指示决定是否执行 `openspec-sync-specs` 把本变更的 delta spec 应用到 `openspec/specs/app-api-credentials/spec.md`（归档仍为用户手动触发，不自动执行）
