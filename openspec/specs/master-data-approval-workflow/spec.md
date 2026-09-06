# master-data-approval-workflow Specification

## Purpose

为组织、用户、任职、应用四类主数据的新增、编辑、启用、停用、删除操作提供统一的审批流程：操作提交后生成一条待审批的变更申请，不立即修改业务数据，仅当拥有审批权限的用户批准后才执行原有的创建/更新/状态切换/删除逻辑；基于 Flowable 流程引擎驱动申请状态流转，为后续演进到多级审批预留空间；是否对某类业务对象启用审批可按业务对象类型独立开关配置。

## Requirements

### Requirement: 审批开关
系统 SHALL 提供 `tab_approval_switch` 表，为组织（ORG）、用户（USER）、任职（POSITION）、应用（APP）四类业务对象类型各维护一条独立的审批开关记录（`bizType` 唯一，`enabled` 布尔），数据库迁移时 SHALL 为四类业务对象类型均预置 `enabled=false`（默认全部关闭审批，系统初始化后四个模块的写接口直接生效，需管理员手动开启）。系统 SHALL 提供查询当前四类开关状态的接口（需要 `ApprovalManagement:switch:view` 权限点）与修改指定 `bizType` 开关状态的接口（需要 `ApprovalManagement:switch:edit` 权限点）。关闭某个 `bizType` 的开关 SHALL NOT 影响该 `bizType` 下已存在的"待审批"申请——这些申请仍可正常被批准、拒绝或撤回；关闭/开启开关本身不回溯处理开关变更前后已经生效或待处理的申请。

#### Scenario: 迁移完成后四类业务对象默认关闭审批
- **WHEN** 系统完成数据库迁移
- **THEN** 组织、用户、任职、应用四个 `bizType` 的审批开关均为关闭状态，四个模块的写接口直接生效

#### Scenario: 开启组织的审批开关
- **WHEN** 拥有 `ApprovalManagement:switch:edit` 权限的用户将默认关闭的 `bizType=ORG` 开关修改为开启
- **THEN** 系统保存该状态，此后组织的新增/编辑/启用/停用/删除接口调用改为生成待审批申请，不立即修改业务数据

#### Scenario: 关闭组织的审批开关
- **WHEN** 拥有 `ApprovalManagement:switch:edit` 权限的用户将已开启的 `bizType=ORG` 开关修改为关闭
- **THEN** 系统保存该状态，此后组织的新增/编辑/启用/停用/删除接口调用立即生效，不再生成审批申请

#### Scenario: 无权限修改开关被拒绝
- **WHEN** 不拥有 `ApprovalManagement:switch:edit` 权限的用户调用修改开关接口
- **THEN** 系统拒绝该次调用，返回无权限错误，开关状态不变

#### Scenario: 关闭开关不影响已存在的待审批申请
- **WHEN** `bizType=APP` 存在一条"待审批"状态的申请，随后该 `bizType` 的审批开关被关闭
- **THEN** 该条"待审批"申请仍然可以被正常批准、拒绝或撤回，不因开关关闭而被自动处理或失效

### Requirement: 提交时按开关状态分流为审批或直接生效
四个模块的新增/更新/启用/停用/删除接口被调用时，系统 SHALL 先查询该 `bizType` 当前的审批开关状态：开关开启时，按"提交审批申请"需求生成待审批的变更申请，不立即修改业务数据；开关关闭时，SHALL NOT 生成审批申请、SHALL NOT 启动 Flowable 流程实例，直接调用该模块既有的创建/更新/状态切换/删除方法立即执行，行为与未接入审批流程之前完全一致。四个模块的写接口 SHALL 统一返回一个"写操作结果"包装对象，包含：`approvalEnabled`（本次调用时该 `bizType` 的开关状态）、`approvalRequest`（开关开启时非空，审批申请信息）、`data`（开关关闭时非空，创建/更新后的业务数据）。

#### Scenario: 开关开启时提交生成待审批申请
- **WHEN** 某个 `bizType` 的审批开关为开启状态，客户端调用该 `bizType` 的新增接口
- **THEN** 响应的 `approvalEnabled` 为 `true`，`approvalRequest` 非空，`data` 为空，不创建真实业务记录

