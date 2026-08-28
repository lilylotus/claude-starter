## Context

- 组织/用户/任职/应用四个模块目前的写接口（新增/更新/启用/停用/删除）结构高度一致：Controller 只做参数接收 + `@Valid` 触发校验 + 调用 Service；Service 内部完成业务校验（唯一性、管辖组织范围、父子关系约束等）与写库，写库动作内嵌操作日志记录调用（`operation-log-management` 能力）。四个模块状态常量语义完全一致：`2000`=启用、`3000`=停用、`-1000`=已删除。
- `org-scope-data-permission` 提供 `OrgScopeService.isOrgIdAllowed(userId, orgId)`：四个模块的写接口目前都在 Service 层用它做"目标组织/所属组织是否在当前用户管辖范围内"的前置校验，校验不通过时按各自约定返回"资源不存在"或直接拒绝。
- `backend/build.gradle` 已经声明 Flowable 7.2.0 的 Spring Boot Starter 系列依赖（`flowable-spring-boot-starter`、`-process`、`-modeler`、`-idm`、`-rest`），但仓库内没有任何 Flowable 相关 Java 代码、BPMN 文件或数据库表，是完全空白的起点。
- 用户已经确认的核心决策（见对话中的澄清问题）：先审批后生效（不修改真实业务数据、审批通过才落库）；固定审批权限点，不依赖组织架构或指定审批人；单级审批（MVP，不做多级顺序审批）；批量导入不接入审批，维持直接生效；"注销"等价于现有的逻辑删除，四个对象 × 五种操作共 20 种组合全部接入审批；四个模块现有接口直接改变语义（不新增一套并行接口）；按业务对象类型（`bizType`）各自独立配置是否开启审批（默认关闭，需管理员手动开启），新建一个系统设置页面管理该开关（见 Decision 9）。

## Goals / Non-Goals

**Goals:**
- 组织、用户、任职、应用的新增/编辑/启用/停用/删除全部改为"提交审批、审批通过后生效"，拒绝时业务数据不受影响。
- 用 Flowable 驱动申请的状态流转（发起流程实例、完成用户任务），为将来从单级审批演进到多级审批预留空间（新增/调整 BPMN 流程定义即可，不需要重写审批申请的数据模型或四个业务模块的接入方式）。
- 审批通过后复用四个模块既有的创建/更新/状态切换/删除 Service 方法，不重新实现其中的业务规则（唯一性、父子关系、任职记录整体同步等），最大程度降低新缺陷引入的风险。
- 提供按 `bizType` 独立配置的审批开关（默认关闭），关闭时四个模块的写接口行为与本 change 之前完全一致（直接生效），不强制所有环境/所有对象类型都必须走审批；需要审批的对象类型由管理员在"审批设置"页面手动开启。

**Non-Goals:**
- 不实现多级/会签审批（预留架构空间，不在本次范围内）。
- 不改造批量导入（`excel-import-export`）、组织/任职/应用列表查询的管辖组织范围收紧（`org-scope-data-permission`）本身的实现。
- 不为角色、权限点、管理员、菜单、字典等其他资源接入审批。
- 不引入"审批人指定""组织负责人审批链"等更复杂的审批人路由机制。
- 不做审批超时自动处理（超时提醒、自动通过/驳回）。

## Decisions

### 1. 审批申请统一数据模型：一张表承载四类对象 × 五种操作
新增 `tab_approval_request`：
- `id`、`biz_type`（ORG/USER/POSITION/APP，复用 `formfield.constant.FormFieldBizType` 同一套常量）、`operation_type`（CREATE/UPDATE/ENABLE/DISABLE/DELETE）
- `target_id`（可空：CREATE 提交时目标记录尚不存在，为空；UPDATE/ENABLE/DISABLE/DELETE 必填）
- `result_target_id`（可空，仅 CREATE 审批通过后回填，记录实际创建出的记录 id，供审批历史追溯"这条申请最终对应哪条业务记录"）
- `request_payload`（`TEXT`，JSON 序列化的原始请求体：CREATE/UPDATE 存完整的 `XxxCreateRequest`/`XxxUpdateRequest`；ENABLE/DISABLE/DELETE 不需要额外字段，留空）
- `status`（`ApprovalRequestStatus`：`1000`=待审批、`2000`=已通过、`3000`=已拒绝、`4000`=已撤回；这套编码与业务实体的 `2000`/`3000`/`-1000` 语义不同，是独立的申请生命周期，不复用业务实体状态常量避免混淆）
- `approver_id`（可空，处理后回填）、`approve_time`（可空）、`opinion`（审批意见，拒绝时必填，通过时可选）
- `flowable_process_instance_id`、`flowable_task_id`（关联 Flowable 运行时数据，供追溯与后续多级审批演进时定位当前任务节点）
- `create_by`/`create_time`/`update_by`/`update_time`（提交人即 `create_by`，处理人即最后一次 `update_by`，符合仓库"所有表必须有默认创建人/创建时间/更新人/更新时间"的约定）

