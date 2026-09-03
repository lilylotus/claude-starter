## ADDED Requirements

### Requirement: WorkflowService 抽象隔离 Flowable 引擎 API
系统 SHALL 提供 `WorkflowService` 接口作为业务代码接入审批流程的唯一入口，
覆盖 `start`/`approve`/`reject`/`returnTask`/`withdraw`/`transfer`/
`delegate`/`addSign`/`findTodoTasks`/`findDoneTasks`/`getProcessDetail`
操作。业务层 Controller 与 Service SHALL NOT 直接注入 Flowable 的
`RuntimeService`/`TaskService`/`RepositoryService`/`HistoryService`，
底层 Flowable 引擎调用 SHALL 全部封装在 `WorkflowService` 的实现内部。

#### Scenario: 业务层通过 WorkflowService 启动流程
- **WHEN** 某业务模块需要为一条待审批记录启动审批流程
- **THEN** 该业务模块调用 `WorkflowService.start(...)`，不出现对
  `RuntimeService`/`TaskService` 等 Flowable 引擎 Service 的直接依赖

### Requirement: 可配置的节点审批人规则
系统 SHALL 提供审批任务表（记录每个 Flowable 用户任务对应的
`assignee`/候选人明细/状态）与节点审批人规则表（按不可变的"流程定义
版本 + 节点 id"配置 `assignee_type`、`assignee_value`、
`approval_mode`、`empty_assignee_strategy`、`allow_self_approval`
及是否允许转办/委派/加签/退回），流程节点创建用户任务时 SHALL 按规则
解析出实际审批人，不依赖硬编码在 Java 分支中的业务判断。同一流程编码
发布新版本时，系统 SHALL 为新版本单独生成一套节点审批人规则记录，
SHALL NOT 修改已发布旧版本关联的规则记录，确保旧版本运行中的流程实例
始终按其发布时刻的规则运行。系统 SHALL
至少支持以下 `assignee_type`：指定人员（`USER`）、指定角色
（`ROLE`）、指定岗位（`POSITION`，当前无岗位数据源实现，解析结果恒为
空并按空审批人策略处理）、指定组织负责人（`ORG_LEADER`）、发起人部门
负责人（`APPLICANT_DEPT_LEADER`）、发起人部门上级负责人
（`APPLICANT_DEPT_PARENT_LEADER`）、流程发起人（`INITIATOR`）、上一
节点审批人（`PREVIOUS_APPROVER`）。

#### Scenario: 按角色规则解析出候选审批人
- **WHEN** 某节点配置 `assignee_type=ROLE`，`assignee_value` 指定的角色
  当前被若干名启用状态的管理员持有
- **THEN** 该节点用户任务的候选人集合等于这些管理员关联的用户 id 集合，
  任一候选人均可认领并处理该任务

#### Scenario: 发布新版本不影响旧版本运行中实例的审批人规则
- **WHEN** 某流程编码已有一个运行中的流程实例（跑在旧版本上），随后该
  流程编码发布了新版本，新版本修改了某节点的 `assignee_type`
- **THEN** 该运行中的旧实例后续流转到相同节点时仍按旧版本发布时刻的
  规则解析审批人，不受新版本规则变更影响

#### Scenario: 指定岗位类型当前恒返回空
- **WHEN** 某节点配置 `assignee_type=POSITION`
- **THEN** 系统解析结果为空候选人集合，按该节点配置的
  `empty_assignee_strategy` 处理，不抛出未捕获异常导致流程卡死

#### Scenario: 发起人部门负责人解析
- **WHEN** 某节点配置 `assignee_type=APPLICANT_DEPT_LEADER`，流程实例
  记录的发起人所属组织存在管辖范围覆盖该组织、持有约定角色的启用管理员
- **THEN** 该管理员关联的用户被设置为该节点任务的审批人

#### Scenario: 发起人部门无负责人时向上级部门查找
- **WHEN** 某节点配置 `assignee_type=APPLICANT_DEPT_PARENT_LEADER`，
  发起人所属组织没有可解析出的负责人，但其上级组织存在
- **THEN** 系统沿组织路径向上查找，取第一个能解析出负责人的上级组织
  对应的用户作为审批人

### Requirement: 空审批人与自审场景的处理策略
当某节点解析出的候选审批人集合为空，或候选人集合仅包含流程发起人本人
且该节点未允许自审时，系统 SHALL 按该节点配置的 `empty_assignee_strategy`
处理：`TO_WORKFLOW_ADMIN`（转配置的流程管理员角色）、`AUTO_SKIP`（自动
完成该节点并记录一条说明性审批轨迹）、`REJECT`（终止流程并记录失败
原因）。系统 SHALL NOT 在无候选人时让用户任务停留在"永远无人可处理"的
状态而不产生任何轨迹或告警信息。