#### Scenario: 开关关闭时提交直接生效
- **WHEN** 某个 `bizType` 的审批开关为关闭状态，客户端调用该 `bizType` 的新增接口，携带的字段满足全部结构与业务规则校验
- **THEN** 系统直接创建该业务记录，响应的 `approvalEnabled` 为 `false`，`data` 非空且为创建后的业务数据，`approvalRequest` 为空，不生成审批申请、不启动流程实例

### Requirement: 审批申请数据模型
系统 SHALL 提供 `tab_approval_request` 表统一记录组织（ORG）、用户（USER）、任职（POSITION）、应用（APP）四类业务对象的新增（CREATE）、更新（UPDATE）、启用（ENABLE）、停用（DISABLE）、删除（DELETE）审批申请，每条申请包含：业务对象类型（`bizType`）、操作类型（`operationType`）、目标记录 id（`targetId`，CREATE 申请为空，其余操作类型必填）、审批通过后实际生效的记录 id（`resultTargetId`，仅 CREATE 申请审批通过后回填）、原始请求内容（`requestPayload`，JSON 序列化的创建/更新请求体，ENABLE/DISABLE/DELETE 申请为空）、状态（`status`：`1000`=待审批、`2000`=已通过、`3000`=已拒绝、`4000`=已撤回）、提交人（`createBy`）、提交时间（`createTime`）、审批人（`approverId`）、审批时间（`approveTime`）、审批意见（`opinion`）、关联的 Flowable 流程实例 id（`flowableProcessInstanceId`）与用户任务 id（`flowableTaskId`）、当前所处审批节点名称（`currentNodeName`，流程结束后为空）。审批流程 SHALL 支持多级审批：申请从"待审批"到"已通过"/"已拒绝"之间可能经过多个审批节点，`approverId`/`approveTime`/`opinion` 记录的是最终产生该状态变化（通过或拒绝）的那一级审批人；每一级的完整处理轨迹由通用审批引擎的审批记录能力维护，不在 `tab_approval_request` 表内逐级记录。

#### Scenario: 提交创建类申请时目标记录 id 为空
- **WHEN** 客户端提交一条 `bizType=ORG`、`operationType=CREATE` 的审批申请
- **THEN** 系统保存的申请记录 `targetId` 为空，`requestPayload` 保存完整的组织创建请求体

#### Scenario: 提交状态切换类申请时不保存请求体
- **WHEN** 客户端提交一条 `operationType=ENABLE` 或 `DISABLE` 的审批申请
- **THEN** 系统保存的申请记录 `requestPayload` 为空，`targetId` 为目标记录的 id

#### Scenario: 多级审批流转中更新当前节点名称
- **WHEN** 一条申请从第一级审批节点通过，流转到第二级审批节点
- **THEN** 系统将该申请的 `currentNodeName` 更新为第二级节点的名称，申请状态仍为"待审批"

### Requirement: 提交审批申请
本需求描述的是"审批开关"开启状态下的提交行为（开关关闭时的直接生效行为见"提交时按开关状态分流为审批或直接生效"需求）。系统 SHALL 提供按 `bizType`（ORG/USER/POSITION/APP）与 `operationType`（CREATE/UPDATE/ENABLE/DISABLE/DELETE）提交审批申请的接口。提交时系统 SHALL 执行：请求体的结构校验（`@Valid`，必填/格式/长度等，与该 `bizType` 对应模块既有创建/更新请求体的校验规则一致）；管辖组织范围校验（复用"解析当前登录用户管辖组织范围"能力，规则与该 `bizType` 现有写接口的管辖范围校验完全一致——受限时目标组织/所属组织必须落在允许集合内，不落在范围内时拒绝提交，不生成申请记录）。系统 SHALL NOT 在提交阶段执行依赖当前数据库状态的业务规则校验（如编码唯一性、父子关系约束），这类校验延后到审批通过时执行。提交成功后，系统 SHALL 启动一个 Flowable 流程实例并创建对应的用户任务，申请状态置为"待审批"。

#### Scenario: 提交创建组织申请成功
- **WHEN** 客户端提交一条 `bizType=ORG`、`operationType=CREATE` 的申请，携带的组织名称、编码等字段满足结构校验，当前登录用户的管辖组织范围允许操作请求携带的 `parentId`
- **THEN** 系统创建一条状态为"待审批"的申请记录，启动对应的 Flowable 流程实例，不创建真实的组织记录