**备选方案**：为四个业务对象各自建一张审批表（`tab_org_approval_request`/`tab_user_approval_request`/...）。未采用：四类对象的审批生命周期（提交/通过/拒绝/撤回）完全一致，字段结构也完全一致，拆四张表只会带来四份几乎相同的 Mapper/Service 代码，"我的申请"/"待我审批"列表还需要 UNION 四张表才能统一展示，得不偿失。

### 2. 提交时只做结构校验与管辖范围校验，业务规则校验延后到审批通过时执行
提交审批申请时，系统 SHALL 执行：请求体的 `@Valid` 结构校验（必填、格式、长度等）；管辖组织范围校验（复用 `OrgScopeService.isOrgIdAllowed`，与四个模块现有写接口的校验逻辑完全一致）。**不 SHALL** 在提交时执行依赖当前数据库状态的业务规则校验（如编码唯一性、父组织是否存在未删除子组织等）——这类校验推迟到审批通过的那一刻，由审批服务直接调用对应模块既有的创建/更新/状态切换/删除 Service 方法触发，该方法内部已经包含这些校验，不需要重新实现一遍。

这带来一个必须显式处理的后果：审批通过时，原有的业务规则校验可能失败（比如提交时编码不重复，但审批通过前另一条申请已经抢先审批通过并占用了相同编码）。此时审批操作本身 SHALL 失败并返回具体错误，该申请 SHALL 保持"待审批"状态不变（不自动转为"已拒绝"），由审批人根据报错信息决定是驳回该申请、还是等提交人协调后重新审批。

**备选方案**：提交时把全部业务规则都校验一遍（包括唯一性），通过审批时只做"乐观"执行，失败即回退整个流程。未采用：业务规则校验的结果在"提交"到"审批"之间的时间窗口内可能失效（另一条申请先审批通过），在提交时校验通过不能保证审批时依然成立，重复校验没有意义，还会让人误以为"提交时校验过了，审批一定能成功"，掩盖真正需要在审批时再次确认的必要性。

### 3. Flowable 的使用范围：只借助流程引擎做状态机与审计轨迹，审批权限判断仍走项目自有 RBAC
每条审批申请提交时启动一个 Flowable 流程实例（单一通用 BPMN 定义 `approval-process.bpmn20.xml`：开始事件 → 用户任务"审批" → 排他网关按 `approved` 流程变量分流 → "已通过"/"已拒绝"两个结束事件），流程变量携带 `requestId`；审批人调用通过/拒绝接口时，系统按 `flowable_task_id` 认领并完成该用户任务（`TaskService.claim` + `TaskService.complete`），同时更新 `tab_approval_request` 的 `status`/`approver_id`/`opinion`。

**是否使用 Flowable 自带的候选组/身份服务（IDM）机制来控制"谁能看到并处理这个任务"？不使用。** 谁能调用审批/拒绝接口，由项目自有的 `ApprovalManagement:request:approve` 权限点判断（与其余接口的权限校验方式完全一致，走 `IdentityAuthFilter` 的 `menu` 请求头机制），不依赖 Flowable IDM 的用户/组表、也不使用 BPMN 里的 `candidateGroups`/`candidateUsers` 做访问控制。用户任务节点在 BPMN 里不设置候选组，接口层面直接对拥有权限点的调用者放行认领+完成。

**备选方案**：把项目自有的用户/角色同步进 Flowable IDM 表，用 BPMN 候选组做访问控制。未采用：项目已经有一套完整的自有 RBAC（`tab_user`/`tab_role`/`tab_permission`），维护两套身份体系的同步一致性（用户新增/删除、角色变更都要双写）成本和风险远大于收益，尤其是当前只需要"持有某个权限点的任意用户都能审批"这种最简单的路由规则，用自有权限点判断已经足够，且与项目其余接口的鉴权方式保持一致，不引入认知负担。

