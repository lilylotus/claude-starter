## Context

已通过只读代码走查（不含代码修改）确认以下事实，均可在当前工作树复核：

- `WorkflowModelCompilerV2`/`WorkflowModelCompilerImpl` 把条件节点编译为 `ExclusiveGateway`，没有显式
  `condition` 的边被标记为 `defaultFlow`；`ProcessModelDslV2Validator.validateConditionNodes` 只要求
  "唯一默认分支 + priority 唯一"，不要求整条流程图上必须存在至少一个审批（`userTask`）节点——
  `start → condition(仅默认分支) → end` 目前能正常通过发布校验、正常被业务绑定。
- Flowable 7.2.0 对"网关只有一条默认出边"的路由行为本身没有问题（已核对
  `ExclusiveGatewayActivityBehavior` 源码）：不会抛异常，只是正常、安静地按默认分支推进到结束事件。
- `WorkflowV2EndOutcomeListener`（挂在 v2 `endEvent` 的 `executionListener event="end"` 上）会在每一个
  结束事件把编译期固化的 `outcome`（`APPROVED`/`REJECTED`）写入流程变量 `approved`；v1 编译产物没有等价
  的"每个结束事件都写"逻辑，只在驳回分支显式写 `approved=false`，其余情况依赖
  `FlowableWorkflowService.finalizeInstanceIfEnded` 里"变量不存在时默认 `approved=true`"的既有兜底
  （`backend/.../FlowableWorkflowService.java:1035-1039`）。两者共同保证：无论 v1 还是 v2，只要流程已经
  跑到某个结束事件，`finalizeInstanceIfEnded` 都能读出正确的终态方向，不需要为这次修复改动任何监听器。
- `FlowableWorkflowService.start(StartProcessCommand)`（`backend/.../FlowableWorkflowService.java:137-194`）
  在 `runtimeService.startProcessInstanceById(...)` 之后只做了两件事：把 `flowableInstanceId` 写回
  `tab_wf_process_instance`、插入一条 `SUBMIT` 审批轨迹记录；从未调用同一个类里已经写好、`approve()`/
  `reject()`/会签计票完成路径末尾都会调用的私有方法 `finalizeInstanceIfEnded(Long processInstanceId)`
  （`:1024-1050`）。该方法本身是幂等、安全的："仍有剩余运行中执行"时直接 `return`，不会误伤正常等待人工
  审批的流程。
- `ApprovalRequestServiceImpl.submit(...)`（`backend/.../ApprovalRequestServiceImpl.java:138-199`）在
  `entity`（`tab_approval_request`）落库之后，同一事务内依次做：获取业务活动锁
  （`businessLockService.acquire`）→ 调用 `approvalProcessService.start(...)` 发起流程 → 用
  `findOpenTask(process.processInstanceId())` 查一次是否已经产生开放任务 → 无论查没查到，都把 `entity`
  存成 `PENDING`（申请自身的初始状态本来就是 `PENDING`，这里只是把 `processInstanceId`/
  `flowableProcessInstanceId`/`currentNodeName`/（可能有的）`flowableTaskId` 补齐后原样保存）。
- 与之对比，`approve(Long id, String opinion)`（`:204-226`）在调用 `approvalProcessService.approve(...)`
  之后，会重新查询 `tab_wf_process_instance.status` 并做穷尽分支处理：`RUNNING` → `advanceNode`（推进到
  下一节点，申请状态不变）；`APPROVED` → `finalizeApproval`（真正执行业务写入、回填 `resultTargetId`、
  申请状态置为"已通过"）；`TERMINATED` → `terminateAsRejected`（空审批人 `REJECT` 策略触发的系统终止，
  申请状态置为"已拒绝"）；其余情况抛异常。`submit()` 完全没有对应这套"流程已经跑到终态"的处理——这正是
  数据丢失的直接原因：`finalizeApproval()`（仓库里唯一调用
  `masterDataOperationExecutor.executeWrite(...)` 创建真实业务记录的地方）只能从 `approve()` 触发，而
  `approve()` 依赖 `requireCurrentTask(...)` 找到一个开放任务——零审批节点的流程永远没有任务，
  `approve()`/`finalizeApproval()` 永远不会被调用到。
- `finalizeApproval(ApprovalRequestEntity entity, Long approverId, String opinion)`（`:245-277`）通过
  `LambdaUpdateWrapper` 对 `tab_approval_request` 做局部字段更新，**不会**同步修改传入的 `entity` 这个
  Java 对象自身的字段（`entity.status`/`entity.approverId`/... 在方法返回后仍是调用前的旧值）。
  `submit()` 方法体末尾会用同一个（未刷新的）`entity` 对象构造返回给前端的 VO
  （`toVO(entity, displayNames)`）——如果直接照搬 `finalizeApproval` 现成逻辑而不额外处理，会产生一个新的
  "数据库已经是已通过，接口返回的 VO 却显示待审批"的不一致。

