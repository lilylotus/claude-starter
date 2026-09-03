## MODIFIED Requirements

### Requirement: 审批通过后执行既有业务逻辑
系统 SHALL 提供审批通过接口，仅 `ApprovalManagement:request:approve` 权限点持有者且具备当前任务资格的用户可调用。系统 SHALL 在申请发起时冻结 executionMode；历史和未启用新模式的申请使用 LEGACY_SYNC，新绑定可显式选择 RELIABLE_ASYNC。非最终节点审批 SHALL 只推进流程，不执行正式主数据变更。

LEGACY_SYNC 最终审批通过时，系统 SHALL 以该申请的**提交人**身份（而非当前调用审批接口的审批人身份）重新执行一次管辖组织范围校验，通过后调用该 `bizType` 对应模块既有的创建/更新/启用/停用/删除方法（`request_payload` 反序列化为该方法的请求参数），复用全部业务规则校验与操作日志记录；操作日志中操作人 SHALL 为提交人。业务校验失败时审批操作 SHALL 返回失败，申请保持待审批，不自动拒绝且不修改正式业务记录。成功后申请 SHALL 为已通过，记录审批人与时间，CREATE回填resultTargetId，并完成关联Flowable任务。

RELIABLE_ASYNC 最终审批 SHALL 同事务完成引擎推进、审批通过状态、审批审计和业务执行Outbox，executionStatus置PENDING。执行器 SHALL 使用提交人的当前权限和管辖范围、目标版本与既有业务规则校验，再执行相同主数据方法；成功原子保存SUCCEEDED、resultTargetId与消费标记，操作日志仍归属提交人并额外追踪审批人与执行身份。失败 SHALL 保留审批通过结果，按原因置FAILED_RETRYABLE或FAILED_MANUAL，不回滚已完成审批，也不修改未授权或冲突的业务数据。改变payload SHALL 必须重新申请审批。

#### Scenario: 审批通过创建类申请后执行创建
- **WHEN** LEGACY_SYNC创建类申请最终批准且提交人范围与业务校验通过
- **THEN** 同步创建业务记录、操作日志归属提交人，申请已通过并回填resultTargetId

#### Scenario: 审批通过时业务规则校验失败，申请保持待审批
- **WHEN** LEGACY_SYNC组织创建申请最终批准时组织编码已被占用
- **THEN** 返回业务错误，申请保持待审批且不创建组织

#### Scenario: 审批通过时提交人的管辖组织范围已收紧导致失败
- **WHEN** LEGACY_SYNC申请最终批准时提交人已无目标组织管辖范围
- **THEN** 审批失败且申请保持待审批，不执行业务变更

#### Scenario: 更新用户类申请审批通过后同步执行任职记录整体更新
- **WHEN** LEGACY_SYNC用户更新申请携带完整positions并通过最终审批
- **THEN** 按既有用户更新规则同步新增、更新、删除任职记录，与直接调用用户更新接口一致

#### Scenario: 无审批权限的用户调用审批通过接口被拒绝
- **WHEN** 用户无审批权限或不具备当前任务资格
- **THEN** 请求被拒绝且申请与任务状态不变

#### Scenario: 新模式业务校验失败
- **WHEN** RELIABLE_ASYNC最终审批通过后执行器发现编码冲突或提交人范围收紧
- **THEN** 审批保持已通过，执行标记FAILED_MANUAL，正式数据不变，页面展示失败原因与处理入口

#### Scenario: 新模式异步更新用户及任职
- **WHEN** RELIABLE_ASYNC用户更新申请最终通过且执行校验成功
- **THEN** 用户及完整positions更新在同一业务事务生效，成功结果与消费标记一起提交

## ADDED Requirements

### Requirement: 双状态展示与模式兼容
系统 SHALL 为申请返回executionMode、approvalStatus、executionStatus、可见失败信息与resultTargetId。前端 SHALL 区分审批中、审批通过待生效、已生效和执行失败；旧申请不因新绑定切换而改变语义。审批开关关闭后的直接生效行为 SHALL 保持不变。按申请ID审批且存在多个可操作任务时 SHALL 要求明确taskId，不能任取任务。

#### Scenario: 同意后尚未生效
- **WHEN** 新模式最终审批已通过但执行事件尚未消费
- **THEN** 页面显示审批通过待生效，不显示业务创建成功

#### Scenario: 切换绑定不改变存量
- **WHEN** 绑定从LEGACY_SYNC切到RELIABLE_ASYNC
- **THEN** 已发起申请仍按原模式执行，新申请采用新模式

#### Scenario: 多任务消歧
- **WHEN** 同一审批人拥有某申请的多个并行待办且请求未指定taskId
- **THEN** 返回任务歧义错误，要求选择明确任务