### 4. Flowable 数据库表与配置
`application.yml` 新增 `flowable.database-schema-update: true`（首次启动自动建表，与项目当前"表结构用 Flyway 迁移脚本管理"的既有约定不同——Flowable 自身的 `ACT_*` 系列表由 Flowable 官方维护版本演进，不适合也不需要纳入本项目的 Flyway 脚本管理，这是 Flowable 官方推荐的标准接入方式）；关闭 Flowable 自带的 REST API 暴露路径映射冲突风险（`flowable-spring-boot-starter-rest` 默认会注册一批 `/process-api/**` 路径，需要确认与本项目现有 `IdentityAuthFilter` 的白名单/鉴权机制不冲突，若冲突则通过配置关闭其自动路由，只保留 Java API 编程调用能力）；关闭 Flowable Modeler UI 的匿名访问（若默认启用匿名，需要收敛到仅本机/开发环境可用，避免生产环境暴露未鉴权的流程建模界面）。

### 5. 审批通过后的落库方式：直接调用既有 Service 方法，不重复实现业务逻辑
审批服务针对 `(bizType, operationType)` 的每种组合，反序列化 `request_payload` 为对应的 `XxxCreateRequest`/`XxxUpdateRequest`，直接调用该模块现有的 `OrgService.create(request)`/`update(id, request)`/`enable(id)`/`disable(id)`/`delete(id)` 等方法（`UserService`/`PositionService`/`AppService` 同理）。这些方法内部已有的唯一性校验、管辖范围二次确认（若校验方法本身依赖 `CurrentUserContext.getUserId()`，需要在审批线程上下文里设置为**提交人**而非**审批人**的 id，见 Decision 6）、操作日志记录（`operation-log-management`）全部原样复用，审批服务本身不重新实现任何一条业务规则。

对于用户模块的"更新用户"（内嵌任职记录整体同步 diff 逻辑），审批通过后同样是完整调用 `UserService.update(id, request)`，`request.positions` 数组就是提交时保存的完整快照，diff 同步逻辑不需要改动。

### 6. 操作日志与管辖范围的执行主体：以提交人身份执行，而非审批人身份
审批通过后落库调用既有 Service 方法时，`CurrentUserContext` 的当前用户 SHALL 设置为该申请的**提交人**（`create_by`），而不是当前正在点击"通过"按钮的**审批人**。理由：
- 操作日志（`operation-log-management`）记录的"操作人"应该反映"谁发起了这个业务变更"，审批人只是走完了流程的批准动作，不是数据变更的业务责任人；沿用现有 `createBy`/`updateBy` 语义，审批通过时执行的创建/更新，其审计字段应归属提交人。
- 管辖组织范围校验（如果 Service 内部方法本身还会再次校验）应该基于提交人的管辖范围，而不是审批人的管辖范围——审批人可能拥有比提交人更大的管辖范围（比如审批权限点通常授予管理层账号），如果按审批人身份重新校验，会出现"提交人本没有权限操作这个组织，但审批人的管辖范围覆盖了它，于是审批通过后侥幸执行成功"的越权漏洞。审批服务在调用既有 Service 方法前，SHALL 用提交人 id 重新执行一次管辖范围校验（与 Decision 2 提交时的校验一致），确保提交人当前（审批发生的这一刻）仍然在管辖范围内——提交之后、审批之前，提交人的管辖范围配置可能已经变化。

单独记录一条"审批通过/拒绝"本身的操作日志（操作人=审批人，被操作对象=该审批申请），与上面"业务数据变更本身的操作日志（操作人=提交人）"是两条独立的日志，不互相替代。

### 7. 前端交互：提交后不立即展示新数据，改为"已提交，等待审批"提示
四个管理页面的新增/编辑/启用/停用/删除操作，调用对应接口成功后 SHALL 展示"已提交审批，请等待审批通过后生效"类提示，不再假设接口返回的是最终业务数据、不再乐观更新本地列表（列表数据在审批通过前不会变化，审批通过后管理员需要手动刷新或者被动等待下次查询才能看到最新数据——不在本次范围内做"审批状态实时推送"）。新增页面（审批相关）：
- "我的申请"：当前登录用户提交的全部申请，可按状态筛选，可撤回"待审批"状态的申请。
- "待我审批"：仅 `ApprovalManagement:request:approve` 权限点持有者可见，展示全部"待审批"状态的申请（不区分提交人），可查看申请详情（`bizType`/`operationType`/提交人/提交时间/`request_payload` 的可读化展示）、批准、拒绝（拒绝需填写意见）。

