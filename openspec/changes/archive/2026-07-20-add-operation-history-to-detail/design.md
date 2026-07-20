## Context

已归档的 `add-operation-log` 变更已经打好了全部基础设施：`tab_operation_log` 表、`OperationLogRecorder`（手动记录，已接入全部 9 个业务模块的 create/update/enable/disable/delete）、只读查询接口 `GET /api/operation-logs`（支持 module/resourceType/operationType/createBy/时间范围筛选，按 `create_time` 降序）与 `GET /api/operation-logs/{id}`（含结构化字段变更列表）。本次改动不新增记录逻辑、不新增表，纯粹是"把已有数据以更贴近使用场景的方式在既有详情弹窗里再展示一次"，属于查询侧的增强。

9 个业务模块中，除字典管理外的 8 个（组织、用户、任职、应用、角色、权限点、管理员、菜单）都已经有统一模式的只读"详情"弹窗：点击列表行"详情" → 调用各自的 `GET /api/xxx/{id}` → `el-dialog` + `el-descriptions` 展示完整字段。字典管理比较特殊：`GET /api/dict-types/{id}`、`GET /api/dict-items/{id}` 这两个后端详情接口本身已经存在（backend 早已实现、spec 也已记录），但前端 `DictManagementView.vue` 从未实现对应的只读详情弹窗（只有新增/编辑弹窗），`权限资源.txt` 里也确实没有 `DictManagement:dictType:detail`/`DictManagement:dictItem:detail` 这两条编码——这是本次需要一并补上的缺口，而不是本次新引入的需求。

## Goals / Non-Goals

**Goals:**
- `GET /api/operation-logs` 支持按 `resourceType` + `targetId` 查询单个资源实例的操作历史。
- 组织、用户、任职、应用、角色、权限点、管理员、菜单这 8 个模块的详情弹窗内嵌入操作历史列表（新增/编辑/启用/停用，按操作时间降序）。
- 补齐字典类型、字典项的只读详情弹窗（此前完全不存在），同样嵌入操作历史列表。
- 抽取可复用的 Vue 组件，避免"字段变更详情弹窗"这块 UI 在 10 个地方（9 个详情弹窗 + 1 个独立操作日志页面）被重复实现。

**Non-Goals:**
- 不改变 `OperationLogRecorder` 的记录逻辑、不新增审计字段。
- 不在嵌入的历史列表里提供筛选栏（模块/资源类型对该资源实例而言是固定值，操作人/时间范围筛选在独立的操作日志页面已经提供，没必要在详情弹窗里重复）。
- 不展示删除记录——不是刻意过滤，而是软删除机制的自然结果：一条记录被逻辑删除后，其详情弹窗本身已经不可访问（`GET /api/xxx/{id}` 对已删除记录直接返回业务错误），所以永远不会有机会在"某条记录的详情弹窗"里看到"删除了它自己"这条记录，不需要在查询或前端加任何 `operationType != 5` 的过滤代码。
- 不做操作历史的导出、不提供"跳转到独立操作日志页面并带过滤条件"的快捷链接（后续如有需要可以是独立的小改动）。

## Decisions

### 1. 后端：`GET /api/operation-logs` 新增 `targetId` 筛选参数

`OperationLogQueryRequest` 新增 `targetId: Long`（可选）；`OperationLogController#page` 新增对应 `@RequestParam(required = false) Long targetId`；`OperationLogMapper.xml` 的动态查询 SQL 新增 `<if test="targetId != null">AND target_id = #{targetId}</if>`，与现有的 `resourceType` 条件同级并列。不新增专门的"按资源实例查历史"端点——现有分页接口的筛选参数模型天然支持这种扩展，`resourceType` + `targetId` 组合已经能精确定位到单个资源实例的全部历史，复用比新增端点更简单，也不需要新的 DTO。

`targetId` 单独出现（不带 `resourceType`）时不做特殊校验或报错，只是会跨资源类型按 `target_id` 精确匹配——不同资源类型的 `target_id` 是各自表的自增主键，天然会有数值重叠（如组织 id=5 和角色 id=5 是不同的记录），因此前端使用时 SHALL 始终同时传 `resourceType` + `targetId` 两个参数，后端不做强制校验（与其他筛选参数的处理方式一致，均为独立的可选条件）。

### 2. 前端：抽取 `OperationLogDetailDialog.vue`

把 `OperationLogManagementView.vue` 里内联的"字段变更详情"只读弹窗（`el-dialog` + `el-descriptions` + 字段变更 `el-table`）原样抽取成 `src/components/OperationLogDetailDialog.vue`：

```
Props: modelValue: boolean（详情弹窗可见性，配合 v-model 使用）、logId: number | null
Emits: update:modelValue
```

