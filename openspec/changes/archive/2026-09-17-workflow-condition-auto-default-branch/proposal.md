## Why

流程设计器目前强制要求业务管理员为每个"条件"节点手工画一条不带条件的兜底出边，否则拒绝发布。这个人工步骤容易被遗漏、增加设计门槛，且"兜底分支该连到哪里"完全由使用者自行决定，缺乏统一语义。实际上 Flowable 引擎已有现成能力：流程走完且从未有审批任务写入 `approved=false`（即从未被人工驳回）时，`FlowableWorkflowService.finalizeInstanceIfEnded()` 默认判定该流程实例为已通过。可以直接借助这一既有语义，把"默认兜底分支"改为系统自动生成、自动绕过后续审批直达结束、从而自动通过，省掉人工画默认边的步骤，同时让"不符合任何已配置条件 = 自动通过"成为确定性的系统行为，而不是由使用者随意决定默认分支去向。

## What Changes

- 前端流程设计器不再要求使用者为条件节点手动添加/勾选"默认兜底分支"出边，去掉相关校验报错与"作为默认兜底分支"勾选框、提示文案。
- 后端发布流程模型时，在结构校验前为所有缺少无条件出边的条件节点合成一个共享的 END 节点，并为每个条件节点添加一条指向它的无条件兜底出边，编译为 Flowable 排他网关的 default flow。
- 发布快照保存编译期补全后的 DSL，使流程实例详情图与实际部署结构一致；可编辑的原始草稿保持不变。
- 该自动兜底分支绕过所有审批节点直接进入结束事件，依据 Flowable 引擎既有的"流程结束时未写入 `approved=false` 即视为已通过"语义，实现"不符合任何已配置条件的申请自动通过审批"。
- **BREAKING（校验行为收紧方向不变，语义变化）**：条件节点即使配置了多条带条件的出边而未手动配置默认分支，也不再报错拒绝发布——系统会自动补全，因此"条件节点缺少默认分支"这条发布校验错误不再存在。
- 范围仅覆盖当前唯一生效的 v1 DSL/校验器/编译器（`workflow/designer` 包）；v2（`workflow/dslv2` 包）当前没有任何前端入口可达，本次不改动。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `workflow-process-designer`：
  - Requirement "条件节点分支必须有兜底"：从"要求使用者手动配置至少一条无条件出边，否则拒绝发布"改为"系统自动补全默认兜底分支，使用者无需手动配置；该分支自动绕过审批直达结束，效果为自动通过"。
  - Requirement "发布前结构与业务规则的强制校验"：移除"条件节点存在兜底默认分支"作为拒绝发布的校验项（因为系统保证该条件在发布时必然满足，不再需要作为一条会导致拒绝的校验规则出现）。

## Impact

- 前端：`frontend/src/utils/workflowValidation.ts`、`frontend/src/views/workflow/designer/panels/NodePropertyPanel.vue`、`frontend/src/views/workflow/designer/ProcessDesignerView.vue`、`frontend/src/views/workflow/designer/nodes/ConditionNode.vue`（仅注释）。
- 后端：`backend/src/main/java/cn/nihility/rbac/workflow/designer/compiler/ProcessModelDslValidator.java`、`backend/src/main/java/cn/nihility/rbac/workflow/designer/compiler/WorkflowModelCompilerImpl.java`、`backend/src/main/java/cn/nihility/rbac/workflow/designer/service/impl/WorkflowProcessModelServiceImpl.java` 的 `publishV1()`，以及 `ConditionNodeDsl.java` 的条件节点注释。
- 不影响：`workflow/dslv2` 包（当前不可达，不改动）、Flowable 终态判定逻辑 `FlowableWorkflowService.finalizeInstanceIfEnded()`（复用既有语义，不修改）。
- 涉及已发布/运行中的流程实例：仅影响之后新发布的流程模型版本，不回溯改写已部署的 Flowable 流程定义。