### 8. `request_payload` 的可读化展示：复用现有的表单字段定义元数据
"待我审批"详情页需要把 `request_payload` 的 JSON 内容渲染成人类可读的字段列表（而不是原始 JSON），复用 `form-field-definition-management` 能力已有的渲染元数据接口（`GET /api/form-fields/render-schema?bizType=`）得到字段标识到展示名称的映射，与四个管理页面新增/编辑表单已经在用的动态渲染是同一套元数据来源，不重新维护一份字段展示名映射。UPDATE 类型的申请详情 SHALL 同时展示目标记录的当前值与申请中的新值（新旧对照），CREATE 类型只展示申请中的新值（无旧值可对比）。

### 9. 审批开关：按 bizType 独立配置，控制提交接口是走审批还是直接生效
新增 `tab_approval_switch` 表，为组织（ORG）、用户（USER）、任职（POSITION）、应用（APP）各预置一条开关记录（`biz_type` 唯一，`enabled` 布尔），迁移时默认全部 `enabled=false`（系统初始化时不强制任何对象类型走审批，管理员需要在"审批设置"页面按需手动开启某个 `bizType` 的审批）。四个模块的新增/更新/启用/停用/删除接口被调用时，SHALL 先查询该 `bizType` 当前的开关状态：
- **开关开启**：走"提交审批申请"逻辑（Decision 1、2），接口返回审批申请信息。
- **开关关闭**：SHALL NOT 生成审批申请、SHALL NOT 启动 Flowable 流程实例，直接调用该模块既有的创建/更新/状态切换/删除方法立即执行并返回业务数据，行为与本 change 之前完全一致。

**分流职责边界**：审批开关判断 SHALL 位于组织、用户、任职、应用各自的 Controller 最外层。开关关闭时，Controller 直接调用本模块原有 Service，并将结果包装为 `WriteOperationResultVO.applied(...)`；开关开启时，Controller 才调用 `ApprovalRequestService` 创建申请。`ApprovalRequestService.submit` 只负责已启用审批场景，不得在其内部通过通用 `executeWrite` 分发关闭审批时的原业务写操作。审批通过阶段允许在审批服务内部按 `(bizType, operationType)` 调用既有业务 Service，这是在途申请真正生效所必需的职责。通用 `POST /api/approval-requests` 在对应开关关闭时拒绝提交并提示调用原业务接口，避免绕过上述业务入口分流。

**统一响应结构**：由于同一个接口在开关不同状态下返回的内容形态不同（审批申请信息 vs 业务数据本身），四个模块的写接口 SHALL 统一返回一个"写操作结果"包装对象（如 `WriteOperationResultVO<T>`），包含：`approvalEnabled`（布尔，本次调用时该 `bizType` 的开关状态）、`approvalRequest`（开关开启时非空，审批申请信息，含申请 id/状态）、`data`（开关关闭时非空，创建/更新后的业务数据，类型为 `T`，如 `OrgVO`）。前端根据 `approvalEnabled` 决定展示"已提交审批"提示还是直接展示 `data`，不需要额外猜测响应形状。

**权限点**：查看审批开关当前状态需要 `ApprovalManagement:switch:view`；修改开关需要 `ApprovalManagement:switch:edit`；这两个权限点与处理审批任务的 `ApprovalManagement:request:approve` 相互独立——一个组织可能希望"运维/系统管理员能开关审批策略"和"业务主管能审批具体申请"是两类不同的人。

**开关状态变更对在途申请的影响**：关闭某个 `bizType` 的开关，SHALL NOT 影响该 `bizType` 下已经存在的"待审批"申请——这些申请仍然可以被正常批准、拒绝或撤回（走 Decision 1 描述的既有流程），只是关闭之后新提交的写请求不再生成新的审批申请。同理，重新开启开关也不会让关闭期间已经直接生效的操作"回滚"为待审批状态。

**备选方案**：把开关做成全局唯一的一个开关（不区分 `bizType`）。未采用：用户明确要求按业务对象类型分别配置，不同对象类型引入审批的紧迫程度可能不同（比如组织架构变更影响面大，可能更早需要审批；应用信息变更影响面相对小，可能希望先维持直接生效观察一段时间）。

**备选方案**：开关关闭时依然生成一条"已通过"状态的审批记录用于留痕。未采用：这会让"审批申请"这个概念泛化成"所有写操作的通用日志"，与 `operation-log-management` 已有的操作日志职责重叠，且没有实际的审批动作发生却生成一条"已通过"记录容易造成误导（看起来像是被谁批准了，其实是系统自动跳过）；开关关闭期间的写操作留痕，复用 `operation-log-management` 现有能力即可，不需要审批模块重复记录。