#### Scenario: 空审批人转流程管理员
- **WHEN** 某节点 `empty_assignee_strategy=TO_WORKFLOW_ADMIN`，解析结果
  为空
- **THEN** 该节点用户任务的候选人被设置为配置的流程管理员角色关联用户

#### Scenario: 空审批人自动跳过
- **WHEN** 某节点 `empty_assignee_strategy=AUTO_SKIP`，解析结果为空
- **THEN** 系统自动完成该节点，流程进入下一节点，审批记录表新增一条
  `action=APPROVE` 且说明"无审批人自动通过"的轨迹

#### Scenario: 审批人为发起人本人且不允许自审
- **WHEN** 某节点解析出的唯一候选审批人是流程发起人本人，且该节点
  `allow_self_approval=false`
- **THEN** 系统按该节点的 `empty_assignee_strategy` 处理，不允许发起人
  直接审批自己提交的申请

#### Scenario: 明确允许自审时保留候选人
- **WHEN** 某节点解析出的唯一候选审批人是流程发起人本人，且该节点
  `allow_self_approval=true`
- **THEN** 该候选人不被替换，流程发起人可以处理该节点

### Requirement: 会签（多实例）审批
系统 SHALL 基于 Flowable 原生 Multi-Instance 机制支持会签节点，不在
业务层重新实现会签状态机，支持 `AND`（全部候选人通过才算通过）、
`OR`（任一候选人通过即算通过）、`PERCENT`（通过比例达到配置阈值即算
通过）三种会签模式；会签节点的候选人集合 SHALL 在该节点被创建之前
解析完成并写入流程变量，供 Multi-Instance 的集合表达式引用。

#### Scenario: AND 模式要求全部通过
- **WHEN** 某会签节点 `approval_mode=AND` 有 3 名候选审批人，其中 2 人
  已通过、1 人尚未处理
- **THEN** 该节点尚未完成，流程未进入下一节点

#### Scenario: OR 模式任一通过即完成
- **WHEN** 某会签节点 `approval_mode=OR` 有 3 名候选审批人，其中 1 人
  已通过
- **THEN** 该节点立即完成，流程进入下一节点，其余未处理的候选人任务被
  自动结束

#### Scenario: PERCENT 模式达到比例即完成
- **WHEN** 某会签节点 `approval_mode=PERCENT`、`approval_percent=60`，
  共 5 名候选审批人，其中 3 人已通过
- **THEN** 该节点完成（3/5=60% 达到阈值），流程进入下一节点

#### Scenario: 会签节点任一候选人驳回时终止
- **WHEN** 某会签节点的任一候选审批人执行驳回操作
- **THEN** 该节点立即终止，流程直接进入拒绝结束事件，其余候选人未处理
  的任务被自动结束，不等待其余候选人处理

### Requirement: Reject 与 Return 语义区分
系统 SHALL 区分"驳回"（Reject）与"退回"（Return）两种操作：Reject SHALL
直接终止当前流程实例并进入拒绝结束事件，不产生新的用户任务；Return
SHALL 将流程状态退回到指定的历史节点，重新触发该节点的审批人解析，
仅当目标节点配置 `allow_return=true` 时才允许发起 Return。系统 SHALL NOT
向业务代码或前端暴露 Flowable 原生的
`ChangeActivityStateBuilder`/`moveExecutionsToSingleActivityId` API，
Return 操作统一通过 `WorkflowService.returnTask(taskId, targetNodeId)`
封装。

#### Scenario: 驳回直接终止流程
- **WHEN** 审批人对当前任务执行驳回操作
- **THEN** 流程实例进入拒绝结束事件并结束，不产生新的用户任务

#### Scenario: 退回历史节点重新解析审批人
- **WHEN** 审批人对当前任务执行退回操作，目标为已配置
  `allow_return=true` 的历史节点
- **THEN** 流程状态回到目标节点，该节点重新解析审批人（不复用退回前
  遗留的审批人信息）

#### Scenario: 退回不允许的节点被拒绝
- **WHEN** 审批人尝试退回到未配置 `allow_return=true` 的节点
- **THEN** 系统拒绝该次操作，返回业务错误，流程状态不变

### Requirement: 撤回策略
系统 SHALL 提供 `WithdrawPolicy` 抽象，默认实现仅允许在流程实例尚未
产生任何"通过"或"驳回"审批记录时撤回；一旦已有审批记录，撤回操作
SHALL 被拒绝。撤回成功后，系统 SHALL 终止对应的流程实例，并将流程
实例状态置为"已撤回"。

