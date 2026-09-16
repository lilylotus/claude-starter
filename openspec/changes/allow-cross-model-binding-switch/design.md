## Context

`WorkflowProcessBindingService.switchDefinition`（`backend/src/main/java/cn/nihility/rbac/workflow/dslv2/binding/WorkflowProcessBindingService.java:108-138`）目前会校验目标流程定义的 `processModelId` 必须与当前绑定原来指向的定义的 `processModelId` 相同，否则抛 `BusinessException("切换目标流程定义必须与当前绑定属于同一流程模型")`。

但前端 `ProcessBindingView.vue` 的"切换绑定版本"弹窗里，"流程模型"下拉（`form.modelId`）在切换模式下并未被禁用，管理员可以自由选择任意模型、再从级联的"流程版本"下拉里选该模型下的已发布版本提交——UI 本身就允许跨模型切换，只是提交后必定被后端拒绝。同时"新建绑定"接口对同一绑定维度重复创建直接拒绝（不看 `enabled` 状态），也没有任何删除绑定的接口（`approval-process-binding-console` change design.md 明确决策"不做绑定删除功能——遵循既有后端能力边界"）。三者叠加导致一个绑定维度一旦建立，事实上无法再指向别的流程模型，这是一个功能缺口而不是设计预期行为（用户在业务绑定管理页面里实际操作时撞上了这个限制）。

## Goals / Non-Goals

**Goals:**
- 去掉 `switchDefinition` 对"目标定义必须与当前绑定同一流程模型"的强制校验，允许切换到任意已发布流程模型的已发布版本。
- 调整"是否为回滚"的判定，使其只在同一流程模型内的版本号比较才有意义，跨模型切换不误判为"回滚"或"升级"。

**Non-Goals:**
- 不新增删除绑定接口（维持 `approval-process-binding-console` change 的既有决策），本次通过"允许切换到任意模型"这条路径解决"如何把绑定换到另一个模型"的问题，不需要"先删后建"这条路径。
- 不改前端 `ProcessBindingView.vue`——它已经允许自由选择模型/版本，本次只是让后端不再拒绝。
- 不改绑定维度（`bizType`/`operationType`/`scopeType`/`scopeId`）本身的唯一性约束，`createBinding` 对已存在维度的拒绝逻辑不变。

## Decisions

### Decision 1：去掉同模型校验，只保留"目标必须已发布"校验
删除 `switchDefinition` 里的
```java
if (!definition.getProcessModelId().equals(previousDefinition.getProcessModelId())) {
    throw new BusinessException("切换目标流程定义必须与当前绑定属于同一流程模型");
}
```
这一段。`requirePublishedDefinition(request.getDefinitionId())` 对目标定义"必须是 PUBLISHED 状态"的校验保留不变——跨模型切换仍然只能切到一个已发布版本，不能切到草稿/下线状态的定义。

### Decision 2："回滚"判定收窄到同模型内比较，跨模型切换单独记日志
现有 `isRollback` 判定：
```java
boolean isRollback = previousDefinition.getVersion() != null && definition.getVersion() != null
        && definition.getVersion() < previousDefinition.getVersion();
```
版本号（`version`）是"同一流程模型内"的递增序号，不同模型各自从 1 开始计数，跨模型比较版本号大小没有意义（模型 B 的 v1 并不比模型 A 的 v3"更旧"）。调整为：
```java
boolean sameModel = definition.getProcessModelId().equals(previousDefinition.getProcessModelId());
boolean isRollback = sameModel && previousDefinition.getVersion() != null && definition.getVersion() != null
        && definition.getVersion() < previousDefinition.getVersion();
```
日志文案里，跨模型切换（`!sameModel`）单独打一种场景标签（如"切换到其它流程模型"），不再套用"显式回滚到历史版本"/"切换到更新版本"这两种同模型场景下的措辞，避免运维排查时把跨模型切换误读成同模型的版本回退。

### Decision 3：不需要新增字段或接口
`ProcessBindingEntity`/`ProcessBindingVO`/`ProcessBindingRequest` 均不需要改动——绑定表本来就只存 `definitionId`，不存 `processModelId`（模型信息通过 `definitionId` 关联 `tab_wf_process_definition` 间接得到，前端 `definitionMap` 已经是这么 join 展示的），去掉的只是一条业务规则校验，不涉及数据结构变化。

## Risks / Trade-offs

- [跨模型切换后，旧模型下的运行中实例如何处理] → 与现状完全一致：`switchDefinition` 从来只影响"新发起时用哪个定义"，不影响已经在跑的流程实例（实例已经冻结了自己发起时的 `definitionId`），本次不改变这个既有语义，不引入新风险。
- [管理员误操作把绑定切到完全不相关的流程模型] → 前端 UI 早就允许这么选（本次之前后端会拦一道，之后不拦），风险接受：这本来就是管理员在"业务绑定"管理页面里的一次显式操作，与"误删"这类不可逆操作不同类型，选错了再切回来即可，且每次绑定变更都会写审计字段。