## Risks / Trade-offs

- [提交与审批之间的时间窗口内，多条互相冲突的申请（如两条申请使用相同编码）都能提交成功，审批时后处理的一条会失败] → Decision 2 已经把这种情况定义为"审批操作报错、申请保持待审批"而非静默失败，审批人能看到明确报错并决定驳回，不会产生数据不一致。
- [Flowable 引入的 `ACT_*` 表由 Flowable 自动建表管理，脱离本项目 Flyway 迁移脚本的版本控制] → 可接受：这是 Flowable 官方标准接入方式，且这些表的 schema 演进由 Flowable 自身版本负责，本项目不需要也不应该手工管理其结构；后续升级 Flowable 版本时需要关注其官方迁移指南。
- [审批通过时以提交人身份重新执行管辖范围校验，如果提交人在提交后被调整了管辖范围导致审批失败] → 符合预期：这正是"以提交人身份而非审批人身份执行"要防止的越权场景的另一面，属于正确行为而非缺陷，审批人会看到明确的失败原因。
- [单级审批下，审批权限点持有者数量较多时，可能出现多人同时尝试处理同一条申请的竞态] → 用数据库层面的乐观锁（校验 `status` 仍为"待审批"才允许更新，否则拒绝并提示"该申请已被处理"）规避，不依赖 Flowable 任务认领的分布式锁语义。
- [批量导入与本次审批流程是两条完全独立的写入路径，管理员可能通过批量导入绕开审批] → 已经是用户明确确认的既定范围（Non-Goal），不在本次处理，如后续需要收紧，应作为独立 change 处理。
- [持有 `ApprovalManagement:switch:edit` 权限的用户可以随时关闭某个 `bizType` 的审批，实质上是审批流程的"总开关旁路"] → 符合预期：这是用户明确要求的能力（可以按需关闭），权限点本身已经收紧了"谁能关闭"，不是缺陷；如需要审计"谁在什么时候关闭过审批"，可以复用 `operation-log-management` 对开关变更本身的操作记录（该表的增删改同样是一次"写操作"，走既有的操作日志机制即可，不需要审批模块额外实现）。

## Migration Plan

1. 新增 Flyway 迁移脚本：`tab_approval_request` 建表；`tab_approval_switch` 建表并为四个 `bizType` 预置默认**关闭**（`enabled=false`）的开关记录。`V8__add_master_data_approval_workflow.sql` 已在部分环境执行过（`V9__fix_approval_pending_menu_resource.sql` 的注释已确认此约束），不可直接修改其内容；本次默认值调整改用新增的 `V10__set_approval_switch_default_disabled.sql`，将 `tab_approval_switch` 表的 `enabled` 列默认值与四条既有记录统一更新为 `0`，对已执行过 V8 的环境同样生效。
2. 新增 `approval-process.bpmn20.xml` 流程定义，随应用启动由 Flowable 自动部署（`classpath*:processes/*.bpmn20.xml`）。
3. 后端新增 `approval` 包；四个模块的 Controller 在最外层按开关状态分流：关闭时直接调用各自原有 Service，开启时提交审批申请，统一返回 `WriteOperationResultVO`（Decision 9）；审批申请 Service 不承接关闭审批时的原业务写操作。各自 Service 可直接复用既有的 `create`/`update`/`enable`/`disable`/`delete` 方法，审批通过时由审批服务在正确设置 `CurrentUserContext` 后调用。
4. 新增四个权限点（`ApprovalManagement:request:view`/`request:approve`/`switch:view`/`switch:edit`）并同步 `权限资源.txt`。
5. 前端新增审批相关页面（我的申请、待我审批、审批设置）与四个管理页面按 `approvalEnabled` 分流的交互调整。
6. 默认开关为"关闭"：系统初始化后四个模块的写接口行为与本 change 之前完全一致（直接生效），管理员需要在"审批设置"页面按需手动开启某个 `bizType` 才会走"20 种组合全覆盖"的审批流程。即便默认关闭，这仍然是一次响应结构上的**BREAKING** 变更（四个模块现有接口的响应体统一包装为 `WriteOperationResultVO`，不再是裸的 `OrgVO`/`UserVO` 等），前端需要同步适配这一层包装，不做进一步的向后兼容处理。
