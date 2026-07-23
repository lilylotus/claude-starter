## Context

`add-excel-import-export` 已归档（`openspec/changes/archive/2026-07-23-add-excel-import-export/`），
其能力已同步进 `openspec/specs/excel-import-export/spec.md`。本次是该能力上线后用户反馈的
三个跟进问题，不改变已归档 change 的历史记录，而是作为新的独立 change 处理，涉及三个互相
独立的关注点：前端树刷新、批量导入行处理顺序、操作日志来源标记。

## Goals / Non-Goals

**Goals:**
- 组织批量导入完成后，用户在不离开当前页面/不切换菜单的前提下，能看到任意层级、任意父
  组织下新导入的组织。
- 组织批量导入不再对 Excel 内"上级组织编码"列与"组织编码"列的行序敏感——只要一份文件内
  同时包含某组织与其上级组织的数据行，不论两行谁在前，都应该都能被正确导入。
- 操作历史/操作日志能一眼区分某条新增/编辑记录是界面手动操作还是 Excel 批量导入产生的。

**Non-Goals:**
- 不扩展"父子依赖拓扑排序"到 USER/POSITION/APP——这三类业务对象在当前数据模型下没有
  "同批次内一行依赖另一行"的场景（POSITION/APP 依赖的用户、组织通常是已存在数据，不是
  同一份任职/应用导入文件里的其他行）。
- 不为操作日志新增"按来源筛选"的查询入口——本次只解决"展示时能区分"，不做筛选器改动，
  避免超出用户实际提出的范围。
- 不改变批量导入"逐行独立事务提交、单行失败不影响其余行"的既有事务边界（design.md 决策 3，
  见归档的 `add-excel-import-export`），拓扑排序只改变处理**顺序**，不改变每行仍是独立
  `REQUIRES_NEW` 事务这一点。

## Decisions

### 1. 组织批量导入完成后，左侧导航树改为整体重置而非局部分支刷新

现状：`OrgManagementView.vue` 的 `handleImported()` 复用了单条记录新增/编辑/删除后的刷新
逻辑（`orgStore.refreshAfterMutation()` 刷新右侧表格当前 `currentParentId` 分支 +
`refreshNavTreeAfterMutation()` 只刷新左侧懒加载树中 `currentParentId` 这一个分支的直接
子节点）。这个刷新范围对"单条记录变更"是合理的（新增/编辑操作发生的父级就是当前浏览的
`currentParentId`），但批量导入一次可能在文件里任意多个不同的、甚至当前树上还没展开过的
父组织下新增/更新组织，局部刷新覆盖不到这些分支；已经展开过的分支即使导航过去，`el-tree`
懒加载模式下也不会重新请求该节点的子节点（内部有缓存），所以用户目前只能通过"切换菜单"
（整个组件重新挂载，抛弃所有缓存）来看到新数据。

决策：批量导入（仅批量导入这一入口，单条记录的新增/编辑/删除/启停用维持现有的局部刷新，
不改动）完成后：
- 右侧表格：维持现状，仍调用 `orgStore.refreshAfterMutation()` 刷新当前 `currentParentId`
  分支——用户当前正在看的这一页数据本身就应该刷新。
- 左侧导航树：不再调用 `refreshNavTreeAfterMutation()` 做局部分支刷新，改为整体重置——
  给 `el-tree` 绑定的 `:data`/懒加载状态提供一个会在每次批量导入完成后自增的版本号
  （`orgStore` 新增 `navTreeVersion` ref，`handleImported` 里 `orgStore.navTreeVersion++`），
  组件模板里给 `el-tree` 加 `:key="orgStore.navTreeVersion"`——Vue 在 `key` 变化时会销毁旧
  组件实例、创建新实例，`el-tree` 因此丢弃全部懒加载缓存、collapse 回初始状态，重新从根节点
  开始懒加载。代价是批量导入后左侧树里任何已展开的节点会被收起，需要用户重新展开——这是
  可接受的折衷（比"必须切换菜单才能看到新数据"好），且只在批量导入这一相对低频的操作后
  发生，不影响单条记录操作的既有局部刷新体验。

被拒绝的替代方案：让后端批量导入接口返回"本次实际涉及的父组织 id 集合"，前端据此对每个
受影响分支分别调用 `refreshNavTreeBranch`。这样能保留其余分支的展开状态，体验更好，但
实现复杂度高（后端要额外统计并返回这个集合，前端要处理"某个受影响父节点在树上尚未被展开
过"时如何处理），与问题的实际严重度不成比例，本次不采用。