#### Scenario: 首个审批人处理前允许撤回
- **WHEN** 流程发起人对一个尚无任何审批记录的流程实例执行撤回操作
- **THEN** 系统终止该流程实例，状态置为"已撤回"

#### Scenario: 已有审批记录后拒绝撤回
- **WHEN** 流程发起人对一个已存在至少一条"通过"或"驳回"审批记录的流程
  实例执行撤回操作
- **THEN** 系统拒绝该次撤回操作，返回业务错误，流程实例状态不变

### Requirement: 转办、委派与加签
系统 SHALL 提供转办（Transfer，变更当前任务处理人）、委派（Delegate，
委托他人处理后归还原处理人）、加签（AddSign，为会签节点动态增加候选
审批人）三类操作，仅当目标节点分别配置 `allow_transfer`/
`allow_delegate`/`allow_add_sign` 为真时才允许对应操作。转办与委派
SHALL 记录原处理人与新处理人；加签与减签 SHALL 使用 Flowable 提供的
多实例执行变更 API，系统 SHALL NOT 直接手工修改
`nrOfInstances`/`nrOfCompletedInstances` 等 Flowable 内部计数变量。

#### Scenario: 转办变更处理人
- **WHEN** 审批人对配置 `allow_transfer=true` 的当前任务执行转办操作，
  指定新的处理人
- **THEN** 该任务的处理人变更为新处理人，审批记录表记录原处理人与新
  处理人

#### Scenario: 转办不允许的节点被拒绝
- **WHEN** 审批人对未配置 `allow_transfer=true` 的节点尝试转办
- **THEN** 系统拒绝该次操作，返回业务错误

#### Scenario: 会签节点加签
- **WHEN** 审批人对配置 `allow_add_sign=true` 的会签节点执行加签操作，
  指定新增的候选审批人
- **THEN** 该会签节点新增一个待处理的审批任务分支，原有候选人的任务
  与完成条件判定不受影响

### Requirement: 审批操作幂等
系统 SHALL 对 `approve`/`reject`/`returnTask`/`withdraw`/`transfer`/
`delegate`/`addSign` 写操作提供基于请求方传入幂等键的去重保护：同一
幂等键的重复请求 SHALL 直接返回首次执行的结果，SHALL NOT 重复执行
底层流程引擎操作或产生重复的审批记录。未携带幂等键的请求不受此保护，
按正常流程处理。

#### Scenario: 重复提交同一幂等键的审批请求
- **WHEN** 客户端使用相同的幂等键连续两次调用同一任务的审批通过接口
- **THEN** 系统仅执行一次实际的审批操作，第二次请求返回与第一次相同的
  处理结果，不产生第二条审批记录

### Requirement: 任务处理越权校验
系统 SHALL 在执行任务处理类操作（完成、转办、委派、加签、退回）前校验
当前操作人满足以下至少一项：是该任务的指定处理人（assignee）；是该
任务候选人明细中的指定用户（candidateUser）；持有该任务候选人明细中
指定的角色（candidateGroup）。均不满足时系统 SHALL 拒绝该次操作。
候选组任务在执行完成操作前，若尚未被认领，系统 SHALL 自动先认领
（claim）再完成，不要求调用方额外调用认领接口。

#### Scenario: 非候选人处理任务被拒绝
- **WHEN** 某用户既不是任务的指定处理人，也不在候选人明细中，也不持有
  候选角色，尝试完成该任务
- **THEN** 系统拒绝该次操作，返回无权限错误，任务状态不变

#### Scenario: 候选组任务未认领时自动认领后完成
- **WHEN** 某候选组任务尚未被任何人认领，一名符合候选条件的用户直接
  调用完成接口
- **THEN** 系统自动将该任务认领给该用户后完成，不要求先调用认领接口

### Requirement: 待办与已办查询不依赖 Flowable 运行时表
系统 SHALL 提供"我的待办"与"我的已办"查询能力，数据来源为自有的审批
任务表与审批记录表，SHALL NOT 要求调用方或前端直接查询 Flowable 的
`ACT_RU_TASK`/`ACT_HI_TASKINST` 等运行时/历史表。

#### Scenario: 查询我的待办
- **WHEN** 当前用户调用"我的待办"查询接口
- **THEN** 系统返回该用户作为指定处理人或候选人（用户/角色维度）的全部
  待处理任务，数据来自审批任务表

#### Scenario: 查询我的已办
- **WHEN** 当前用户调用"我的已办"查询接口
- **THEN** 系统返回该用户已处理完成的审批记录，数据来自审批记录表