组件内部 `watch([() => props.modelValue, () => props.logId])`，当弹窗打开且 `logId` 有值时调用 `operationLogApi.getOperationLogById(logId)` 加载详情。`OperationLogManagementView.vue` 改为使用该组件（`<OperationLogDetailDialog v-model="detailVisible" :log-id="selectedLogId" />`），消除原本内联的一份重复标签；后续 `OperationHistoryPanel` 复用同一个组件，两处共用同一份展示逻辑与样式，不会出现"独立操作日志页面"和"详情弹窗内嵌历史"两处渲染不一致的问题。

### 3. 前端：新增 `OperationHistoryPanel.vue`

```
Props: resourceType: string、targetId: number | null
```

内部逻辑：
- `watch(() => props.targetId, ..., { immediate: true })`，`targetId` 有值时重置分页到第 1 页并请求 `operationLogApi.getOperationLogPage({ resourceType: props.resourceType, targetId: props.targetId, page, pageSize: 5 })`（`targetId` 为 `null` 时不发请求，展示空状态，用于详情数据尚未加载完成的过渡态）。
- 展示：小标题"操作历史" + `el-table`（列：操作时间、操作类型（`el-tag`，复用与独立操作日志页面一致的颜色映射）、操作人、操作 → "查看变更"链接按钮）+ `el-pagination`（`small`，`page-size=5`，与详情弹窗紧凑的视觉密度匹配，不使用独立页面那种大分页组件）+ 空状态文案"暂无操作记录"。
- 点击"查看变更"记录选中的 `logId`，打开内部持有的 `<OperationLogDetailDialog>`（详情弹窗叠在详情弹窗之上，Element Plus 原生支持嵌套 `el-dialog`，`append-to-body` 默认行为即可正确处理层级，不需要特殊处理）。

页面大小固定为 5，不做可调节的每页条数选择器——这是详情弹窗内的辅助信息区块，不是主列表，克制展示密度，需要看更多历史时用户可以直接翻页而不需要放大每页条数。

### 4. 嵌入 8 个已有详情弹窗

组织、用户、任职、应用、角色、权限点、管理员、菜单这 8 个模块的 `XxxManagementView.vue` 详情弹窗内，在既有的 `el-descriptions`（或用户模块额外的任职记录表格）之后、`el-dialog` 的 `#footer` 之前，插入：

```html
<OperationHistoryPanel :resource-type="'org'" :target-id="detailData?.id ?? null" />
```

`resourceType` 字符串常量与后端 `OperationLogResourceType` 保持一致（`org`/`user`/`position`/`app`/`role`/`permission`/`admin`/`menu`），`targetId` 直接取该模块详情数据自身的 `id`（详情弹窗展示的正是这一条记录，`id` 就是 `target_id`）。用户模块的任职记录本身走独立的"任职管理"模块（`position-management`），有自己的详情弹窗，不在用户详情弹窗里重复嵌入任职记录的操作历史。

### 5. 字典管理：新增详情弹窗

字典类型、字典项此前都没有详情弹窗，本次各自新增一个（复用已存在的 `dictApi.getDictTypeById`/`getDictItemById`），交互与其余 8 个模块保持一致（只读 `el-descriptions` + 底部嵌入 `OperationHistoryPanel`，`resourceType` 分别为 `dictType`/`dictItem`）。左侧字典类型列表、右侧字典项表格的操作列各自新增一个"详情"按钮（位置参照其余模块——启用/停用/删除按钮之前）。

### 6. 权限资源编码与菜单种子数据

新增 `DictManagement:dictType:detail`、`DictManagement:dictItem:detail` 两条编码到 `权限资源.txt`；新增 Flyway 迁移 `V16__seed_dict_detail_menu_resource_data.sql`，挂在 `tab_menu` 里已存在的字典管理页面节点（`DictManagement:dictType:view`）下，`showOrder` 参照其余模块"详情"按钮的相对位置（新增之后、编辑之前）：字典类型现有 `add=100`/`edit=90`，插入 `detail=95`；字典项现有 `add=50`/`edit=40`，插入 `detail=45`。

## Risks / Trade-offs

- [Risk] `targetId` 参数在缺少 `resourceType` 时会跨资源类型精确匹配（不同资源表的自增 id 会重叠）→ Mitigation：属于筛选参数的正常自由组合语义（与其他独立筛选参数一致），前端固定同时传两个参数，不存在实际误用路径；后端不强制校验是为了保持这个通用查询接口的筛选参数彼此独立、互不依赖的既有设计。
- [Risk] 8 个详情弹窗改动点分散、容易遗漏 → Mitigation：全部改动都是同一种模式（引入 `OperationHistoryPanel` + 传 `resourceType`/`targetId` 两个字面量），改动小而机械，`tasks.md` 按模块逐一列出。
- [Risk] 字典管理新增详情弹窗触及的是此前完全空白的功能点（不是"改造已有功能"），需要额外确认按钮位置、权限编码、菜单种子数据三处都补齐，遗漏风险比其余 8 个模块更高 → Mitigation：`tasks.md` 单独成组、并在末尾验证阶段专门核对 `权限资源.txt`/迁移/前端按钮三者一致。

## Open Questions

无。
