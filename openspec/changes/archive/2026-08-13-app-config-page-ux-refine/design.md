## Context

`AppConfigView.vue`（`frontend/src/views/application/app/AppConfigView.vue`，约 1000 行单文件组件）当前用外层 `el-tabs`（`activeTab`：`basic`/`signature`/`sync`）划分"基础信息/接口配置/同步配置"三个分区。"同步配置"分区内先是一段"基础同步配置"表单（同步方式、通知地址/参数、`needSign` 开关），随后是"数据范围"区块——用左侧纵向 `el-tabs`（`syncDomainTab`）在组织/用户/任职/应用/角色/字典六个数据域之间切换，每个数据域面板内部再纵向堆叠"是否启用"（`domainConfigs`）、"同步范围"（`orgScopeState`，仅组织/用户/任职）、"字段映射"（`fieldMappingRowsCache`，组织/用户/任职/应用/角色）三个子区块，每个子区块各自一个独立保存按钮。同一数据域面板同屏最多同时出现 3 个保存按钮，加上外层"接口配置"（签名算法）与"基础同步配置"各一个，整页最多可见 5 个保存按钮。

三个数据获取/保存函数（`saveDomainConfig`、`saveOrgScope`、`saveFieldMappings`）与三份状态（`domainConfigs`、`orgScopeState`、`fieldMappingRowsCache`/`metadataFieldOptionsCache`）已经是相互独立、按需加载（`ensureFieldMappingLoaded`/`ensureOrgScopeLoaded`）的，本次改动不动这套数据层，只调整模板结构（增加一层二级 tab）和触发加载的时机。

## Goals / Non-Goals

**Goals:**
- 数据范围每个数据域面板内，把"是否启用"/"同步范围"/"字段映射"改成同一层级的二级 `el-tabs`（默认 `tab-position="top"`，展示在左侧数据域 tab 右侧的内容区），一次只显示一个子区块及其保存按钮。
- 去掉"接口配置" tab，把签名算法选择挪进"基础同步配置"表单，`needSign` 开关（改名展示为"签名校验"）勾选后才显示，默认 `SHA-256`；两者合并为一次保存点击、两次既有接口调用。
- 字段映射"新增字段"时应用字段名称/编码默认预填源字段的名称/编码。

**Non-Goals:**
- 不改动任何后端接口的请求/响应结构（`PUT .../config/sign-algorithm`、`PUT .../config/sync` 均保持原样，前端依旧各发一次请求）。
- 不改变数据范围各子区块本身的字段、校验规则、按需加载策略（仍是"切到某数据域才拉取一次"）。
- 不改变权限点定义本身（`editSignAlgorithm` 保留在后端/权限资源清单中，只是前端不再单独引用）。

## Decisions

### Decision 1：数据域面板内用二级 `el-tabs`，而不是继续纵向堆叠或改成手风琴
现有左侧纵向数据域 tab（`tab-position="left"`）视觉上已经是这条页面的主导航层级；子区块再嵌一层 `el-tabs`（默认顶部横排）比手风琴/分段器更符合 Element Plus 现有用法习惯，也天然带出"当前只有一个子区块可见"的语义，不需要额外写展开/收起状态。三个候选子 tab 里，"是否启用"对全部 6 个数据域都展示，"同步范围"仅组织/用户/任职（复用既有 `orgScopeSupportedDomains` 判断），"字段映射"仅组织/用户/任职/应用/角色（复用既有 `fieldMappingSupportedDomains` 判断，字典不展示）；用 `v-if` 按数据域过滤要渲染的二级 `el-tab-pane`，与外层数据域 tab 已有的按数组判断展示逻辑保持一致写法。
- **备选方案**：改成同一面板内的分段器（`el-segmented`）切换 + 单个内容区。未采纳——Element Plus 的分段器不如 `el-tabs` 语义清晰，且项目里目前没有该组件的既有用法先例，`el-tabs` 复用性更好。

### Decision 2：二级 tab 的激活状态按数据域独立记忆，而不是全局共用一个变量
用户切换左侧数据域 tab 后，二级 tab 应该分别记住各自数据域上次停留的子 tab（例如在"组织"里点开"字段映射"，切到"用户"后一般也更倾向先看"是否启用"这类默认项，而不是强行跟随另一数据域的选择）——实际做法更简单：二级 tab 统一用一个 `Record<SyncDomain, '启用'|'同步范围'|'字段映射'>`（键名用与现有代码风格一致的英文常量，如 `'enable' | 'orgScope' | 'fieldMapping'`）本地状态，默认值 `'enable'`，不做跨数据域联动，也不持久化到路由/localStorage（和现有 `syncDomainTab` 的记忆策略一致，纯前端易失状态）。
- **备选方案**：全局一个 `subTab` 变量，切换数据域时保持子 tab 选中项不变。未采纳——会出现切到不支持"同步范围"的"应用"数据域时，子 tab 选中值悬空（该 tab 不存在）需要额外兜底逻辑，按数据域独立记忆更简单、不需要兜底。