## Goals / Non-Goals

**Goals:**

1. 一次提交如果对应的流程在 `start()` 内部同步跑到终态（不存在任何等待人工处理的任务），系统 SHALL
   立即完成该终态对应的收尾：`APPROVED` 时真正创建/更新业务数据并回填 `resultTargetId`；`REJECTED` 时
   标记申请为已拒绝，不产生业务数据。两种情况都 SHALL 释放本次提交获取的业务活动锁，且 `tab_wf_
   process_instance.status`/`tab_approval_request.status` SHALL 与 Flowable 引擎的真实终态保持一致，不
   再停留在 `RUNNING`/"待审批"。
2. 正常需要人工审批（至少一个等待中的任务）的流程行为 SHALL 完全不变，不能因为这次修复引入任何回归。
3. 不修改数据库结构，不引入新的依赖。

**Non-Goals:**

- 不处理修复前已经产生的历史"僵尸"申请数据修复（是否需要一次性运维脚本补跑收尾，留给用户在本提案确认
  阶段决定是否纳入范围；默认不纳入）。
- 不在发布校验阶段新增"流程必须至少包含一个审批节点"的限制（见下方 Decision 3 的取舍说明）。
- 不涉及 `RELIABLE_ASYNC` 执行模式（`production-approval-lifecycle` change 里已记录为"未接入生产路由"，
  与本次修复的同步执行路径无关）。

## Decisions

### 1. 在 `FlowableWorkflowService.start()` 里补一次 `finalizeInstanceIfEnded` 调用

```java
ProcessInstance flowableInstance = runtimeService.startProcessInstanceById(
        processDefinition.getFlowableDefinitionId(),
        String.valueOf(instance.getId()),
        variables);

instance.setFlowableInstanceId(flowableInstance.getId());
instance.setUpdateTime(LocalDateTime.now());
processInstanceMapper.updateById(instance);

approvalRecordMapper.insert(ApprovalRecordEntity.builder()
        .processInstanceId(instance.getId())
        .operatorId(command.applicantId())
        .action(ApprovalAction.SUBMIT)
        ...
        .build());

finalizeInstanceIfEnded(instance.getId()); // 新增：流程若已同步跑完，立即收尾状态

ProcessInstanceEntity refreshed = processInstanceMapper.selectById(instance.getId());
return new WorkflowInstanceResult(
        refreshed.getId(),
        refreshed.getFlowableInstanceId(),
        refreshed.getCurrentNodeId(),
        refreshed.getCurrentNodeName());
```

放在 `SUBMIT` 轨迹记录之后、最终 `refreshed` 查询之前，这样返回值里的 `currentNodeId`/`currentNodeName`
本身就会正确反映"已经没有当前节点"（`finalizeInstanceIfEnded` 会把这两个字段清空），不需要额外改
`WorkflowInstanceResult` 的字段或调用方逻辑。

**为什么安全**：`finalizeInstanceIfEnded` 内部第一步就是
`runtimeService.createProcessInstanceQuery().processInstanceId(...).count()`，只有这个数字为 0（Flowable
判定该实例已经彻底跑完、不再存在于运行时表）才会继续往下收尾；只要还有一个用户任务在等待，这个查询就会
命中 1，方法直接 `return`，正常的多级审批流程完全不受影响。这与 `approve()`/`reject()`/会签计票完成路径
末尾调用它的前提完全一致，只是把调用时机也覆盖到"发起时就已经跑完"这一种此前遗漏的情况。

**备选方案（未采用）**：只在 `ApprovalProcessServiceImpl.start()` 或
`ApprovalRequestServiceImpl.submit()` 里查询流程实例状态、不改 `FlowableWorkflowService`。未采用原因：
`tab_wf_process_instance.status` 是流程引擎模块自己对外暴露的状态字段，"流程有没有跑完"是引擎模块自身
应该负责收尾的职责，不应该依赖上层业务模块（`approval` 包）反过来帮忙修正引擎模块自己的表；而且
`GET /api/v1/workflow/process-instances/{id}`（流程实例详情查询，`workflow` 模块自身对外的接口）如果不
修这里，即使 `approval` 层单独把申请状态修对了，流程实例详情页仍然会一直显示"运行中"，与申请的真实状态
矛盾。两处都要修，但顺序上引擎模块先收尾、业务模块再读取收尾后的结果，职责边界更清晰。

