## Context

`workflow-approval-engine` change 交付了一个通用的、按 `processCode` 驱动的工作流引擎（`WorkflowService`/流程设计器），可以为任意业务发布任意多套流程模型。但四类主数据业务（ORG/USER/POSITION/APP）提交审批申请时，`ApprovalProcessServiceImpl.start()` 把 `processCode` 写死为常量 `WorkflowConstants.MASTER_DATA_APPROVAL_PROCESS_CODE`，完全没有按 `bizType` 区分，导致"分业务类型设计并执行审批流程"这个诉求在系统里无法落地。

现状相关代码：
- `ApprovalSwitchEntity`/`tab_approval_switch` 只有 `bizType`（唯一）+ `enabled`（布尔）两个业务字段。
- `ApprovalSwitchServiceImpl.update(bizType, enabled)` 只做布尔切换，不涉及流程选择。
- `ApprovalProcessServiceImpl.start(requestId, bizType, applicantId, applicantOrgId)` 忽略 `bizType`，恒定传 `PROCESS_CODE` 常量给 `WorkflowService.start()`。
- `WorkflowService.start()`（`FlowableWorkflowService`）按 `processCode` 查 `tab_wf_process_model`，要求 `status=PUBLISHED` 且 `current_definition_id` 非空，否则拒绝（`BusinessException`）——这个校验已经存在，本次改动复用它，不需要重新实现。
- 流程模型列表接口 `GET /api/workflow/process-models` 已存在（`WorkflowProcessModelController.listModels()`），返回全部流程模型（含 `status` 字段），无服务端状态过滤参数。

## Goals / Non-Goals

**Goals:**
- 让每个 `bizType` 能独立绑定一个已发布的流程模型（`processCode`），并让运行时提交审批真正按此绑定路由。
- 绑定关系的维护走既有"审批设置"页面/接口，不新增独立的管理入口。
- 迁移后不改变现有环境的实际行为（四类业务此前唯一可用的流程就是预置的 `MASTER_DATA_APPROVAL`，迁移后应继续绑定它）。

**Non-Goals:**
- 不改动 `WorkflowService`/流程设计器/Flowable 引擎本身，它们已经是通用的。
- 不支持一个 `bizType` 同时绑定多个流程模型（如按操作类型 CREATE/UPDATE 分流到不同流程）——一个 `bizType` 一个绑定，与现有"审批开关"粒度一致。
- 不做"流程模型列表接口的服务端按 status 过滤"——现有 `listModels()` 返回全量列表即可，前端按 `status==='PUBLISHED'` 客户端过滤，避免为一个下拉框新增查询参数/接口版本。
- 不处理"绑定的流程模型草稿变更后如何提示"这类设计器侧的通知机制，超出本次范围。

## Decisions

### Decision 1：`tab_approval_switch` 新增可空的 `process_code` 列，而不是新建独立的关联表
四类业务与流程模型是简单的一对一绑定，`tab_approval_switch` 本身已经是"按 `bizType` 唯一"的表，直接加一列语义清晰、查询/更新都不需要 JOIN。新建关联表（`tab_approval_switch_process`）除了多一次 JOIN 没有额外价值，故不采用。列设计为**可空**：允许"开关关闭且尚未绑定任何流程"这一初始状态存在（迁移前的存量环境除外，见 Decision 4）。

### Decision 2：启用开关（或在已启用状态下更换绑定）时，强制校验目标流程处于 PUBLISHED 状态；关闭开关时不做该校验
`ApprovalSwitchServiceImpl.update()` 的方法签名扩展为 `update(bizType, enabled, processCode)`。校验规则：
- 若目标结果是 `enabled=true`：`processCode` 必须非空，且必须能查到一条 `tab_wf_process_model`，其 `status=PUBLISHED`；不满足则拒绝本次更新（`BusinessException`），开关状态和绑定值均不变。
- 若目标结果是 `enabled=false`：不校验 `processCode` 的可用性（允许提前绑定一个尚在草稿阶段的流程，等发布后再开启），也允许 `processCode` 为空。

这样保证"开关一旦为开启状态，绑定的流程必然当时是可用的"，把校验前移到管理员操作时机，而不是等到某个用户提交申请时才发现流程不可用。

**备选方案**：只在 `submit()` 运行时校验，不在开关更新时校验——被否决，因为这会让管理员在毫无提示的情况下开启一个指向无效流程的开关，直到第一个用户提交申请失败才发现，体验更差、且错误发生在错误的时机（提交人而非配置人）。

### Decision 3：运行时（`ApprovalProcessServiceImpl.start()`）绑定流程被后置下线的兜底行为——拒绝提交，不静默回退到默认流程
即使 Decision 2 在开关更新时做了校验，仍存在时间差场景：开关开启、绑定流程 A 为 PUBLISHED 之后，管理员通过流程设计器把流程 A 下线（`WorkflowDesign:model:disable`，这是设计器侧已有的独立操作，不会级联检查审批开关的绑定关系）。此时 `ApprovalProcessServiceImpl.start()` 按 `bizType` 查到的 `processCode` 对应的流程模型已经不是 `PUBLISHED`。处理方式：**直接透传给 `WorkflowService.start()`，复用其已有的"未发布拒绝"校验**（`BusinessException("流程 xxx 未发布，无法发起")`），提交审批的用户会看到明确的失败提示；系统不做"自动切回默认流程"的静默兜底。