#### Scenario: 提交时结构校验不通过被拒绝
- **WHEN** 客户端提交一条申请，请求体缺少必填字段或格式不合法
- **THEN** 系统拒绝提交，返回参数校验错误，不生成申请记录，不启动流程实例

#### Scenario: 提交时管辖组织范围校验不通过被拒绝
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，客户端提交一条申请，请求携带的目标组织/所属组织不在管辖组织范围允许集合内
- **THEN** 系统拒绝提交，返回业务错误，不生成申请记录

#### Scenario: 提交时不校验编码唯一性等业务规则
- **WHEN** 客户端提交一条 `bizType=APP`、`operationType=CREATE` 的申请，携带的应用编码与当前某条未删除应用的编码相同
- **THEN** 系统仍然成功生成一条状态为"待审批"的申请记录，不在提交阶段因编码重复而拒绝

### Requirement: 审批通过后执行既有业务逻辑
系统 SHALL 提供审批通过接口，仅 `ApprovalManagement:request:approve` 权限点持有者，且同时是该申请当前所处审批节点解析出的指定处理人或候选人（用户或角色维度命中）时，才允许调用；候选组节点未认领时系统 SHALL 自动先认领再完成。系统 SHALL 在申请发起时冻结 executionMode；历史和未启用新模式的申请使用 LEGACY_SYNC。审批流程 SHALL 支持多级：非最终节点审批 SHALL 只推进流程到下一节点、更新 `currentNodeName`，不执行正式主数据变更，申请状态保持"待审批"。

> **当前实现状态**：`RELIABLE_ASYNC` 的 Outbox 生产/消费基础设施与 ORG/USER 两类业务执行适配器已实现，但 `ProcessBindingResolutionService.resolveForStart` 当前对任何 `RELIABLE_ASYNC` 绑定一律拒绝并提示"可靠异步执行尚未实现"，因此生产环境目前**没有任何入口**能让一次真实的审批提交真正走上 `RELIABLE_ASYNC` 路径；"新绑定可显式选择 RELIABLE_ASYNC"仅描述目标形态，尚未生效。POSITION/APP 两类业务的执行适配器也尚未实现。放开该入口、补齐 POSITION/APP 适配器留待后续 change。

LEGACY_SYNC 最终审批通过时，系统 SHALL 以该申请的**提交人**身份（而非当前调用审批接口的审批人身份）重新执行一次管辖组织范围校验，通过后调用该 `bizType` 对应模块既有的创建/更新/启用/停用/删除方法（`request_payload` 反序列化为该方法的请求参数），复用该方法内部已有的全部业务规则校验（如唯一性、父子关系约束）与操作日志记录；该方法执行的操作日志中"操作人"字段 SHALL 记录为提交人，而非审批人。业务校验失败时审批操作 SHALL 返回失败，申请状态 SHALL 保持"待审批"不变，不自动转为"已拒绝"，也不创建/修改任何业务记录，流程也不会推进到已通过状态。成功后申请 SHALL 置为"已通过"，记录最终审批人与审批时间；`operationType=CREATE` 的申请 SHALL 回填 `resultTargetId` 为新创建记录的 id；系统 SHALL 完成该申请关联的 Flowable 用户任务。

RELIABLE_ASYNC 最终审批 SHALL 同事务完成引擎推进、审批通过状态、审批审计和业务执行 Outbox，executionStatus 置 PENDING。执行器 SHALL 使用提交人的当前权限和管辖范围、目标版本与既有业务规则校验，再执行相同主数据方法；成功原子保存 SUCCEEDED、resultTargetId 与消费标记，操作日志仍归属提交人并额外追踪审批人与执行身份。失败 SHALL 保留审批通过结果，按原因置 FAILED_RETRYABLE 或 FAILED_MANUAL，不回滚已完成审批，也不修改未授权或冲突的业务数据。改变 payload SHALL 必须重新申请审批。