### 2. `ApprovalRequestServiceImpl.submit()` 补齐终态分支，复用/适配 `finalizeApproval`

```java
WorkflowInstanceResult process = approvalProcessService.start(
        entity.getId(), bizType, operationType, applicantId, applicantOrgId, typedPayload);
entity.setProcessInstanceId(process.processInstanceId());
entity.setFlowableProcessInstanceId(process.flowableProcessInstanceId());
entity.setCurrentNodeName(process.currentNodeName());

ProcessInstanceEntity instance = processInstanceMapper.selectById(process.processInstanceId());
switch (instance.getStatus()) {
    case ProcessInstanceStatus.RUNNING -> {
        ApprovalTaskEntity openTask = findOpenTask(process.processInstanceId());
        if (openTask != null) {
            entity.setFlowableTaskId(openTask.getFlowableTaskId());
        }
        approvalRequestMapper.updateById(entity);
    }
    case ProcessInstanceStatus.APPROVED -> {
        approvalRequestMapper.updateById(entity); // 先落库 processInstanceId 等字段
        finalizeApproval(entity, null, AUTO_APPROVED_OPINION);
    }
    case ProcessInstanceStatus.REJECTED -> {
        approvalRequestMapper.updateById(entity);
        finalizeAutoRejected(entity, AUTO_REJECTED_OPINION);
    }
    default -> throw new BusinessException("流程实例状态异常：" + instance.getStatus());
}

entity = approvalRequestMapper.selectById(entity.getId()); // 反映上面任一分支真正落库后的最新状态
Map<String, String> displayNames = userDisplayService.resolveDisplayNames(Set.of(currentUserId));
return WriteOperationResultVO.pending(toVO(entity, displayNames));
```

（示意代码，最终实现以真实类型/空值检查/事务边界为准，供 tasks.md 阶段核实调整。）

要点：

- **`finalizeApproval` 需要能接受 `approverId=null`**：`tab_approval_request.approver_id` 是
  `VARCHAR(64) NULL`（`db/migration/V1__init_schema.sql:917`），落库层面没有问题；但
  `finalizeApproval` 内部 `finally { CurrentUserContext.setUserId(approverId); }` 如果直接把
  线程本地用户上下文恢复成 `null`，会影响 `submit()` 方法体里这次调用之后仍在同一线程执行的后续代码（如
  `operationLogRecorder`、`userDisplayService`，以及请求处理链路上更晚执行的过滤器/切面）。必须改为恢复
  成本次提交的申请人 `applicantId`（`submit()` 方法一开始就已经解析出来，且此处"提交人"与"申请人"是
  同一个人），而不是恢复成传入的 `approverId` 参数本身。实现时把"写入 `approver_id` 列的值"和"
  `finally` 里恢复上下文用的值"拆成两个独立参数，或者在 `submit()` 侧包一层小方法，不直接改
  `finalizeApproval` 现有对 `approve()` 调用方的语义。
- **新增 `finalizeAutoRejected`**：不能直接复用现成的 `terminateAsRejected(entity, approverId, instance)`
  ——它的 `latestTerminateReason(instance.getId())` 是专门为"空审批人 `REJECT` 策略触发的系统终止"设计的
  （查询 `tab_wf_approval_record` 里 `action=TERMINATE` 的轨迹），我们这条路径压根没有 `TERMINATE`
  轨迹记录，套用会读到 `latestTerminateReason` 的兜底文案"无审批人自动终止"，语义不准确、容易误导后续
  运维排查。新方法直接使用固定的、准确描述这次场景的文案（如"系统自动拒绝：该流程未配置需要人工处理的
  审批节点，按默认分支直接流转到拒绝结束节点"），其余逻辑（更新 `tab_approval_request` 状态、
  `recordRequestStatusChange`、`releaseBusinessLock`）与 `terminateAsRejected` 结构一致。
- **`entity` 必须重新从数据库查询一次再构造响应 VO**：`finalizeApproval`/`finalizeAutoRejected`/
  `advanceNode`（沿用现状）都通过 `LambdaUpdateWrapper` 做局部字段更新，不会同步修改调用方手里那个
  `entity` Java 对象。`RUNNING` 分支目前是 `approvalRequestMapper.updateById(entity)`（全量保存修改过的
  同一个对象），所以理论上该分支的 `entity` 已经是最新的；但为了三个分支统一好维护、避免以后有人在某个
  分支改成局部更新却忘记同步 `entity`，最简单可靠的做法是在 `switch` 结束后统一重新
  `selectById` 一次，多付出的一次主键查询可忽略不计。
