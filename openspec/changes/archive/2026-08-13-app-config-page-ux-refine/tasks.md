## 1. 外层 tab 结构调整

- [x] 1.1 `AppConfigView.vue` 的 `activeTab` 类型从 `'basic' | 'signature' | 'sync'` 收窄为 `'basic' | 'sync'`，删除模板中 `name="signature"` 的"接口配置" `el-tab-pane`（含其中的签名算法表单与 `saveSignAlgorithm` 调用点）
- [x] 1.2 `signAlgorithmForm` ref 保留，改为在"同步配置" tab 的"基础同步配置"表单里渲染，用 `v-if="syncForm.needSign"` 控制展示，紧跟在"签名校验"开关之后；`el-form-item label` 从"需要签名/验签校验"改为"签名校验"

## 2. 基础同步配置与签名算法合并保存

- [x] 2.1 新增 `savingBasicSync` loading 状态，替换原 `savingSignAlgorithm`/`savingSync` 两个独立 loading 在模板里各自控制按钮的用法（实现时未保留两个旧 ref——因为 2.4 选择了内联 API 调用而不是复用 `saveSignAlgorithm`/`saveSyncConfig`，两个旧函数被整体删除，对应的旧 loading ref 也一并删除，不存在“供内部函数使用”的需要，避免死代码）
- [x] 2.2 新增 `saveBasicSyncConfig()` 函数：先执行现有 `validateNotifyUrl()` 前端校验，通过后 `Promise.all([appApi.updateAppSignAlgorithm(appId.value, { signAlgorithm: signAlgorithmForm.value }), appApi.updateAppSyncConfig(appId.value, { ...syncForm.value, notifyParams: notifyParamRowsToRecord() })])`，成功后用任一响应 `applyConfig`，`ElMessage.success('保存成功')`；失败时不吞异常，交由全局错误提示
- [x] 2.3 模板里"基础同步配置"分组下原来的"保存"按钮改为调用 `saveBasicSyncConfig`，`v-if` 权限判断从 `hasPermission('AppManagement:app:config:editSync')` 保持不变（不再额外判断 `editSignAlgorithm`）
- [x] 2.4 删除模板中不再被引用的"接口配置" tab 里原保存按钮（随任务 1.1 一并删除）；`saveSignAlgorithm`、`saveSyncConfig` 两个函数均不再被模板调用，`saveBasicSyncConfig` 内联直接调用 `appApi.updateAppSignAlgorithm`/`appApi.updateAppSyncConfig`，因此两个旧函数（及其独立 loading ref）已整体删除，避免死代码

## 3. 数据范围二级 tab 改造

- [x] 3.1 新增数据域二级 tab 的本地状态，例如 `const domainSubTab = reactive<Record<SyncDomain, 'enable' | 'orgScope' | 'fieldMapping'>>({ ORG: 'enable', USER: 'enable', POSITION: 'enable', APP: 'enable', ROLE: 'enable', DICT: 'enable' })`，各数据域互不影响，不做全局联动
- [x] 3.2 模板里每个数据域 `el-tab-pane`（`app-config__domain-panel` 内）新增一层 `el-tabs v-model="domainSubTab[option.value]"`：
  - "是否启用" `el-tab-pane`（`name="enable"`）：迁入原"是否启用"表单（启用开关+分页大小+保存按钮），六个数据域均展示
  - "同步范围" `el-tab-pane`（`name="orgScope"`）：迁入原"同步范围"区块（单选+组织行列表+保存按钮），仅 `v-if="orgScopeSupportedDomains.includes(option.value)"` 时渲染该 tab
  - "字段映射" `el-tab-pane`（`name="fieldMapping"`）：迁入原"字段映射"区块（新增字段下拉+表格+保存按钮），仅 `v-if="fieldMappingSupportedDomains.includes(option.value)"` 时渲染该 tab
- [x] 3.3 确认 `handleDomainTabChange`（切换左侧数据域一级 tab 时）保持现状——继续对该数据域一次性预加载"同步范围"与"字段映射"数据（不改成依赖二级 tab 的 `@tab-change` 才按需加载），避免用户点开二级 tab 时出现可感知的加载空白（design.md Decision 1 对应的风险缓解）
- [x] 3.4 调整 `.app-config__domain-panel` 及相关 scss 样式，确认二级 tabs 嵌入左侧纵向数据域 tab 内容区后视觉上不冲突（间距、边框），新增 `app-config__domain-sub-tabs` 样式类；顺带删除因二级 tab 标签已能表意而不再被模板引用的 `.app-config__org-scope-title`、`.app-config__field-mapping-title` 两个旧小标题样式（原来的"同步范围"/"字段映射"文字小标题被二级 tab 的 label 取代，不再需要）
- [x] 3.5 「同步范围」组织行里的组织选择 `el-tree-select` 原用 `style="width: 100%"` 撑满 `.app-config__org-scope-row__fields` 网格第一列（`grid-template-columns: 1fr auto`），在页面变宽后显得过长；改为 `grid-template-columns: minmax(0, 260px) auto` 给下拉框设置最大宽度上限，与"字段映射"新增下拉框的 260px 保持视觉一致

## 4. 字段映射新增行默认预填

- [x] 4.1 `handleAddField` 里插入新行时，`appFieldName: field.fieldName, appFieldCode: field.fieldCode` 替换原来的空字符串默认值

## 5. 权限资源文档

- [x] 5.1 `权限资源.txt` 中 `AppManagement:app:config:editSignAlgorithm` 行追加说明"已废弃，前端未使用"（不删除该行本身）

## 6. 验证

- [x] 6.1 `frontend/` 目录下执行 `npm run build`（vue-tsc 类型检查 + vite build）确认无类型错误——通过
- [ ] 6.2 本地 `npm run dev` 手动验证：签名校验开关联动签名算法展示与默认值、基础同步配置合并保存成功、六个数据域的二级 tab 展示是否与数据域能力矩阵（字典仅"是否启用"、应用/角色无"同步范围"）一致、字段映射新增行默认预填（按本次任务要求未做浏览器人工验证，留待用户自行用 `npm run dev` 验证）

## 7. 文档同步

- [x] 7.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致，如实现时有调整需回写（见 design.md 新增的实现落地说明）