#### Scenario: 非最终节点通过仅推进流程
- **WHEN** 审批人对一条配置了两级审批的待审批申请，在第一级节点调用审批通过接口
- **THEN** 系统不执行任何业务数据创建/更新，申请状态保持"待审批"，`currentNodeName` 更新为第二级节点名称

#### Scenario: 审批通过创建类申请后执行创建
- **WHEN** LEGACY_SYNC 创建类申请最终批准且提交人范围与业务校验通过
- **THEN** 同步创建业务记录、操作日志归属提交人，申请已通过并回填 resultTargetId

#### Scenario: 审批通过时业务规则校验失败，申请保持待审批
- **WHEN** LEGACY_SYNC 组织创建申请最终批准时组织编码已被占用
- **THEN** 返回业务错误，申请保持待审批且不创建组织

#### Scenario: 审批通过时提交人的管辖组织范围已收紧导致失败
- **WHEN** LEGACY_SYNC 申请最终批准时提交人已无目标组织管辖范围
- **THEN** 审批失败且申请保持待审批，不执行业务变更

#### Scenario: 更新用户类申请审批通过后同步执行任职记录整体更新
- **WHEN** LEGACY_SYNC 用户更新申请携带完整 positions 并通过最终审批
- **THEN** 按既有用户更新规则同步新增、更新、删除任职记录，与直接调用用户更新接口一致

#### Scenario: 无审批权限的用户调用审批通过接口被拒绝
- **WHEN** 用户无审批权限或不具备当前任务资格
- **THEN** 请求被拒绝且申请与任务状态不变

#### Scenario: 拥有审批权限但不是当前节点处理人被拒绝
- **WHEN** 拥有 `ApprovalManagement:request:approve` 权限点的用户，对一条当前处于第一级审批节点、但该用户既不是该节点指定处理人也不在候选人范围内的申请调用审批通过接口
- **THEN** 系统拒绝该次调用，返回无权限错误，该申请状态与所处节点不变

#### Scenario: 新模式业务校验失败
- **WHEN** RELIABLE_ASYNC 最终审批通过后执行器发现编码冲突或提交人范围收紧
- **THEN** 审批保持已通过，执行标记 FAILED_MANUAL，正式数据不变，页面展示失败原因与处理入口

#### Scenario: 新模式异步更新用户及任职
- **WHEN** RELIABLE_ASYNC 用户更新申请最终通过且执行校验成功
- **THEN** 用户及完整 positions 更新在同一业务事务生效，成功结果与消费标记一起提交

> 该场景当前只能通过测试手工构造 `RELIABLE_ASYNC` 数据并手动触发 Outbox 事件消费来验证执行适配器本身的正确性，无法通过真实提交审批的生产 API 端到端触发（见上文"当前实现状态"）。

### Requirement: 审批拒绝
系统 SHALL 提供审批拒绝接口，仅 `ApprovalManagement:request:approve` 权限点持有者，且同时是该申请当前所处审批节点解析出的指定处理人或候选人时才允许调用，且必须携带非空的拒绝意见。无论申请当前处于第几级审批节点，审批拒绝时系统 SHALL NOT 执行该 `bizType` 对应的创建/更新/状态切换/删除逻辑，业务数据保持不变；系统 SHALL 将该申请状态置为"已拒绝"，记录拒绝人、拒绝时间与拒绝意见，并终止该申请关联的 Flowable 流程实例（不再等待其余审批级别）。

#### Scenario: 拒绝待审批申请
- **WHEN** 审批人对一条待审批申请调用审批拒绝接口，携带拒绝意见"信息不完整"
- **THEN** 系统将该申请状态置为"已拒绝"，记录该意见，不执行任何业务数据变更

#### Scenario: 拒绝时未携带意见被拒绝
- **WHEN** 审批人调用审批拒绝接口但未携带拒绝意见
- **THEN** 系统拒绝该次调用，返回参数校验错误，该申请状态不变

#### Scenario: 非最终节点拒绝直接终止流程
- **WHEN** 一条配置了两级审批的申请当前处于第一级审批节点，第一级审批人调用审批拒绝接口
- **THEN** 系统将该申请状态直接置为"已拒绝"，不再流转到第二级审批节点

