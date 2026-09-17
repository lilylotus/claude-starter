## MODIFIED Requirements

### Requirement: 审批申请查询
系统 SHALL 提供"我的申请"查询接口（当前登录用户作为提交人提交的全部申请，可按 `bizType`/`operationType`/`status` 过滤，分页，按提交时间降序）与"待我审批"查询接口（分页，按提交时间降序，仅 `ApprovalManagement:request:approve` 权限点持有者可调用）。"待我审批"查询结果 SHALL 仅包含当前处于"待审批"状态、且当前所处审批节点将当前登录用户解析为指定处理人或候选人（用户或角色维度命中）的申请，不再是"仅持有权限点即可看到全部待审批申请"。查询结果 SHALL 携带足够信息供前端渲染申请详情与当前所处审批级别名称（`currentNodeName`）、以及该申请关联的流程实例 id（`processInstanceId`，供前端据此查询完整的流程实例详情与节点图，见"管理页面的审批入口"需求）。

`operationType=UPDATE`/`ENABLE`/`DISABLE`/`DELETE`（即 `targetId` 非空的四类操作）SHALL 提供目标记录当前值（`targetSnapshot`），其中 UPDATE 额外同时提供 `requestPayload` 中的新值供前端新旧对照展示；`operationType=CREATE`（`targetId` 为空）只提供 `requestPayload` 中的新值。目标记录当前值与新值 SHALL 携带足够信息供前端解析出用于展示的"审批对象"名称（组织/用户/应用统一使用 `name` 字段，任职使用 `userName`/`orgName` 字段），不要求额外返回专门计算好的展示名称字段。目标记录已不存在（如 DELETE 申请审批通过后原记录已被删除）时 `targetSnapshot` SHALL 为空，不因此报错。

`bizType=USER` 的申请查询结果中，`requestPayload.positions`（新增/提交的任职记录数组）里每条记录 SHALL 携带其所属组织的名称（`orgName`），不要求前端仅凭 `orgId` 自行推断；对应组织已不存在时该条记录的 `orgName` 可为空，不因此报错。

#### Scenario: 查询我的申请
- **WHEN** 当前登录用户调用"我的申请"查询接口
- **THEN** 系统返回当前用户作为提交人的全部申请分页列表，按提交时间降序排列，每条记录携带当前所处审批级别名称与关联的流程实例 id

#### Scenario: 查询待我审批仅返回当前节点命中的申请
- **WHEN** 拥有 `ApprovalManagement:request:approve` 权限点的用户调用"待我审批"查询接口，系统中存在两条待审批申请，一条当前节点将该用户解析为候选人，另一条当前节点的候选人不包含该用户
- **THEN** 返回结果仅包含前一条申请，不包含后一条

#### Scenario: 无审批权限调用待我审批被拒绝
- **WHEN** 不拥有 `ApprovalManagement:request:approve` 权限点的用户调用"待我审批"查询接口
- **THEN** 系统拒绝该次调用，返回无权限错误

#### Scenario: 更新类申请查询结果包含新旧对照
- **WHEN** 查询一条 `operationType=UPDATE` 的申请详情
- **THEN** 返回结果同时包含目标记录当前的字段值与 `requestPayload` 中提交的新字段值

#### Scenario: 启用/停用/删除类申请查询结果包含目标记录当前值
- **WHEN** 查询一条 `operationType=ENABLE`、`DISABLE` 或 `DELETE` 的申请详情
- **THEN** 返回结果包含目标记录当前值（`targetSnapshot`），供前端据此展示审批对象名称，不再为空

#### Scenario: 目标记录已被删除时目标值为空但不报错
- **WHEN** 一条已通过的 `DELETE` 申请，其目标记录已经被物理删除，此后再次查询该申请详情
- **THEN** 接口正常返回，`targetSnapshot` 为空，不抛出异常或返回错误状态码

#### Scenario: 用户新增申请的任职记录携带组织名称
- **WHEN** 查询一条 `bizType=USER`、`operationType=CREATE` 的申请详情，其 `requestPayload.positions` 包含至少一条任职记录
- **THEN** 返回结果中该任职记录携带其 `orgId` 对应的组织名称 `orgName`，前端可直接展示，不需要仅凭 `orgId` 数字辨认所属组织

#### Scenario: 任职记录引用的组织已不存在时该条记录组织名称为空
- **WHEN** `requestPayload.positions` 中某条任职记录的 `orgId` 对应的组织已被删除
- **THEN** 该条记录的 `orgName` 为空，接口仍正常返回，不因单条组织查不到而影响其余记录或报错