- **业务写入失败时的行为是预期内的变化，不是新缺陷**：如果 `finalizeApproval` 内部
  `masterDataOperationExecutor.executeWrite(...)` 抛出业务校验异常（如唯一性冲突），由于整段 `submit()`
  是 `@Transactional(rollbackFor = Exception.class)`，会连同本次已经插入的 `tab_approval_request` 行、
  已经启动的 Flowable 流程实例、已经获取的业务活动锁一起回滚，`submit()` 调用本身以异常形式失败。这与
  "审批开关关闭、直接生效"模式下业务校验失败直接抛出异常的现状语义一致（都是"当场失败，不留下任何
  半成品记录"），比修复前"悄悄留下一条永远卡住的待审批申请"更符合直觉，proposal.md Impact 已说明。

### 3. 不在发布校验阶段禁止"零审批节点"流程（备选方案，未采用）

考虑过在 `ProcessModelDslV2Validator`（以及 v1 对应校验器）里新增一条规则："流程图上必须存在至少一个可达
的审批（用户任务）节点，否则拒绝发布"，从源头避免这种流程被设计出来。

**未采用**，理由：

- 这类"某些分支完全不需要人工审批、直接自动通过/自动拒绝"的设计本身是合理且常见的建模需求（例如低风险
  变更走默认分支直接放行，只有高风险分支才配审批节点），不应该被当作错误配置一刀切拒绝。
- 加上这条限制之后，本次要修的"运行时没有正确处理零等待终态"这个根因依然存在——它不只发生在"整条流程都
  没有审批节点"，也会发生在"某条具体分支路径上没有经过任何审批节点，但流程图别处确实有审批节点"这种更
  常见的场景（校验"图上是否存在审批节点"这种全局判断，管不住"某条具体命中路径是否经过审批节点"）。只加
  发布校验、不修运行时收尾逻辑，无法覆盖这类场景，问题并未真正解决。
- 这是本次用户报告的具体复现路径（`start → condition(仅默认分支) → end`）背后的真实使用场景之一，直接
  禁止会让用户已经设计好的流程无法发布，属于比"根因修复"更激进的行为变更。

如果用户在确认阶段希望额外增加这条发布校验（作为运行时修复之外的锦上添花，防止"忘记加审批节点"这类
误操作），可以作为后续独立 change 处理，不阻塞本次数据丢失问题的修复。

## Risks / Trade-offs

- [`submit()` 内联执行业务写入，可能延长提交接口的响应时间] → 零审批节点场景本来就是小概率、快速路径
  （没有网络等待，只是多一次数据库写入+既有业务校验逻辑），可忽略；有开放任务的正常场景（绝大多数情况）
  完全不受影响。
- [`finalizeApproval` 被两个调用方共用后，未来有人改动其中一处逻辑却忘记验证另一处] → 通过为 `submit()`
  这条新路径补充专门的集成测试（见 tasks.md）覆盖，并在方法 Javadoc 里明确注明"审批人为 `null` 时代表
  提交即同步自动通过"这一新增语义。
- [历史遗留的僵尸申请不在本次修复范围] → proposal.md Impact 已明确说明，是否需要一次性运维脚本补跑收尾
  留给用户在确认阶段决定。

## Migration Plan

1. `FlowableWorkflowService.start()` 补充 `finalizeInstanceIfEnded` 调用；补单元/集成测试覆盖"零任务
   流程 `start()` 后实例状态立即变为 `APPROVED`/`REJECTED`"与"正常有任务的流程 `start()` 后实例状态仍为
   `RUNNING`（无回归）"两类场景。
2. `ApprovalRequestServiceImpl` 补齐 `submit()` 的终态分支、调整 `finalizeApproval` 对 `approverId=null`
   的支持、新增 `finalizeAutoRejected`；补集成测试覆盖 APPROVED/REJECTED/RUNNING 三种真实结果，以及
   "自动通过分支业务写入失败时整体回滚、不留下任何记录"的场景。
3. 运行现有回归测试（`workflow.*`、`approval.*`），确认零任务场景之外的既有审批流程（含
   `production-approval-lifecycle` change 已交付的并行/会签/条件分支等场景）无回归。
4. 用户确认阶段决定是否需要为历史僵尸申请编写一次性运维脚本/接口；如需要，作为本 change 的追加任务或
   独立后续 change 处理。
5. 实施结束后依真实 diff/测试结果同步本 change 的 `proposal.md`/`design.md`/`tasks.md`。