### Requirement: 撤回审批申请
系统 SHALL 允许申请的提交人撤回自己提交的、状态仍为"待审批"且尚未产生任何一级"通过"或"拒绝"处理记录的申请；非提交人不能撤回，已处理（已通过/已拒绝/已撤回）的申请不能重复撤回，已经至少经过一级审批处理（即使仍处于"待审批"、尚在流转到下一级）的申请也不能撤回。撤回时系统 SHALL 将该申请状态置为"已撤回"，并终止其关联的 Flowable 流程实例。

#### Scenario: 提交人撤回自己的待审批申请
- **WHEN** 提交人对自己提交的、尚未有任何一级审批处理记录的待审批申请调用撤回接口
- **THEN** 系统将该申请状态置为"已撤回"，该流程实例被终止，不执行任何业务数据变更

#### Scenario: 非提交人撤回被拒绝
- **WHEN** 非提交人（含拥有审批权限的用户）对他人提交的待审批申请调用撤回接口
- **THEN** 系统拒绝该次调用，返回业务错误，该申请状态不变

#### Scenario: 已处理的申请不能撤回
- **WHEN** 提交人对一条状态已经是"已通过"/"已拒绝"/"已撤回"的申请调用撤回接口
- **THEN** 系统拒绝该次调用，返回业务错误

#### Scenario: 已经过第一级审批的多级申请不能撤回
- **WHEN** 一条配置了两级审批的申请已经通过第一级审批、流转到第二级审批节点，仍处于"待审批"状态，提交人调用撤回接口
- **THEN** 系统拒绝该次撤回操作，返回业务错误，该申请状态与所处节点不变

### Requirement: 审批申请查询
系统 SHALL 提供"我的申请"查询接口（当前登录用户作为提交人提交的全部申请，可按 `bizType`/`operationType`/`status` 过滤，分页，按提交时间降序）与"待我审批"查询接口（分页，按提交时间降序，仅 `ApprovalManagement:request:approve` 权限点持有者可调用）。"待我审批"查询结果 SHALL 仅包含当前处于"待审批"状态、且当前所处审批节点将当前登录用户解析为指定处理人或候选人（用户或角色维度命中）的申请，不再是"仅持有权限点即可看到全部待审批申请"。查询结果 SHALL 携带足够信息供前端渲染申请详情与当前所处审批级别名称（`currentNodeName`），UPDATE 类型的申请 SHALL 同时提供目标记录当前值与 `requestPayload` 中的新值，供前端新旧对照展示；CREATE 类型只提供 `requestPayload` 中的新值。

#### Scenario: 查询我的申请
- **WHEN** 当前登录用户调用"我的申请"查询接口
- **THEN** 系统返回当前用户作为提交人的全部申请分页列表，按提交时间降序排列，每条记录携带当前所处审批级别名称

#### Scenario: 查询待我审批仅返回当前节点命中的申请
- **WHEN** 拥有 `ApprovalManagement:request:approve` 权限点的用户调用"待我审批"查询接口，系统中存在两条待审批申请，一条当前节点将该用户解析为候选人，另一条当前节点的候选人不包含该用户
- **THEN** 返回结果仅包含前一条申请，不包含后一条

#### Scenario: 无审批权限调用待我审批被拒绝
- **WHEN** 不拥有 `ApprovalManagement:request:approve` 权限点的用户调用"待我审批"查询接口
- **THEN** 系统拒绝该次调用，返回无权限错误

#### Scenario: 更新类申请查询结果包含新旧对照
- **WHEN** 查询一条 `operationType=UPDATE` 的申请详情
- **THEN** 返回结果同时包含目标记录当前的字段值与 `requestPayload` 中提交的新字段值