### Decision 3：签名算法与基础同步配置合并为一次点击、两次既有请求，不新增/合并后端接口
`signAlgorithmForm`（独立 ref）保留不变，只是模板位置从"接口配置" tab 移进"同步配置" tab 的"基础同步配置"表单里，条件渲染在 `syncForm.needSign` 为真时展示，默认值仍是 `'SHA256'`。原 `saveSignAlgorithm()`、`saveSyncConfig()` 两个函数保留，新增一个 `saveBasicSyncConfig()` 编排函数：先做现有的 `notifyUrl` 前端校验，校验通过后 `Promise.all([appApi.updateAppSignAlgorithm(...), appApi.updateAppSyncConfig(...)])` 并发提交两个请求（两者互不依赖对方结果，用 `Promise.all` 而不是串行更快），成功后用其中任一响应体 `applyConfig`（两个接口返回的 `AppConfigVO` 结构相同，取哪个都行，取签名算法请求的返回值即可），按钮的 `loading` 态合并为一个 `savingBasicSync`。不改动后端是因为这纯粹是前端展示层的合并，两个接口本身语义独立（一个改签名算法，一个改同步方式/通知配置/签名校验开关），没有必要为了省一次请求去改动已经上线可用的后端契约、徒增回归风险。
- **备选方案**：扩展 `SyncConfigUpdateRequest` 加一个 `signAlgorithm` 字段，后端合并成一个接口。未采纳——属于后端契约变更，超出本次"纯前端 UX 调整"的范围，且当前两个独立接口本身没有问题，合并请求不是本次要解决的问题（本次要解决的是"保存按钮太多"，不是"请求次数太多"）。

### Decision 4：`editSignAlgorithm` 权限点合并到 `editSync`，权限资源清单标注废弃但不删除
合并后的保存按钮统一受 `AppManagement:app:config:editSync` 控制（用户已确认此权限收敛方向）。`AppManagement:app:config:editSignAlgorithm` 权限点定义在 `权限资源.txt` 中保留（不删除该行），但追加说明其已不再被任何前端按钮引用，避免后续开发者疑惑"这个权限点去哪了"；已有角色-权限关联数据不受影响（该权限点即使被勾选也不再产生任何前端可见效果，纯粹冗余但无害）。
- **备选方案**：彻底删除该权限点及数据库中的关联记录。未采纳——删除权限点属于更大范围的清理动作（涉及已有角色数据、可能的审计影响），超出本次纯前端 UX 改动的范围；标注废弃是更保守、可回滚的选择。

### Decision 5：字段映射新增行默认预填源字段名称/编码，允许用户直接编辑覆盖
`handleAddField` 里插入新行时，`appFieldName`/`appFieldCode` 初始值从 `''` 改为 `field.fieldName`/`field.fieldCode`；两个输入框本身保持可编辑，用户仍可以改成不同的应用侧命名，只是把"多数情况下两者相同"这个常见场景的默认值省下来。不加任何"是否与源字段同名"的额外提示或联动清空逻辑，保持改动最小。

## Implementation Notes（实现落地时的调整）

- Decision 3 描述的“`saveSignAlgorithm()`、`saveSyncConfig()` 两个函数保留”在实际实现时未采纳：`saveBasicSyncConfig()` 直接内联调用 `appApi.updateAppSignAlgorithm`/`appApi.updateAppSyncConfig`（tasks.md 2.4 本身也预留了这个选项——“若改为直接内联调用则删除该函数，避免死代码”），因此两个旧函数、以及对应的 `savingSignAlgorithm`/`savingSync` 两个独立 loading ref 均已删除，只保留合并后的 `savingBasicSync`。这不影响对外行为（仍是两次既有接口调用、合并为一次按钮点击），纯粹是内部代码组织上更精简的选择。
- 数据范围区块的“同步范围”“字段映射”原本各自有一个文字小标题（`<h5>同步范围</h5>`、`<span>字段映射</span>`），二级 tab 化后这两个标题的语义已经由 tab 的 label（“同步范围”“字段映射”）承担，保留会造成视觉重复，因此实现时一并删除了这两处小标题及对应的 `.app-config__org-scope-title`、`.app-config__field-mapping-title` 样式类；“是否启用”二级 tab 内部仍保留了原有的 `el-form-item label="是否启用"`（因为该 tab 内还有“拉取分页大小”这个平级表单项，需要区分展示，不是纯粹的重复小标题）。

## Risks / Trade-offs

- [风险] 二级 tab 引入后，`ensureFieldMappingLoaded`/`ensureOrgScopeLoaded` 目前是切换*数据域*一级 tab 时触发；现在字段映射/同步范围区块可能要等用户点开对应二级 tab 才首次可见，若仍然只在切一级 tab 时预加载没有问题（数据仍会按需加载好，只是二级 tab 未激活时不渲染），但如果实现时改成"只有点开二级 tab 才发请求"，需要同时在二级 tab 的 `@tab-change` 上也接上对应的 `ensureXxxLoaded` 调用，否则首次点开子 tab 时会有一次可感知的加载空白 → 缓解：实现时保持"切一级数据域 tab 就把该数据域下所有支持的子区块数据一次性预加载好"的现有策略不变，二级 tab 只负责展示，不额外触发按需加载。
- [风险] 签名算法与同步配置合并请求后，如果其中一个接口失败（如 `notifyUrl` 校验在后端也拒绝了，或权限校验失败）而另一个成功，会出现"保存了一半"的状态 → 缓解：`Promise.all` 任一失败即整体走 `catch`（不显式吞掉单个失败），已经成功的那一个后端状态确实会保留（这是两个独立资源的固有特性，无法在纯前端层面做成原子操作），但 UI 上仍统一提示失败、不展示"保存成功"，用户可以重新点击保存重试（重试是幂等的，两个接口都是覆盖式更新）。