### 2. 组织批量导入：先完整解析 Excel，再按"上级组织编码"列的文件内依赖关系排序处理

现状：`BatchImportServiceImpl.processDataRows` 按 Excel 文件的物理行顺序逐行调用
`ImportRowExecutor.processRow`。ORG 场景下，"上级组织编码"列（`__parentCode`）非
`"0"` 时会去查库匹配 `tab_org.code`——如果这份 Excel 里子组织的行排在其上级组织的行
前面，子组织行处理时上级组织还没被创建，查库匹配不到，该行被判定失败，即使上级组织的
数据其实就在后面几行。

决策：`BatchImportServiceImpl` 改为两阶段：
1. **解析阶段**：跟现在一样读取全部非空白数据行，但不立即调用 `processRow`，而是先把
   每一行都解析成 `fieldCode -> 单元格文本` 的 `Map`，连同其 Excel 行号一起收集成一个
   列表（沿用现有的单元格读取逻辑，只是把"解析"和"执行"这两步从同一个循环体里拆开）。
2. **排序阶段（仅 `bizType=ORG` 触发）**：在这个批次内部，以每行的 `code` 值为节点、
   以"某行的 `__parentCode` 等于批次内另一行的 `code`"为有向边（父 → 子），做拓扑排序
   （Kahn 算法）：
   - `__parentCode` 为字面量 `"0"`，或其值在当前批次所有行的 `code` 集合里找不到匹配
     （意味着引用的是数据库里已存在的组织，或者是一个本次会在 `processOrg` 里查库时
     判定失败的无效编码），都不构成批次内部依赖，这类行没有"必须等谁先处理"的约束。
   - 若干行的 `__parentCode` 相互指向、形成环（如 A 的上级是 B、B 的上级是 A），这些行
     在拓扑排序里会有无法消解的入度，直接判定为失败，失败原因为"上级组织编码与文件内
     其他行形成循环引用，无法确定导入顺序"，不再进入 `processOrg` 走查库匹配（避免给出
     "无法匹配到已有组织记录"这种具有误导性的原因）。
   - 批次内 `code` 出现重复（多行 `code` 相同）不是本次要解决的问题，维持现状——按原始
     文件顺序参与排序与处理，多行同 `code` 时后面的行会在 `processOrg` 里对前面刚创建的
     记录做更新，行为与排序前一致，只是相对顺序现在也要满足父子依赖约束（如果同名的多行
     之间也存在父子引用关系，实现时按拓扑排序自然结果处理，不需要额外特判）。
   - 排序结果：把参与拓扑排序、未被判定为循环失败的行，按"父在前、子在后"的顺序重新排列；
     被判定为循环失败的行直接加入 `failList`，不进入下一步。
3. **执行阶段**：按第 2 步得到的顺序（非 ORG 场景就是原始文件顺序）依次调用
   `ImportRowExecutor.processRow`，其余行为（逐行独立事务、单行失败不影响其余行、成功
   条数与失败明细汇总）不变。

拓扑排序与循环检测的实现放在 `BatchImportServiceImpl` 内部新增的私有方法里（如
`sortOrgRowsByParentDependency`），不下沉到 `ImportRowExecutor`——`ImportRowExecutor`
只负责单行处理，"看得到整个批次"是 `BatchImportServiceImpl` 的天然职责，这个划分与已有的
"分层职责"保持一致。

被拒绝的替代方案一：多轮重试（每一轮跑一遍所有尚未成功的行，只要本轮至少有一行成功就继续
下一轮，直到没有行再取得进展）。这个方案不需要显式建图，实现更简单，但时间复杂度是拓扑
排序的平方级（最坏情况下每轮只成功一行），且循环引用的场景下无法明确判断"是循环导致的
死锁"还是"单纯是这些行本来就有别的失败原因"，报错信息不如显式拓扑排序精确，本次不采用。

被拒绝的替代方案二：要求管理员必须手动整理 Excel 的行顺序（父组织必须出现在子组织之前），
文档说明即可，不改代码。这正是用户当前遇到的痛点本身，不是"修复"，予以拒绝。

### 3. 操作日志新增"操作来源"字段，通过线程级标记区分 Excel 导入 vs 界面操作

现状：批量导入引擎复用组织/人员/任职/应用四个模块既有的 `create`/`update` service 方法
（`add-excel-import-export` design.md 决策 4），这些方法内部按各自既有逻辑调用
`OperationLogRecorder.recordCreate`/`recordUpdate` 写操作日志，不知道调用方是页面手动
操作还是批量导入——这也是"决策 4 不改这四个模块 service 方法签名"这一约束的直接后果。