### Requirement: 管理页面的审批入口
系统 SHALL 提供"我的申请"、"待我审批"、"审批设置"三个前端页面；"待我审批"页面的访问与操作 SHALL 受 `ApprovalManagement:request:approve` 权限点门控，"审批设置"页面的访问 SHALL 受 `ApprovalManagement:switch:view` 权限点门控、修改开关操作 SHALL 受 `ApprovalManagement:switch:edit` 权限点门控，无对应权限的用户看不到相应菜单入口。组织、用户、任职、应用四个管理页面的新增/编辑/启用/停用/删除操作，调用对应接口成功后 SHALL 按响应的 `approvalEnabled` 字段分别展示提示：为 `true` 时展示"已提交审批，等待审批通过后生效"，不假定接口返回的是最终生效的业务数据；为 `false` 时展示与本 change 之前一致的直接生效提示（如"创建成功"），并使用响应的 `data` 更新页面展示。"我的申请""待我审批"两个页面共用的申请详情展示（含 `UPDATE` 类型申请的新旧字段对照）依赖字段渲染元数据接口（`GET /api/form-fields/render-schema`，见 `password-login-auth` 能力"表单字段渲染元数据接口豁免操作资源编码校验"）查询业务对象类型的字段展示名与控件配置，该查询 SHALL NOT 因当前查看者不持有被审批业务对象（组织/用户/任职/应用）对应的管理权限点而失败——审批详情的查看权限完全由用户能否看到"我的申请"（自助，任何登录用户）或"待我审批"（`ApprovalManagement:request:approve`）决定，不叠加被审批对象自身的管理权限点要求。

#### Scenario: 无审批权限的用户看不到待我审批菜单
- **WHEN** 当前登录用户的权限编码集合不包含 `ApprovalManagement:request:approve`
- **THEN** 侧边导航不展示"待我审批"菜单项

#### Scenario: 无审批开关查看权限的用户看不到审批设置菜单
- **WHEN** 当前登录用户的权限编码集合不包含 `ApprovalManagement:switch:view`
- **THEN** 侧边导航不展示"审批设置"菜单项

#### Scenario: 开关开启时提交新增组织申请后展示待审批提示
- **WHEN** 组织的审批开关为开启状态，用户在组织管理页面提交新增组织表单
- **THEN** 页面展示"已提交审批，等待审批通过后生效"提示，不在列表中立即展示新组织

#### Scenario: 查看更新类申请详情不要求被审批对象的管理权限点
- **WHEN** 用户在"我的申请"或"待我审批"页面打开一条 `operationType=UPDATE` 的申请详情，该用户当前权限编码集合不包含该申请 `bizType` 对应的管理权限点（如 `OrgManagement:org:view`）
- **THEN** 详情弹窗仍能正常拉取到该 `bizType` 的字段渲染元数据并展示新旧字段对照，不因缺少该业务管理权限点而报错或留空

#### Scenario: 开关关闭时提交新增组织后直接展示新数据
- **WHEN** 组织的审批开关为关闭状态，用户在组织管理页面提交新增组织表单
- **THEN** 页面展示创建成功提示，并直接展示新创建的组织数据，行为与本 change 之前一致

### Requirement: 双状态展示与模式兼容
系统 SHALL 为申请返回 executionMode、approvalStatus、executionStatus、可见失败信息与 resultTargetId。前端 SHALL 区分审批中、审批通过待生效、已生效和执行失败；旧申请不因新绑定切换而改变语义。审批开关关闭后的直接生效行为 SHALL 保持不变。按申请 ID 审批且存在多个可操作任务时 SHALL 要求明确 taskId，不能任取任务。

> **当前实现状态**：后端字段（executionMode/approvalStatus/executionStatus/resultTargetId）已随申请落库返回；"前端 SHALL 区分…四种状态"这部分属于前端设计器/待办体验范围，本 change 未实现，留待后续 change。另见"审批通过后执行既有业务逻辑"需求中关于 `RELIABLE_ASYNC` 尚无生产入口的说明。

#### Scenario: 同意后尚未生效
- **WHEN** 新模式最终审批已通过但执行事件尚未消费
- **THEN** 页面显示审批通过待生效，不显示业务创建成功

#### Scenario: 切换绑定不改变存量
- **WHEN** 绑定从 LEGACY_SYNC 切到 RELIABLE_ASYNC
- **THEN** 已发起申请仍按原模式执行，新申请采用新模式

> 由于 `resolveForStart` 当前拒绝 `RELIABLE_ASYNC` 绑定，"切到 RELIABLE_ASYNC"这一步在生产环境暂不可执行，该场景目前只在单元/集成测试层面验证。

#### Scenario: 多任务消歧
- **WHEN** 同一审批人拥有某申请的多个并行待办且请求未指定 taskId
- **THEN** 返回任务歧义错误，要求选择明确任务