**理由**：静默切换到另一个流程（无论是默认流程还是历史流程）都意味着实际生效的审批链路脱离了管理员的显式配置，可能悄悄绕过管理员本想要求的审批级别/审批人，是比"提交失败、需要管理员重新绑定"更危险的行为；而这个操作本身理论上很少发生（下线一个正被审批开关引用的流程是管理员的主动操作，属于配置管理疏漏，应该让失败快速暴露而不是被掩盖）。这与"下线流程模型"需求里"下线后拒绝以该流程编码新发起"的既有语义（`workflow-approval-engine` design.md Decision 11 集成测试已覆盖）完全一致，本次不需要新增任何引擎侧代码，只是让四类业务的提交路径也会命中这条既有校验。

**备选方案考虑过**：
- 自动回退到预置的 `MASTER_DATA_APPROVAL`——否决，理由同上（静默改变审批链路）。
- 在 `submit()` 阶段主动检测并给出比引擎异常更友好的提示——本次不做，作为 Open Question 记录，避免范围蔓延；引擎抛出的 `BusinessException` 消息已经足够明确（"流程 xxx 未发布，无法发起"），前端按现有错误提示机制展示即可。

### Decision 4：数据库迁移时，为 ORG/USER/POSITION/APP 四条既有记录的 `process_code` 回填为预置的 `MASTER_DATA_APPROVAL`
保证迁移后行为与迁移前完全一致（四类业务此前唯一可用、也是唯一存在的流程就是它），不会因为本次改动导致存量环境的审批全部失效（如果回填为空，四个已经开启审批开关的业务类型会在下一次提交时因 Decision 2/3 的校验而全部失败）。

### Decision 5：`ApprovalProcessServiceImpl.start()` 通过注入 `ApprovalSwitchService` 新增的查询方法解析 `processCode`，而不是直接注入 `ApprovalSwitchMapper`
`ApprovalSwitchService` 已经是 `approval` 包内部的标准查询入口（`ApprovalSwitchServiceImpl.getExisting(bizType)` 已经封装了"不支持的业务类型"/"记录不存在"校验），新增一个 `resolveProcessCode(bizType)`（或直接扩展现有查询返回值携带 `processCode`）复用这层封装，避免 `ApprovalProcessServiceImpl` 跨过 Service 层直接摸 Mapper。`processCode` 为空（未绑定）或查到的记录 `enabled=false` 时同样通过 `BusinessException` 拒绝提交（正常情况下调用方在此之前已经用 `isEnabled(bizType)` 做过分流判断，走到 `start()` 说明开关必然是开启的，此处的空值判断是防御性的，理论上不会命中，因为 Decision 2 已在开关侧堵住"开启但未绑定有效流程"的状态）。

### Decision 6：前端流程模型下拉的数据源与过滤方式
沿用已有的 `GET /api/workflow/process-models`（`workflowApi.listProcessModels()`），前端按 `status === 'PUBLISHED'` 过滤后渲染为下拉选项（`processCode` 作为 value，`processName（processCode）` 作为 label）。若某业务类型当前绑定的 `processCode` 不在过滤后的列表里（如已被下线或从未发布过），下拉仍需能显示该值（作为一个禁用/告警态的选项），避免管理员看不到当前实际绑定了什么。

## Risks / Trade-offs

- **[Risk] 绑定流程后又被设计器下线，导致该业务类型审批被"卡住"（Decision 3 的直接后果）** → Mitigation：这是有意为之的 fail-closed 设计，前端"审批设置"页面在渲染时应主动提示"当前绑定流程已下线，请重新绑定"（依据 Decision 6 里下拉展示当前绑定值 + 其 `status` 字段判断），把发现时机从"用户提交失败"提前到"管理员打开审批设置页面"。
- **[Risk] `tab_approval_switch.process_code` 与 `tab_wf_process_model.process_code` 之间没有物理外键（项目约定不建物理外键，参考 `tab_admin.user_id` 等既有先例）** → Mitigation：由 Decision 2/3 的应用层校验保证一致性，与项目里其余跨表引用的既有做法一致，不引入新约定。
- **[Trade-off] 一个 bizType 只能绑定一个流程，不支持按操作类型细分** → 接受，超出本次范围（见 Non-Goals），后续如有需要可作为独立 change 扩展 `tab_approval_switch` 的绑定粒度。

## Migration Plan

1. 新增 `db/migration/V11__add_approval_switch_process_code.sql`：`ALTER TABLE tab_approval_switch ADD COLUMN process_code VARCHAR(64) NULL`，并 `UPDATE` 四条既有记录的 `process_code = 'MASTER_DATA_APPROVAL'`（复用已有的 `MASTER_DATA_APPROVAL_PROCESS_CODE` 字符串常量值，迁移脚本里直接写字面量，与项目里其余迁移脚本的写法一致，不从 Java 常量读取）。
2. 无需数据回填脚本之外的手工步骤，无需停机；本次不涉及 Flowable 自身 schema 变更。
3. 回滚策略：如需回滚，`ALTER TABLE tab_approval_switch DROP COLUMN process_code` 即可，`ApprovalProcessServiceImpl` 回退到上一版本代码（写死常量）即可恢复原行为；本次改动不改变 `tab_wf_*` 系列表结构，风险面窄。

## Open Questions

- `submit()` 阶段是否要为"绑定流程不可用"这个失败场景提供比引擎原生异常消息更友好的业务错误码/提示文案？本次按 Decision 3 不做，留给使用反馈后再评估。
