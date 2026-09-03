## MODIFIED Requirements

### Requirement: 审批开关
系统 SHALL 提供 `tab_approval_switch` 表，为组织（ORG）、用户（USER）、任职（POSITION）、应用（APP）四类业务对象类型各维护一条独立的审批开关记录（`bizType` 唯一，`enabled` 布尔，`processCode` 可空字符串，记录该业务对象类型当前绑定的流程模型编码，关联 `tab_wf_process_model.process_code`）。数据库迁移时 SHALL 为四类业务对象类型均预置 `enabled=false`（默认全部关闭审批，系统初始化后四个模块的写接口直接生效，需管理员手动开启），`processCode` SHALL 预置为系统预置的默认流程编码，保证迁移后行为与迁移前一致。系统 SHALL 提供查询当前四类开关状态（含各自绑定的 `processCode`）的接口（需要 `ApprovalManagement:switch:view` 权限点）与修改指定 `bizType` 开关状态及绑定流程的接口（需要 `ApprovalManagement:switch:edit` 权限点）。修改接口 SHALL 校验：当目标状态为 `enabled=true` 时，`processCode` 必须非空，且必须对应一个当前 `status=PUBLISHED` 的 `tab_wf_process_model` 记录，不满足时系统 SHALL 拒绝本次修改，开关状态与绑定值均保持不变；当目标状态为 `enabled=false` 时，SHALL NOT 对 `processCode` 的可用性做该项校验（允许提前绑定尚未发布的流程）。关闭某个 `bizType` 的开关 SHALL NOT 影响该 `bizType` 下已存在的"待审批"申请——这些申请仍可正常被批准、拒绝或撤回；关闭/开启开关本身不回溯处理开关变更前后已经生效或待处理的申请。

#### Scenario: 迁移完成后四类业务对象默认关闭审批且绑定预置流程
- **WHEN** 系统完成数据库迁移
- **THEN** 组织、用户、任职、应用四个 `bizType` 的审批开关均为关闭状态，`processCode` 均绑定系统预置的默认流程编码，四个模块的写接口直接生效

#### Scenario: 开启组织的审批开关且绑定流程已发布
- **WHEN** 拥有 `ApprovalManagement:switch:edit` 权限的用户将默认关闭的 `bizType=ORG` 开关修改为开启，并指定一个当前状态为 `PUBLISHED` 的 `processCode`
- **THEN** 系统保存该状态与绑定，此后组织的新增/编辑/启用/停用/删除接口调用改为生成待审批申请并按该 `processCode` 启动流程，不立即修改业务数据

#### Scenario: 开启开关但绑定流程未发布被拒绝
- **WHEN** 拥有 `ApprovalManagement:switch:edit` 权限的用户尝试将某 `bizType` 开关修改为开启，指定的 `processCode` 对应的流程模型当前状态不是 `PUBLISHED`（如仍为草稿或已下线）
- **THEN** 系统拒绝本次修改，返回业务错误，开关状态与绑定值保持修改前的值不变

#### Scenario: 开启开关但未指定绑定流程被拒绝
- **WHEN** 拥有 `ApprovalManagement:switch:edit` 权限的用户尝试将某 `bizType` 开关修改为开启，但未指定 `processCode`
- **THEN** 系统拒绝本次修改，返回参数校验错误，开关状态与绑定值保持修改前的值不变

#### Scenario: 关闭组织的审批开关
- **WHEN** 拥有 `ApprovalManagement:switch:edit` 权限的用户将已开启的 `bizType=ORG` 开关修改为关闭
- **THEN** 系统保存该状态，此后组织的新增/编辑/启用/停用/删除接口调用立即生效，不再生成审批申请；本次修改不校验当前绑定流程的发布状态

#### Scenario: 无权限修改开关被拒绝
- **WHEN** 不拥有 `ApprovalManagement:switch:edit` 权限的用户调用修改开关接口
- **THEN** 系统拒绝该次调用，返回无权限错误，开关状态与绑定值不变

#### Scenario: 关闭开关不影响已存在的待审批申请
- **WHEN** `bizType=APP` 存在一条"待审批"状态的申请，随后该 `bizType` 的审批开关被关闭
- **THEN** 该条"待审批"申请仍然可以被正常批准、拒绝或撤回，不因开关关闭而被自动处理或失效

### Requirement: 提交时按开关状态分流为审批或直接生效
四个模块的新增/更新/启用/停用/删除接口被调用时，系统 SHALL 先查询该 `bizType` 当前的审批开关状态：开关开启时，按"提交审批申请"需求生成待审批的变更申请，SHALL 使用该 `bizType` 当前绑定的 `processCode` 启动 Flowable 流程实例（不再使用固定不变的流程编码），不立即修改业务数据；若该 `bizType` 当前绑定的 `processCode` 对应的流程模型此时已不处于 `PUBLISHED` 状态（如绑定后被设计器下线），系统 SHALL 拒绝本次提交，返回业务错误，SHALL NOT 静默改用其他流程、SHALL NOT 生成申请记录；开关关闭时，SHALL NOT 生成审批申请、SHALL NOT 启动 Flowable 流程实例，直接调用该模块既有的创建/更新/状态切换/删除方法立即执行，行为与未接入审批流程之前完全一致。四个模块的写接口 SHALL 统一返回一个"写操作结果"包装对象，包含：`approvalEnabled`（本次调用时该 `bizType` 的开关状态）、`approvalRequest`（开关开启时非空，审批申请信息）、`data`（开关关闭时非空，创建/更新后的业务数据）。

#### Scenario: 开关开启时提交生成待审批申请并按绑定流程启动
- **WHEN** 某个 `bizType` 的审批开关为开启状态且绑定的流程当前为 `PUBLISHED`，客户端调用该 `bizType` 的新增接口
- **THEN** 响应的 `approvalEnabled` 为 `true`，`approvalRequest` 非空，`data` 为空，系统按该 `bizType` 绑定的 `processCode` 启动流程实例，不创建真实业务记录

#### Scenario: 开关开启但绑定流程已被下线导致提交失败
- **WHEN** 某个 `bizType` 的审批开关为开启状态，其绑定的 `processCode` 对应的流程模型此前已被设计器下线（`status` 不再是 `PUBLISHED`），客户端调用该 `bizType` 的新增接口
- **THEN** 系统拒绝本次提交，返回业务错误，不生成审批申请，不启动流程实例，不创建真实业务记录

#### Scenario: 开关关闭时提交直接生效
- **WHEN** 某个 `bizType` 的审批开关为关闭状态，客户端调用该 `bizType` 的新增接口，携带的字段满足全部结构与业务规则校验
- **THEN** 系统直接创建该业务记录，响应的 `approvalEnabled` 为 `false`，`data` 非空且为创建后的业务数据，`approvalRequest` 为空，不生成审批申请、不启动流程实例
