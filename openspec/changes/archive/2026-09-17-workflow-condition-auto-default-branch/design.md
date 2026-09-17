## Context

流程设计器（v1 DSL，当前唯一生效路径；`workflow/dslv2` 包没有任何前端入口可达，本设计不涉及）改动前的发布链路：

1. 画布保存草稿 → `tab_wf_process_model.model_json`（原始 Workflow JSON DSL 字符串，未来仍可编辑）。
2. 发布 → `WorkflowProcessModelServiceImpl.publishV1()`：把 `model_json` 反序列化为 `ProcessModelDsl dsl`，调用 `WorkflowModelCompilerImpl.compile(dsl)`。`compile()` 内部先跑 `ProcessModelDslValidator.validate(dsl)`（结构+业务规则校验，其中就包含"条件节点缺少默认分支"这条报错），再用 Flowable `org.flowable.bpmn.model.*` API 把 DSL 节点/边逐个翻译成 BPMN（`ConditionNodeDsl` → `ExclusiveGateway`，不带 `condition` 的边 → `sourceGateway.setDefaultFlow(...)`），最后过 Flowable 自带的 `ProcessValidator` 二次校验、部署。
3. 发布产物落库到 `tab_wf_process_definition`：`modelJsonSnapshot` 字段目前直接存 `model.getModelJson()`（也就是**原始未编译**的草稿字符串，与传给 `compile()` 的 `dsl` 是分开反序列化出来的两份数据，`compile()` 对 `dsl` 的任何原地修改都不会体现在 `modelJsonSnapshot` 里）。
4. `modelJsonSnapshot` 之后被 `ProcessGraphAssembler.assembleV1()` 等下游消费方重新解析为 `ProcessModelDsl`，用于流程实例详情页的可视化流程图（节点/连线 + 当前节点高亮，`ApprovalRequestDetailDialog.vue` 里的 `ProcessFlowChart.vue`）。

审批结果终态判定见 `FlowableWorkflowService.finalizeInstanceIfEnded()`：流程实例的所有 Flowable 执行都结束后，读取历史流程变量 `approved`；该变量只在某个 `UserTask` 被人工完成时才会被写入；变量不存在时 `approved` 默认判定为 `true`（已通过）。也就是说，只要一条路径完全不经过任何 `UserTask` 就走到 `EndEvent`，天然等价于"自动通过"，无需新增任何"自动通过"专用逻辑。

## Goals / Non-Goals

**Goals:**
- 条件节点不再要求使用者手动画一条无条件出边；发布时系统自动为缺少默认分支的条件节点补一条绕过全部审批节点、直达结束事件的兜底默认分支。
- 自动补的默认分支复用 Flowable 既有的"未被人工驳回即视为已通过"语义，实现"不符合任何已配置条件 ⇒ 自动通过审批"。
- 自动补的节点/边必须同步体现在发布后持久化的 `modelJsonSnapshot` 里，保证流程实例详情页的可视化流程图、当前节点定位等下游能力与真实部署的 Flowable BPMN 结构一致，不出现"引擎里存在的节点，快照图里找不到"的不一致。
- 使用者若已手动画了无条件兜底边（无论指向哪里），系统尊重使用者配置，不重复生成。

**Non-Goals:**
- 不改变草稿 `model_json`（画布可编辑内容）本身——自动补的节点/边只出现在发布产物 `modelJsonSnapshot` 里，用户重新打开设计器编辑草稿时看到的仍然是自己画的原始图，不会看到系统自动加的节点。
- 不新增"自动通过"专用标志位/变量/节点类型；完全复用既有的 `approved` 变量默认值语义。
- 不改动 `workflow/dslv2` 包（v2 DSL/校验器/编译器）——当前没有任何前端入口产出 v2 JSON，属于死代码路径，本次不处理；后续若要迁移到 v2 设计器需要另开 change 同步这个能力。
- 不改动已发布、正在运行中的流程实例——只影响之后新发布的版本。

## Decisions

### Decision 1：自动补全时机放在 `WorkflowModelCompilerImpl.compile()` 内、`processModelDslValidator.validate(dsl)` 之前