决策：
- `tab_operation_log` 新增 `operate_source` 列（`INT NOT NULL DEFAULT 0`，0=界面操作，
  1=Excel 导入），新增常量类 `cn.nihility.rbac.operationlog.constant.OperationSource`
  （`MANUAL=0`/`IMPORT=1`，附 `label()` 方法，写法比照已有的 `OperationType`）。
- 新增线程级标记工具类 `cn.nihility.rbac.operationlog.context.OperationSourceContext`
  （`ThreadLocal<Integer>`，`mark(int)`/`currentOrDefault()`/`clear()`）。放在
  `operationlog` 模块下（而不是 `excelimport` 模块），因为"操作来源"是操作日志领域自身
  的概念，`excelimport` 依赖 `operationlog` 是正常的单向依赖，不产生反向依赖。
- `ImportRowExecutor.processRow` 在方法体最外层用 `try { OperationSourceContext.mark(
  OperationSource.IMPORT); ...原有 switch 分发... } finally { OperationSourceContext
  .clear(); }` 包裹——不区分 ORG/USER/POSITION/APP，四类导入统一标记；`finally` 保证
  即使处理失败（异常向上抛出触发 `REQUIRES_NEW` 事务回滚）也一定会清除标记，避免同一
  线程后续处理下一行、或线程池复用处理其他请求时误继承标记。
- `OperationLogRecorderImpl.record(...)` 写入 `OperationLogEntity` 时，新增一行
  `.operateSource(OperationSourceContext.currentOrDefault())`；未被标记时
  `currentOrDefault()` 返回 `OperationSource.MANUAL`，即所有既有的页面手动操作路径无需
  任何改动就自动落库为"界面操作"，不需要在这十类资源的 `ServiceImpl` 里挨个显式传参
  （这也是选择 `ThreadLocal` 而非"给 `OperationLogRecorder` 接口方法加一个 `source`
  参数"的原因——后者要改遍所有调用点，且仍然绕不开"批量导入调用的是同一批既有 service
  方法、这些方法内部才是真正调用 `OperationLogRecorder` 的地方"这个结构性约束）。
- 展示层：`OperationLogVO`/`OperationLogDetailVO` 新增 `operateSource`/
  `operateSourceLabel` 字段（`operateSourceLabel` 由 `OperationLogQueryServiceImpl`
  仿照现有 `operationTypeLabel` 的写法在查询后填充，不进 MapStruct 转换器）；
  `OperationHistoryPanel.vue`（详情页内嵌的"操作历史"时间线，本次问题里用户所说的
  "历史记录"就是这个组件——它只展示时间、操作类型标签、操作人、字段变更，本身不展示
  `targetName`，因此不能靠"在 `targetName` 前面加文字前缀"这种改法让用户看到区别，
  必须是这里新增的独立字段驱动的标签）与 `OperationLogManagementView.vue`（独立的
  "操作日志管理"列表页）在既有的操作类型标签（新增/编辑/…）旁追加一个次要样式的标签，
  仅当 `operateSource === OPERATION_SOURCE_IMPORT` 时显示文案"Excel 导入"，界面操作
  （多数场景）不展示任何额外标签，维持现状观感。

被拒绝的替代方案：在 `targetName` 前面拼接 `"[Excel导入] "` 文本前缀，不新增字段/不改
表结构。这个方案实现量最小，但 `OperationHistoryPanel.vue`（用户实际描述的"历史记录"
展示位置）根本不渲染 `targetName`，这个前缀不会出现在用户实际看的地方，等于没有解决问题，
予以拒绝。

## Risks / Trade-offs

- **[取舍] 批量导入左侧树整体重置会收起所有已展开节点** —— 见决策 1，接受此代价，仅
  批量导入这一入口受影响。
- **[风险] 拓扑排序引入的额外一次全量内存排序，理论上随文件行数增长有性能开销** ——
  单次导入行数上限本身是 1000 行（`add-excel-import-export` 既有约束），拓扑排序的时间
  复杂度是 O(行数)，对 1000 行规模可忽略不计，不做额外优化。
- **[风险] `operate_source` 是新增的 `NOT NULL DEFAULT 0` 列，对历史已有的操作日志数据
  是安全的（迁移执行时历史行自动补 0，语义上等价于"这些都是界面操作"，事实上确实如此，
  因为 Excel 导入功能上线前不可能有导入来源的记录）** —— 无需额外的数据回填脚本。