`compile()` 收到的 `dsl` 是可变对象（Lombok `@Getter/@Setter`，`nodes`/`edges` 均为 `List`），且是通过引用传入的——在 `compile()` 内部对 `dsl.getNodes()`/`dsl.getEdges()` 做的任何 `add()` 原地修改，调用方 `publishV1()` 持有的同一个 `dsl` 引用之后也能看到。因此不需要改变 `WorkflowModelCompiler` 接口签名、不需要新增返回值——`compile()` 内部完成自动补全后，`publishV1()` 里紧接着序列化 `dsl`（而不是原来的 `model.getModelJson()`）作为 `modelJsonSnapshot` 即可自然拿到补全后的结构。

补全必须在 `processModelDslValidator.validate(dsl)` 之前执行，原因：
- 校验器本身会被同步修改为不再要求默认分支（否则改了这条也没用，草稿仍然会被拒绝发布）。
- 但 Flowable 自带的 `ProcessValidator`（`compile()` 末尾，170-176 行）仍然会校验"排他网关必须有出边/默认流转"这类结构完整性规则；如果不在生成 BPMN 之前补全，排他网关缺 default flow 会在这道二次校验被拒绝，前功尽弃。

具体新增一个私有方法（如 `augmentWithAutoDefaultBranches(ProcessModelDsl dsl)`），在 `compile()` 方法体第一行（校验之前）调用：
1. 按 `dsl.getEdges()` 建 `Map<String, List<EdgeDsl>> outgoingByNode`（沿用与前端 `workflowValidation.ts`/`ProcessModelDslValidator` 一致的分组方式）。
2. 遍历 `dsl.getNodes()` 中 `ConditionNodeDsl` 类型的节点，若其出边中不存在 `edge.getCondition() == null` 的边，则需要补全。
3. 惰性创建一个共享的自动结束节点（见 Decision 2），为每个需要补全的条件节点各加一条 `EdgeDsl(from=节点id, to=自动结束节点id, condition=null)`。
4. 确实需要补全时，将 `dsl.getNodes()`/`dsl.getEdges()` 复制为新的 `ArrayList`，追加节点/边后回填，兼容不可变输入列表；没有待补全节点时直接返回。再次编译已补全的 DSL 时能够识别已有无条件边，不会重复生成。

### Decision 2：多个缺失默认分支的条件节点共享同一个自动生成的结束节点

不给每个需要补全的条件节点各造一个新 `EndNodeDsl`，而是整个模型只惰性创建一个共享的自动结束节点（例如 id 固定为 `__auto_approved_end__`，若与画布已有节点 id 冲突则退化为加数字后缀直到不冲突），所有需要补全的条件节点各出一条边指向它。

理由：BPMN 允许多条 `SequenceFlow` 汇入同一个 `EndEvent`，语义上没有问题；避免节点数量随条件节点数量线性膨胀，也让流程图里"自动通过的兜底出口"是唯一可辨识的一个节点，而不是散落多个。

### Decision 3：`modelJsonSnapshot` 持久化改为序列化编译期已补全的 `dsl`，而不是原始 `model.getModelJson()`

`WorkflowProcessModelServiceImpl.publishV1()` 里 `.modelJsonSnapshot(model.getModelJson())` 改为 `.modelJsonSnapshot(JacksonUtils.toJson(dsl))`（`dsl` 是调用 `workflowModelCompiler.compile(dsl)` 之后、已经被原地补全过默认分支的同一个对象）。

理由：`ProcessGraphAssembler.assembleV1()` 直接把 `modelJsonSnapshot` 反序列化为 `ProcessModelDsl` 渲染流程实例详情页的可视化流程图（含"当前节点"高亮）。如果快照仍是原始草稿（没有自动结束节点），当一次审批申请真的走到自动默认分支、Flowable 引擎里的"当前节点"变成那个自动生成的 `EndEvent` id 时，快照图里找不到这个节点，会出现"引擎状态与可视化流程图对不上"的不一致（轻则流程图渲染不完整，重则前端按 id 查找节点时报错）。让快照和真实部署的 BPMN 结构保持 1:1，是避免这类下游不一致最简单可靠的方式。

草稿 `model_json` 完全不受影响——用户重新打开设计器编辑草稿时，看到的仍然是自己保存的原始内容,不会看到自动生成的节点，这一点在下次发布时会重新按当前草稿计算，不会累积残留。

### Decision 4：前端只做"去掉强制要求"，不新增复杂 UI，但保留一句轻量提示

`workflowValidation.ts`/`NodePropertyPanel.vue`/`ProcessDesignerView.vue` 里"缺少默认分支"的报错与"作为默认兜底分支"勾选框直接删除。为避免用户看不到任何报错就困惑"我没配置默认分支，发布会不会出问题"，在 `NodePropertyPanel.vue` 原警告框的位置替换为一句非阻塞的说明文案（灰色提示，非 `el-alert type=warning`），大意为："未手动配置默认分支时，系统会在发布时自动为该条件节点补一条兜底分支：不满足任何已配置条件的申请将自动通过审批。如需自定义默认分支去向（例如转交人工审批），可手动添加一条不设置条件的出边。"

移除勾选框后，每条出边始终显示可清空的字段选择器，包括 `condition == null` 的新建边。选择字段时创建条件对象，清空选择时将 `condition` 恢复为 `null`，保留手动设置默认分支去向的入口；仅在条件对象存在时显示比较符和比较值。无条件边显示“默认分支”文字标记，字段占位提示为“不选字段时为默认分支”。切换字段继续清空比较值，并在原比较符不适用时恢复 `EQ`；只读模式禁用编辑控件、隐藏分支增删操作。

### Decision 5：v2（`workflow/dslv2`）保持现状，不做类比修改

调研确认：`WorkflowProcessModelServiceImpl.publish()` 按 `model.getModelJson()` 里的 `schemaVersion` 字段分发到 `publishV1()`/`publishV2()`；当前设计器前端产出的 JSON 从不携带 `schemaVersion`，`publishV2()` 路径不可达。为避免在无法被验证/测试覆盖到的死代码路径上引入不一致的改动，本次不改 `ProcessModelDslV2Validator`/`WorkflowModelCompilerV2`；如果未来上线 v2 设计器前端，需要另开 change 同步这个行为。

## Risks / Trade-offs

- **[风险] 自动生成的结束节点 id 与用户手绘节点 id 冲突** → 生成前检查 `nodeById` 是否已包含候选 id，冲突则追加数字后缀直到唯一；覆盖测试用例验证冲突场景。
- **[风险] `modelJsonSnapshot` 从"发布时的草稿原样快照"变为"发布时经过编译期自动补全的快照"，语义上是一次微小但明确的变化** → 已有依赖 `modelJsonSnapshot` 内容做逐字节比对的测试/逻辑需要重新审视（搜索 `modelJsonSnapshot` 的其余用途：`ProcessGraphAssembler`、流程模型版本历史查看等），确认它们都是按结构语义解析而非要求与草稿完全一致。
- **[风险] 已有集成测试断言"条件节点缺少默认分支时发布应报错"** → 需要定位并更新这些测试（预计在 `WorkflowModelCompilerImplTest`、`ProcessModelDslValidatorTest` 等），改为断言"自动补全后可以正常发布，且生成的 BPMN/快照里出现自动结束节点"。
- **[权衡] 共享一个自动结束节点而不是每个条件节点各自一个** → 换来更小的图规模，代价是多个条件节点的"自动通过"路径在可视化流程图上会看起来汇聚到同一个终点；可接受，本身语义上也确实都是"同一件事：自动通过"。

## Migration Plan

- 无数据库结构变更，无需 Flyway 迁移脚本。
- 纯代码行为变更，随正常发布上线；只影响*之后新发布*的流程模型版本，不回溯改写已部署的 Flowable 流程定义或已产生的 `tab_wf_process_definition` 历史记录。
- 无需灰度开关：新旧行为的唯一区别是"是否需要用户手动配置默认分支"，向后兼容（用户已手动配置的模型行为不变，见 Decision "使用者手动配置的默认分支优先生效"）。

## Open Questions

（无——调研已确认关键技术路径，可直接进入 tasks 拆分）
