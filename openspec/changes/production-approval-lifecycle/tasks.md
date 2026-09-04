## 1. 前置基线与契约确认

- [x] 1.1 重读工作树：`workflow-approval-engine` 已于本次会话归档（详见
      `openspec/changes/archive/2026-09-04-workflow-approval-engine`），其代码（引擎、
      设计器、四类业务接入）为真实已提交状态，本 change 在此基础上新增而非重做。
- [x] 1.2 前置设计器闭环（草稿/发布/下线/版本历史/权限）已随 `workflow-approval-engine`
      完成并有真实引擎测试佐证，复用不重做。
- [x] 1.3 身份角色/岗位数据来源：沿用 design.md"无数据来源的规则保持禁用"默认——
      `APP_ADMIN`/`FORM_REFERENCE_PERSON`/`POSITION` 三类在 `ProcessModelDslV2Validator`
      直接拒绝发布，不假装已有数据源；发布审核人/异常处理负责人等组织性决策未落定
      （非本轮编码可解决，按 design.md Open Questions 保留，双人发布约束已在代码层面
      通过"编辑者 ≠ 审核者"强制，具体人员归属留待运营决策）；目标数据库版本经查询确认为
      MySQL 8.0.46（远程共享开发库），但所有新增 SQL 仍按项目既定约定写成 MySQL 5.7
      兼容语法。首轮能力范围收敛为本 change 第 1-4 节（数据模型、DSL v2 编译器、设计/
      发布/绑定接口），第 5-10 节（表单身份安全、复杂任务运行时、可靠执行通知、前端
      体验、运维、验收上线）本轮不实现。
- [x] 1.4 `master-data-approval-workflow` 主 spec 语义（多级审批、撤回策略、审批查询）
      与本 change 待同步的 delta 无冲突：本轮编码未修改任何已归档 spec 涉及的运行时
      行为，新增能力均为可选的 DSL v2/绑定路径，v1 流程行为逐一验证未受影响（见 2/3
      节测试）。
- [x] 1.5 已确认 Flowable 7.2.0 通过 `flowable-spring-boot-starter` 接入，未新增/变更
      `build.gradle` 依赖；未对外暴露通用 Flowable REST。

## 2. 数据模型与兼容迁移

- [x] 2.1 新增 `V11__add_production_approval_lifecycle_tables.sql`：为
      `tab_wf_process_model` 增加 `draft_revision`/`draft_status`/`enabled`，为
      `tab_wf_process_definition` 增加 `schema_version`/`compiler_version`/
      `model_digest`/`xml_snapshot`/`xml_digest`/`node_mapping_json`/
      `rule_snapshot_json`/`form_version_id`。
- [x] 2.2 同一脚本建立 `tab_wf_release_review`（发布审核）、`tab_wf_form_version`
      （表单版本）、`tab_wf_process_binding`（业务绑定，唯一维度
      `biz_type+operation_type+scope_type+scope_id`）三张新表及审计字段。
- [x] 2.3 同一脚本建立 `tab_wf_node_run`（节点轮次），扩展
      `tab_wf_process_instance`（binding/表单版本/身份快照/outcome/exception_code/
      revision）、`tab_wf_approval_task`（node_run_id/owner_id/delegation_status/
      revision/cancel_reason/due_time）、`tab_wf_approval_task_candidate`
      （resolve_basis）、`tab_approval_request`（execution_mode/execution_status/
      base_revision/previous_request_id）；另建 `tab_wf_business_lock`。新增
      `V12__add_node_assignee_rule_fallback_role.sql` 为
      `tab_wf_node_assignee_rule` 补 `fallback_role_code`。真实迁移已在远程开发库执行
      并通过 `./gradlew test` 验证 Flyway 迁移与既有数据兼容。
- [x] 2.4 同一 V11 脚本建立 `tab_wf_outbox_event`（含 `status+next_retry_time+id`
      到期扫描索引）、`tab_wf_event_consume`、`tab_wf_business_execution`、
      `tab_wf_cc_record`、`tab_wf_notification` 五张表；本轮只建表，Outbox 生产/消费
      运行时逻辑属于第 7 节，不在本轮范围内实现。
- [x] 2.5 全部新增列均可空或带默认值：历史 `tab_wf_process_definition.schema_version`
      默认 1（v1 DSL 不变），历史 `tab_approval_request.execution_mode` 默认
      `LEGACY_SYNC`，通过默认值自然回填，不需要额外 UPDATE 语句；已用
      `./gradlew test` 触发 Flyway 迁移并确认既有 workflow-approval-engine 全部测试
      （58 项已完成任务对应用例）迁移后依然全部通过。

## 3. DSL与编译器

- [x] 3.1 新增 `cn.nihility.rbac.workflow.dslv2` 包：`ProcessModelDslV2` 顶层结构含
      `schemaVersion` 字段，与 v1 `ProcessModelDsl`/`WorkflowModelCompiler` 完全独立、
      互不修改，v1 老定义不受影响；前端 TS 类型未在本轮实现（前端体验属于第 8 节）。
- [x] 3.2 `ProcessModelDslV2Validator` 实现节点 id 唯一/边引用存在/可达性/唯一默认边+
      priority/并行块配对（split.joinNodeId 与 join.splitNodeId 互相一致、块内作用域
      通过 BFS 计算、禁止交叉重叠仅允许不相交或完全嵌套）等结构校验，节点数量/条件项
      数量设上限；错误信息携带节点/边 id 定位。
- [x] 3.3 `ConditionAstDsl`/`ConditionItemDsl`（`EQ/NE/GT/GE/LT/LE/IN/IS_NULL`）+
      `ConditionAstCompiler`（编译为受限 UEL，`IN` 展开为 `==` 的 `||` 链，不接受自由
      表达式字符串）+ `ConditionAstEvaluator`（纯 Java 解释执行，供后续试运行复用，
      数值统一按 BigDecimal 比较避免浮点误差）。
- [x] 3.4 `WorkflowModelCompilerV2` 实现 `PARALLEL_SPLIT`/`PARALLEL_JOIN` 配对
      `ParallelGateway`、`vote.execution=SEQUENTIAL/PARALLEL` 控制
      `loopCharacteristics.setSequential`、`END` 节点 `outcome=APPROVED` 为普通结束
      事件/`REJECTED` 附加根流程范围 `TerminateEventDefinition`；多实例变量沿用 v1
      的节点级 `approvers_<nodeId>` 命名，天然按节点隔离。真实引擎集成测试覆盖并行
      分叉/汇合两分支审批+抄送+正常完成、条件分支路由到 REJECTED 终止结束事件
      （`WorkflowModelCompilerV2IntegrationTest`）。
- [x] 3.5 空审批人默认 `BLOCK`（新枚举值，`EmptyAssigneeStrategy`/`ResolvedAssignees.Kind`
      新增而非替换，v1 从不产生该取值）：单人节点任务照常创建但不设置
      assignee/candidates，流程实例标记 `exception_code=ASSIGNEE_EMPTY`；会签节点用
      哨兵候选人（保留字用户 id `0`）避免 Flowable 对空集合多实例节点"立即自动完成"的
      已知行为（不是"内部等待分配节点"这种独立 BPMN 结构，而是复用现有 UserTask/MI
      结构 + 哨兵占位，代价更小、复用既有监听器基础设施更多）。新增
      `WorkflowV2ReassignmentService.reassign(...)` 做运维恢复：单人节点直接补写
      assignee/candidates；会签节点用 `runtimeService.addMultiInstanceExecution` 逐个
      新增真实候选人分支后 `deleteMultiInstanceExecution` 删除哨兵分支，N/K 按补充后
      的真实候选人数量计算。恢复操作经 `IdempotencyService` 保护。真实引擎集成测试
      验证：零候选人 MI 确实不会自动通过（哨兵产生恰好 1 个不可认领任务，流程停在
      `RUNNING`+`ASSIGNEE_EMPTY`，不会被误判为已完成）、同一幂等键重复调用 reassign
      两次结果一致（幂等）。`FALLBACK_ROLE` 策略已实现（复用 `RoleAssigneeResolver`），
      兜底角色仍解析为空时按 `BLOCK` 处理。
- [x] 3.6 `CC` 节点编译为挂 `CcServiceTaskDelegate`（Flowable 字段注入
      recipientType/recipientValue，复用 `AssigneeResolverRegistry` 解析接收人，写入
      `tab_wf_cc_record` + 关联的 `tab_wf_node_run`）的 `ServiceTask`，同步执行、不
      阻塞流程；`AUTO` 节点编译为挂 `AutoServiceTaskDelegate` 的 `ServiceTask`，但
      `AutoActionRegistry` 首轮不注册任何 `actionCode`，`ProcessModelDslV2Validator`
      对任何引用未注册 actionCode 的模型一律拒绝发布——"禁止不支持的动作组合"通过
      "首轮白名单为空"这一更严格的方式满足，真正的 AUTO 动作执行留给第 7 节。
      **未实现**：超时/非中断提醒节点的编译（`TimeoutConfigDsl` 已在 DTO 层定义，
      BPMN 边界定时事件编译与提醒调度属于第 9 节"超时与运行维护"，本轮不在范围内）。
- [x] 3.7 `WorkflowModelCompilerV2.compile()` 末尾复用 Flowable
      `ProcessValidatorFactory` 默认校验器做二次校验（与 v1 同一模式）；发布产物持久化
      （DSL 快照/XML/节点映射/表单/编译器摘要落库到 2.1 新增列）留给第 4 节的发布接口
      实现，本节只产出 `CompiledProcessV2` 记录（bpmnModel + assigneeRules +
      nodeMapping），尚未接入实际 publish 流程写库——即 tasks.md 4.4 完成前，
      `xml_snapshot`/`model_digest` 等列虽已建好但还没有代码路径写入。合法 DSL 覆盖
      并行/会签/条件/抄送场景（见 3.4/3.5 测试）；恶意 DSL（自由表达式注入、越界
      priority、未注册 actionCode 等）覆盖在 `ProcessModelDslV2Validator` 自身
      校验逻辑内联断言，未单独补一个"恶意 DSL 大合集"测试类。

## 4. 设计、试运行、发布与绑定接口

- [x] 4.1 模型新建/列表/详情/复制接口在 `workflow-approval-engine` change 已落地，本轮
      复用不重做；新增草稿 revision 乐观锁：`saveDraft(modelId, modelJson,
      expectedRevision)`（`WorkflowProcessModelService`/`SaveDraftRequest` 均已扩展该
      可选参数，`null` 时不做冲突检测以兼容历史调用方），每次保存草稿
      `draft_revision` 自增，`expectedRevision` 与数据库当前值不一致时拒绝保存并在
      异常消息中携带服务器最新修订号；保存草稿不触碰 `tab_wf_process_definition`，
      不影响已发布版本，行为与既有单测/集成测试一致（回归验证见 `./gradlew build`）。
- [x] 4.2 只做 design.md 第4节"试运行分两层"中的第一层——快速预演（静态路径/人员解析
      解释，不接触真实引擎实例、不产生任何真实任务/流程数据）。新增
      `POST /api/workflow/process-models/{id}/simulations`（`WorkflowSimulationController`
      + `WorkflowSimulationService`，`cn.nihility.rbac.workflow.dslv2.simulation` 包），
      入参 `SimulationRequest`（草稿或指定 `definitionId` 的已发布快照 + 模拟表单值
      `formValues` + 模拟申请人 `applicantId`/`applicantOrgId`），服务端沿 DSL v2 图从
      START 出发做一次访问-一次的 BFS 遍历：条件节点用 `ConditionAstEvaluator`（3.3 已
      完成）按 `priority` 从小到大求值，命中分支展开、其余分支（含存在命中时未展开的
      默认分支）计入未覆盖分支列表；审批节点独立实现一套与运行时
      `NodeAssigneeResolutionService` 语义一致的解析步骤（来源解析 → 自审排除 →
      `BLOCK`/`FALLBACK_ROLE` 空人兜底），复用 `AssigneeResolverRegistry` 但不创建
      任务，对 `APP_ADMIN`/`FORM_REFERENCE_PERSON` 等 v1 无对应枚举值的来源类型容错为
      "无数据来源恒为空"而不是抛异常中断预演；`PARALLEL_SPLIT` 两条分支均入队展开，
      `PARALLEL_JOIN` 只在首次到达时展开一次下游（避免重复计入命中路径）。输出
      `SimulationResultVO`：命中路径（节点 id 有序列表，遍历顺序而非严格执行时间线）、
      每个审批节点的候选人列表+解析依据说明+`emptyAssignee` 显式标注、未覆盖分支列表
      （含 edgeId/sourceNodeId/targetNodeId/reason），`mode` 恒为 `"QUICK_PREVIEW"`。
      权限复用 `WorkflowDesign:model:edit`（见 权限资源.txt 说明），未新增权限点。真实
      单元测试 `WorkflowSimulationServiceTest` 覆盖条件命中/默认分支回退/未覆盖分支
      记录、审批人解析（含空审批人显式标注）、并行分叉两条分支均展开且汇合只展开一次、
      拒绝非 `schemaVersion=2` 草稿五个场景。**明确不做**："独立测试环境真实试运行"
      （需要与生产库隔离的测试引擎/数据库基础设施，属于部署环境层面工作，留待后续
      批次）；不产生 `tab_wf_process_instance`/`tab_wf_approval_task` 等真实运行数据；
      不解析 `CC` 节点抄送接收人（未在本轮明确要求范围内，节点仅作为透传节点参与路径
      遍历）。
- [x] 4.3 新增 `tab_wf_release_review` 对应的 `ReleaseReviewEntity`/`ReleaseReviewMapper`
      + `WorkflowReleaseReviewService`（`submitForReview`/`decide`/
      `requireApprovedForCurrentRevision`）+ `WorkflowReleaseReviewController`
      （`POST .../reviews`、`POST /api/workflow/process-model-reviews/{id}/decisions`）。
      审核者与编辑者不能是同一人（真实数据库集成测试覆盖）；草稿在提交审核后又被修改
      （`draft_revision`/摘要不一致）时审核请求自动判定失效并拒绝决策，需要重新提交
      （真实数据库集成测试覆盖）。**本轮补齐**：`requireApprovedForCurrentRevision`
      已接入 `WorkflowProcessModelServiceImpl.publish()` 作为强制前置门禁，仅对
      `schemaVersion=2`（`publishV2` 路径，通过 `isSchemaV2(model.getModelJson())`
      探测）生效——v1 发布流程本就没有审核概念，本轮不新增，避免改变现有 v1 行为；
      `publishV1` 路径不调用该校验，未通过审核直接抛 `BusinessException`（消息含
      "未通过发布审核"），不执行任何编译/部署，整个方法在校验步骤之前即返回，不产生
      半写状态。真实数据库集成测试新增
      `WorkflowProcessModelServiceV2PublishIntegrationTest#publish_shouldReject_whenV2DraftNotApproved`
      验证未提交/未通过审核时 v2 发布被拒绝，原有
      `publish_shouldPersistV2ArtifactsAndBeRunnable` 相应调整为先
      `submitForReview`+`decide` 通过后再发布。发布幂等沿用
      `workflow-approval-engine` change 已实现的 `IdempotencyService`/
      `X-Request-Id` 机制，本轮未在 publish 接口上重新声明（该机制是通用能力，接入
      publish 接口的具体改动未做，仍是后续工作）。
- [x] 4.4 `WorkflowProcessModelServiceImpl.publish()` 按草稿 JSON 的 `schemaVersion`
      字段动态分派到 `publishV1`（沿用既有实现）或 `publishV2`（新增）：v2 路径编译
      产物落库 `schema_version`/`compiler_version`/`model_digest`/`xml_snapshot`/
      `xml_digest`/`node_mapping_json`/`rule_snapshot_json`，与部署、
      `tab_wf_node_assignee_rule` 批量落库同一事务；部署失败（Flowable
      `ProcessValidator` 校验不通过或引擎异常）整个方法回滚，不产生半写状态（沿用既有
      `@Transactional(rollbackFor = Exception.class)` 边界）。真实集成测试
      `WorkflowProcessModelServiceV2PublishIntegrationTest` 验证发布产物真实落库且可
      驱动真实审批流程。**未做**：模型行级锁——当前实现依赖 MySQL InnoDB 默认隔离级别
      与单条 `updateById`/`insert` 的原子性，未显式 `SELECT ... FOR UPDATE` 锁定
      `tab_wf_process_model` 行防止并发发布产生 `(model_id, version)` 竞争，高并发下
      `nextVersion()` 的"查最大值+1"存在竞态窗口（`workflow-approval-engine` 遗留的
      既有实现方式，本轮沿用未加固）。
- [x] 4.5 新增 `tab_wf_process_binding` 对应的 `ProcessBindingEntity`/
      `ProcessBindingMapper` + `ProcessBindingResolutionService`（确定性解析：精确
      组织 → 最近祖先组织（按 `tab_org.org_path` 由近到远遍历）→ 全局，全局
      `scopeId` 固定哨兵值 0，均未命中或命中未启用绑定则拒绝，不返回默认值）+
      `WorkflowProcessBindingService`（新建/切换版本乐观锁/启停）+
      `WorkflowProcessBindingController`。真实数据库集成测试覆盖精确组织优先、
      祖先回退、全局兜底、未配置拒绝、禁用绑定被跳过五种场景。**本轮补齐**：把
      `ProcessBindingResolutionService` 接进实际提交流程，替换
      `ApprovalProcessServiceImpl.start()` 里硬编码的
      `WorkflowConstants.MASTER_DATA_APPROVAL_PROCESS_CODE`（这正是本次会话早前
      `approval-process-biztype-binding` 提案要修复、后来决定改由本 change 承接的
      那个断链）。实际改动：
      1) `ApprovalProcessService.start(...)`/`ApprovalRequestServiceImpl.submit(...)`
      新增 `operationType` 入参（`submit()` 内已有该值，之前未透传）；
      2) `StartProcessCommand` 新增 `definitionId`/`bindingId`/`bindingRevision`/
      `executionMode` 四个字段（保留 `processCode` 字段仅作展示/日志用途），
      `FlowableWorkflowService.start()` 改为直接按命令携带的 `definitionId`
      （`processDefinitionMapper.selectById`）启动，不再反查
      `processModel.getCurrentDefinitionId()`，`ProcessInstanceEntity.bindingId`/
      `bindingRevision` 随实例落库；
      3) 新增 `ProcessBindingResolutionService.resolveForUpdate(...)`（与既有
      `resolve(...)` 共用私有的三层回退查询逻辑，仅最终 SQL 追加
      `LIMIT 1 FOR UPDATE` 不同，不重复整段回退代码）与一站式
      `resolveForStart(bizType, operationType, orgId)`：加锁解析绑定 → 校验绑定指向的
      `tab_wf_process_definition`（按绑定携带的 `definitionId` 本身而非模型的
      `currentDefinitionId`，因为绑定可能指向历史版本——显式回滚场景）状态为
      `PUBLISHED` → 校验所属 `tab_wf_process_model.enabled`（4.6）→ 拒绝
      `executionMode=RELIABLE_ASYNC`，返回 `ResolvedProcessBinding(binding,
      definition)`；`ApprovalProcessServiceImpl.start()` 新增
      `@Transactional(REQUIRED)`，调用 `resolveForStart` 后直接用其结果构造
      `StartProcessCommand` 并委托 `WorkflowService.start`——三步共享同一个数据库事务
      （`resolveForStart`/`WorkflowService.start` 各自也是 `Propagation.REQUIRED`，
      加入调用方已开启的事务），绑定行锁从解析那一刻一直持有到流程实例真正创建完成，
      与规划文档"下沉到 FlowableWorkflowService.start() 内"的备选方案相比，选择把
      加锁校验放在 `ProcessBindingResolutionService`（更贴近绑定领域，
      `FlowableWorkflowService` 保持通用、不感知绑定概念，供不经绑定发起的历史/引擎
      集成测试直接复用）；
      4) **兼容性种子迁移**：新增 `V13__seed_process_binding_global_fallback.sql`，为
      `ORG`/`USER`/`POSITION`/`APP`（`FormFieldBizType`）×
      `CREATE`/`UPDATE`/`ENABLE`/`DISABLE`/`DELETE`（`ApprovalOperationType`）全部
      20 种组合各插入一条 `scope_type=GLOBAL`、`scope_id=0`、`definition_id=`
      （子查询动态取 `tab_wf_process_model` 中 `process_code='MASTER_DATA_APPROVAL'`
      的 `current_definition_id`，非硬编码）、`execution_mode=LEGACY_SYNC`、
      `enabled=1` 的兜底绑定；`current_definition_id` 为 `NULL` 时 `WHERE` 条件令
      派生结果集为空、优雅跳过插入。已在远程开发库（MySQL 5.7.44）真实执行，确认
      `MASTER_DATA_APPROVAL` 模型当时 `current_definition_id=2`（已发布、
      `PUBLISHED`），实际插入 20 条绑定记录，`ApprovalProcessServiceImplBindingIntegrationTest`
      对 ORG/USER/POSITION/APP 四个模块分别真实调用 `ApprovalProcessService.start(...,
      "CREATE", ...)` 验证均能命中该兜底绑定并成功发起流程；
      5) **执行模式边界**：本轮不实现 `RELIABLE_ASYNC` 的真正可靠执行器（属于第7节），
      若解析命中的绑定 `executionMode=RELIABLE_ASYNC` 则 `resolveForStart` 直接拒绝
      并抛出携带"可靠异步执行尚未实现，请使用 LEGACY_SYNC 模式绑定"消息的
      `BusinessException`，不静默按 `LEGACY_SYNC` 语义处理，避免假装已支持；种子绑定
      固定为 `LEGACY_SYNC` 即不受此限制。真实数据库集成测试
      `ProcessBindingResolutionServiceTest` 新增 `resolveForStart_*` 四个用例覆盖
      成功路径、`RELIABLE_ASYNC` 拒绝、模型 `enabled=false` 拒绝、绑定指向的定义未
      `PUBLISHED` 拒绝。**因此产生的连带测试改动**：`StartProcessCommand` 构造参数
      增至 12 个，`ApprovalProcessServiceImplTest`/`RbacApplicationTests`/
      `ApprovalRequestServiceImplTest` 中对 `ApprovalProcessService.start(...)` 的
      调用与桩全部同步补上 `operationType` 参数；直接驱动 `WorkflowService.start(...)`
      的既有引擎集成测试（`FirstNodeInstanceLinkageBugTest`/
      `MultiInstanceApprovalIntegrationTest`/`TaskOperationsIntegrationTest`/
      `WorkflowModelCompilerV2IntegrationTest`/
      `WorkflowProcessModelServiceV2PublishIntegrationTest`）改为显式传入
      `fixture`/`published` 已持有的 `definitionId` 与 `ExecutionMode.LEGACY_SYNC`，
      行为不变。
- [x] 4.6 启停：`tab_wf_process_binding.enabled` 字段与
      `WorkflowProcessBindingService.setEnabled` 已实现（绑定维度级启停，禁用后该
      维度拒绝新发起、不影响运行中实例，因为运行中实例只依赖发起时快照的
      `definitionId`，与绑定行后续状态无关），本轮不改动。**本轮新增**：
      `WorkflowProcessModelService.setModelEnabled(modelId, enabled, operatorId)` 独立
      控制 `tab_wf_process_model.enabled`（2.1 已建列），语义为"是否接受新发起"，与
      现有 `disable`/`enable`（操作 `tab_wf_process_definition.status`/
      `model.status`，表示版本级下线/重新上线）解耦，只更新 `enabled`/`updateBy`/
      `updateTime` 三列，不触碰 `status`/`currentDefinitionId`，不调用任何 Flowable
      API；新增 Controller 方法 `POST /api/workflow/process-models/{id}/enabled`（请求
      体 `SetModelEnabledRequest{enabled}`），复用 `WorkflowDesign:model:disable`
      权限点（权限资源.txt 已同步补充说明：与版本级下线/重新上线是同一批"流程模型
      上下线相关开关"操作，未单独登记新权限点）。4.5 的 `start()` 改造已同时校验
      `model.enabled`（`ProcessBindingResolutionService.resolveForStart` 内部调用
      `requireEnabledModel`），真实数据库集成测试
      `ProcessBindingResolutionServiceTest#resolveForStart_shouldReject_whenModelDisabled`
      覆盖。"显式回滚"仍不新增独立接口——确认 `WorkflowProcessBindingService
      .switchDefinition` 现有实现确实可以把绑定指向任意已发布的旧 `definitionId`
      （只要求目标定义 `status=PUBLISHED` 且与当前绑定同属一个流程模型，未限制版本号
      必须递增）；本轮在该方法内补充：比较目标版本号与当前绑定版本号大小判定
      `isRollback`，通过 SLF4J 日志（`WorkflowProcessBindingService` 新增 `@Slf4j`）
      区分"显式回滚到历史版本"与"切换到更新版本"两种场景留痕，不拆分新接口、不新增
      持久化字段。

## 5. 表单、身份与安全

- [x] 5.1 新增 `FormVersionEntity`/`FormVersionMapper`/`WorkflowFormVersionService`
      （`cn.nihility.rbac.workflow.dslv2.form` 包）对应已建表 `tab_wf_form_version`；
      `ensureCurrentVersion(bizType)` 基于 `FormFieldDefinitionService
      .listActiveByBizType` 输出的字段定义生成快照（剔除 createBy/createTime/updateBy/
      updateTime 等审计字段后再计算摘要，避免字段定义仅审计信息变化就误判为"表单结构
      变化"），复用 `DigestUtils.sha256` 摘要算法，内容不变复用最新版本、变化才插入新
      版本（`formVersion` 按 `formCode` 自增）。`tab_approval_request` 新增
      `form_version_id`/`before_snapshot`/`after_snapshot` 三列（真实 DDL 核实后确认
      原表无此三列，新增 `V14__add_form_field_permission_org_source_columns.sql`）；
      `ApprovalRequestServiceImpl.submit()` 落库命中的表单版本 id，`before_snapshot`
      按 `targetId` 查询变更前当前数据（CREATE 操作无变更前概念，恒为空），
      `after_snapshot` 为本次提交 payload 的等价只读副本。真实单元测试覆盖 CREATE/
      UPDATE 两种场景的快照落库。**不做**：表单 schema 可视化版本比较页面（第 8 节
      前端）。
- [x] 5.2 `NodeAssigneeRuleDraft`/`NodeAssigneeRuleEntity`/`tab_wf_node_assignee_rule`
      新增 `field_permissions_json` 列，`WorkflowModelCompilerV2` 把
      `ApprovalNodeDslV2.fieldPermissions` 序列化落库；`ApprovalRequestServiceImpl
      .toVO()` 按申请当前所处节点（`tab_wf_process_instance.current_node_id` 反查
      `tab_wf_node_assignee_rule`）读取字段权限快照，从返回给前端的 `requestPayload`
      中整条移除 `HIDDEN` 字段（不是设为 null），单元测试覆盖。**范围调整（如实记录）**：
      1) 过滤只接入了 `ApprovalRequestVO.requestPayload`（这是本仓库当前唯一把表单
      数据返回给前端的位置），未接入 `targetSnapshot`——后者是 `OrgVO`/`UserVO` 等
      业务模块自身的强类型 VO，不是按 fieldCode 组织的 Map，按字段权限过滤需要额外的
      类型级映射机制，超出本轮可验证范围，如实记录为未做，不假装已覆盖；
      2) "处理任务提交时校验实际提交字段集合越权"**未实现代码**：如实核实，当前
      `ApprovalOpinionRequest`（approve/reject 的唯一请求体）只有 `opinion` 字段，
      仓库内不存在任何"审批时提交业务字段修改"的输入通道，这项校验没有真实入参可保护，
      属于"保护一个尚不存在的输入通道"，本轮不臆造该通道也不写形同虚设的校验代码；
      3) `request_payload` 冻结：核实 `approve()`/`reject()`/`cancel()` 全流程代码，
      确认没有任何修改 `request_payload`/`after_snapshot` 的路径，补充一条真实单元
      测试断言最终节点通过后 `requestPayload` 与提交时完全一致、且落库 SQL 不包含这两
      列，验证"无入口可改"这一事实，不是新写冻结逻辑；
      4) 敏感字段：核实 `formfield` 模块字段定义与 ORG/USER/POSITION/APP 四个模块的
      `*CreateRequest`/`*UpdateRequest` 全部字段，未发现任何 password/secret/token/
      credential 类字段（应用密钥 `SecretKey` 走独立的"重置密钥"接口，其请求/响应 DTO
      与 `AppCreateRequest`/`AppUpdateRequest` 完全隔离，不流经审批 payload），如实
      记录"未发现需要剔除的敏感字段"，不做代码改动。
- [x] 5.3 1) `PositionAssigneeResolver` 接入真实任职数据：核实后确认本项目 schema
      未落地独立"岗位"主数据表，"岗位编码"实际对应 `UserPositionEntity.positionType`
      任职类型编码（primary/part_time/temporary 等字典项），新增
      `PositionService.findActiveUserIdsByPositionType(positionType)`（复用
      `UserPositionMapper`，不在 Resolver 内写 SQL），`PositionAssigneeResolver` 委托
      该方法按 `assigneeValue` 查询状态启用的任职用户；`ProcessModelDslV2Validator`
      移除对 `POSITION` 类型的发布禁用，改为与 ROLE/USER 同组的"必填 assignee.value"
      校验。真实单元测试覆盖命中/未命中两种场景。
      2) **`ORG_LEADER` 支持指定固定目标组织**：`AssigneeConfigDsl` 新增 `orgId`
      字段，`orgSource` 新增取值 `FIXED_ORG`（`APPLICANT_SNAPSHOT` 默认行为保留不变）；
      `tab_wf_node_assignee_rule` 新增 `assignee_org_source`/`target_org_id` 两列
      （V14 迁移）；`AssigneeResolveContext` 新增 `orgSource`/`targetOrgId` 两个分量，
      `WorkflowAssigneeTaskListener`/`WorkflowMultiInstanceExecutionListener` 从节点
      规则读取后透传；`OrgLeaderAssigneeResolver` 按 `orgSource=FIXED_ORG` 使用
      `context.targetOrgId()` 而非 `context.applicantOrgId()` 调用
      `AdminRoleLookupService.findOrgLeaderUserIds`；`ProcessModelDslV2Validator`
      新增 `validate(dsl, orgExistsAndEnabled)` 重载校验 `FIXED_ORG` 场景下 `orgId`
      指向的组织真实存在且启用（`WorkflowModelCompilerV2` 注入 `OrgMapper` 提供该
      判定函数）。真实单元测试覆盖 Resolver 与 Validator 两层。前端属性面板改动
      **确认不在本轮范围**，未触碰 `frontend/`。
      3) `APP_ADMIN`/`FORM_REFERENCE_PERSON` 维持 `ProcessModelDslV2Validator` 拒绝
      发布，未新增数据源。
      4) `PREVIOUS_APPROVER` 的 `sourceNodeId`：核实后确认此前**未覆盖**（校验器内
      无任何相关代码），本轮新增 `validatePreviousApproverSourceNode`：按节点直接
      入边数量判定"是否存在多个来源"（>1 视为歧义），歧义场景强制要求
      `assignee.sourceNodeId` 非空且指向图中真实存在的审批节点，真实单元测试覆盖。
- [x] 5.4 `ApprovalTaskCandidateEntity` 补上 `resolveBasis` 字段（`resolve_basis` 列
      V11 已建、此前实体遗漏）；`ResolvedAssignees` 新增 `resolveBasis` 分量，
      `NodeAssigneeResolutionService` 生成可读解析依据文案（如"角色 SECURITY_ADMIN
      命中 3 人"），`WorkflowAssigneeTaskListener.persistTask` 落库候选人时一并写入。
      去重：核实 `AssigneeResolverRegistry.resolve` 返回 `Set<Long>`，候选人集合天然
      去重，落库前不会出现重复 userId。自审排除：核实并**修复真实 bug**——原实现仅在
      "唯一候选人恰为发起人本人"时才排除，候选人集合含发起人 + 其他人时未生效；改为
      从解析结果集合中原地 `remove` 申请人 id（无论集合大小），真实单元测试覆盖多候选
      场景。空人可见：新增 `GET /api/v1/workflow/operations/exceptions`
      （`WorkflowOperationsController`/`Service`）按 `exceptionCode=ASSIGNEE_EMPTY`
      查询运行中实例列表，复用 `WorkflowDesign:model:view` 权限（权限资源.txt 已注明
      复用理由）。停用身份检测：`FlowableWorkflowService.completeTask` 新增
      `requireOperatorEnabled`，在 `taskAuthorizationService.isAuthorized` 之后校验
      `tab_user` 状态，已停用则拒绝（`tab_user` 查不到该 id 时不拒绝，避免误伤既有
      集成测试大量使用的合成 id），真实引擎集成测试
      `StoppedIdentityIntegrationTest` 覆盖"真实停用用户被拒绝"与"用户体系外 id 不受
      影响"两个场景。重分配审计：核实 `WorkflowV2ReassignmentService.reassign` 此前
      只记录新候选人，未记录原候选人对比，补充 `describeOriginalCandidates`
      （单人/候选组节点固定文案"空审批人待分配"，会签节点固定文案"哨兵占位"——两种
      停留形态在重分配前本就没有真实候选人可查），审计 remark 现含"原候选人/执行人/
      新候选人/原因"完整信息。
- [x] 5.5（安全加固，已确认真实漏洞）`IdentityAuthFilter` 新增
      `FIXED_PERMISSION_MAPPINGS`（HTTP 方法 + Ant 路径 → 固定权限编码静态映射表），
      覆盖流程模型增删改查/发布/下线/启停/试运行/发布审核/审核决策、业务绑定增删改
      查/启停、审批 approve/reject 共 21 条映射；`resolveRequiredPermission` 命中
      映射表时返回固定编码（忽略 `menu` 头具体值），未命中的存量接口保持原有"直接用
      `menu` 头值"行为不变。真实单元测试覆盖"伪造低权限 menu 头调用映射内高权限接口
      必须 403"、"持有正确固定权限放行"、"未命中映射表的存量接口不受影响"三个场景。
      本轮补齐 `WorkflowDesign:model:review`/`WorkflowDesign:binding:view`/
      `WorkflowDesign:binding:edit` 三个权限点的 `tab_menu`/`tab_permission`/
      `tab_role_permission` 种子数据（V14 迁移）——核实后发现这是第4节遗留的真实缺口：
      相关 Controller 代码注释早已引用这三个编码，但从未真正写入数据库，导致这些
      接口对任何角色（含 SUPER_ADMIN）都完全不可授权访问，不补上会让本轮新建的固定
      映射表校验永远失败。范围严格限定在本 change 新增的接口，不重新梳理全量存量接口
      映射，`权限资源.txt` 已同步说明。
- [x] 5.6（已确认真实风险，人工已批准修改 build.gradle）从 `backend/build.gradle`
      移除 `flowable-spring-boot-starter-rest` 一行；同步移除
      `application.yml` 中仅为排除该 starter 自动配置类而存在的
      `spring.autoconfigure.exclude: org.flowable.spring.boot.RestApiAutoConfiguration`
      配置项——**额外发现的真实风险**：若保留该配置项，移除依赖后该类已不在 classpath
      上，Spring Boot 启动解析 `spring.autoconfigure.exclude` 会因类不存在而报错，
      必须一并删除，不能只删依赖。移除后 `./gradlew test` 全量跑通（详见测试结果小节），
      确认没有任何 Bean 依赖该 starter 的自动配置类。UEL 拼接排查：复核
      `ConditionAstCompiler`/`WorkflowModelCompilerV2`/`WorkflowModelCompilerImpl`/
      `MultiInstanceCompletionEvaluator` 全部 `${...}` 生成点，确认均为编译器按固定
      结构模板生成、不拼接用户自由文本，与 3.3 已确认的约束一致，未发现遗漏的代码
      路径，未新写代码。

## 6. 运行时与复杂任务

- [x] 6.1（已核实真实共事务，已修复两处静默失败，已如实记录一处刻意保留的既有权衡）
      1) **共事务核实**：反编译
      `org.flowable.spring.boot.ProcessEngineAutoConfiguration#springProcessEngineConfiguration`
      （`javap -p -c`）确认其方法签名直接以 Spring 注入的 `javax.sql.DataSource`/
      `org.springframework.transaction.PlatformTransactionManager` 构造
      `SpringProcessEngineConfiguration`；核实 `application.yml` 只声明了一份
      `spring.datasource`，全仓库未见任何 `@Configuration` 类定义第二个 DataSource 或
      TransactionManager Bean（`WorkflowSpringContext` 等 Flowable 集成代码也只是反射取
      Spring 容器里的既有 Bean，未新建数据源），因此二者在 Spring 容器里必然是同一个
      单例。新增真实集成测试
      `cn.nihility.rbac.workflow.integration.EngineBusinessSharedTransactionIntegrationTest`
      （不使用 `@Transactional` 测试注解，避免测试自身的事务包住被测代码导致"同事务内脏读"
      掩盖问题——两个方法各自都是会真正提交/回滚的最外层物理事务）：
      a) `flowableEngine_shouldShareSameDataSourceAndTransactionManagerBean_withBusinessLayer`
      断言 `SpringProcessEngineConfiguration.getTransactionManager()` 与业务层
      `@Autowired PlatformTransactionManager` 引用相等；`getDataSource()` 实测返回的是
      `org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy`（Flowable 自动
      装配额外包了一层，让直接持有 DataSource 引用的代码也能参与当前线程事务，这是预期行为
      而非 bug），解包 `DelegatingDataSource.getTargetDataSource()` 后与业务层
      `@Autowired DataSource` 引用相等；
      b) `engineRuntimeCallAndBusinessWrite_shouldRollBackTogether_whenBusinessWriteFailsAfterEngineCall`
      用共享的 `PlatformTransactionManager` 构造 `TransactionTemplate`
      （`PROPAGATION_REQUIRED`），在同一个物理事务内先调用真实
      `runtimeService.startProcessInstanceById(...)` 启动一个真实流程实例，再对
      `tab_wf_process_instance` 连续 `insert` 两条 `flowable_instance_id` 相同的记录，
      第二条因真实唯一约束 `uk_tab_wf_process_instance_flowable_id` 抛出真实
      `DataIntegrityViolationException`（不是人为 `throw`）；断言事务整体回滚后
      `runtimeService.createProcessInstanceQuery().processInstanceId(startedId).count()==0`
      （引擎侧运行时变更确实回滚，不是"看起来在同一个库"）且两条业务表 insert（含"第一条
      本已成功"的那条）都查不到。两个方法均已用 `gradlew.bat test --tests
      "...EngineBusinessSharedTransactionIntegrationTest"` 真实跑通（对着远程共享开发库
      MySQL 5.7.44，非 mock）。
      2) **监听器/委托逐个读代码核实静默吞异常**：`WorkflowV2EndOutcomeListener`（决定
      `approved` 终态变量，属于"错误吞掉会让 REJECTED 被误判为 APPROVED"这类静默数据错误
      场景）与 `AutoServiceTaskDelegate`（此前发现异常路径时只打一条 `log.error` 就正常
      `return`，流程照常往下推进，等于把"AUTO 动作其实什么也没做"这一事实用"流程正常完成"
      的假象掩盖，比"catch 后打日志"更隐蔽）确认属于真实的静默失败，均已修复：前者
      catch 块补 `log.error(...); throw ex;`（保留业务上下文日志后原样重新抛出，交由
      Flowable 命令边界回滚整个事务）；后者由"打日志后返回"改为"打日志后
      `throw new IllegalStateException(...)`"，一旦发布前校验失效导致该占位委托被真的
      调用到，流程会真实中止/回滚而不是悄悄把未执行的动作当成已完成。`CcServiceTaskDelegate`
      核实其 catch-log-不重抛是 design.md Decision 10"抄送是持久化 recipient
      记录……非 userTask，不阻塞流程"这一本 change 自身的明确设计（CC 编译为同步
      ServiceTask、无 BPMN 错误边界事件，唯一能实现"抄送失败不阻塞主流程"的方式就是在
      委托内部吸收异常），不是缺陷，未改动。
      **发现的真实设计冲突，本轮未处理**：`WorkflowAssigneeTaskListener`/
      `WorkflowMultiInstanceExecutionListener` 的 `notify()` 同样 catch broad
      `RuntimeException` 后只打日志、不重抛，但这是已归档的 `workflow-approval-engine`
      change 一处明确记录在案的设计权衡（该 change design.md Risks 一节："在
      `WorkflowAssigneeTaskListener`/`ExecutionListener` 里对解析结果做防御性校验，
      解析失败或配置非法时按 `TO_WORKFLOW_ADMIN` 兜底并记录错误日志，不让流程卡死或
      抛出未捕获异常导致 Flowable 事务回滚"），且这两个监听器是 v1/v2 共用的基础设施
      （v1 老流程与本 change 新增的 DSL v2 流程都挂同一个监听器类），与本 change
      design.md Decision 6"监听器失败不吞异常"字面冲突。这不是"缺失则补 throws"能安全
      解决的小修：径直去掉 catch 会让任何审批人解析过程中的运行时异常（哪怕只是某个
      节点规则配置错误这类已被日志覆盖的已知场景）直接回滚正在运行的 v1 生产流程发起/
      任务创建，属于会影响存量在跑流程行为的架构级改动，超出 6.1"核实并按需修复"的
      本轮范围，也不是 6.1 任务描述里"缺失则补 throws"这一具体指引能不经过设计层面权衡
      就直接执行的改动。如实记录此冲突，未修改这两个类，留待后续单独的 OpenSpec 变更
      （更新两份 design.md 的权衡说明并取得人工确认后）处理。
      3) **定时器/异步 Job 现状确认**：`Grep` 全仓库 `cn.nihility.rbac.workflow` 包，未见
      `boundaryEvent`/`TimerEventDefinition`/`@Scheduled`/`ManagementService`/`JobQuery`/
      `AsyncExecutor` 任何引用，确认本 change 目前没有引入任何边界定时器或独立异步 Job
      用于流程终态协调（超时提醒属于第 9 节，本轮未实现），如实记录"当前无定时器/异步
      Job 需要协调"。
- [x] 6.2（已核实真实缺口并补齐，已通过真实并发测试发现并修复两处真实并发缺陷）
      1) **幂等冲突检测**：核实 `tab_wf_operation_request`（V7 建表）确认此前无
      `payload_hash`/`result_text` 列，`IdempotencyService.executeOnce` 只按
      `request_key` 唯一约束"存在即插入失败即短路跳过"，从未比对 payload。新增
      `V18__add_operation_request_payload_hash.sql`（说明见下方"迁移版本号"小节）补齐
      两列；`OperationRequestEntity` 同步加字段；`IdempotencyService.executeOnce` 签名
      新增 `payload` 参数，内部用 `DigestUtils.sha256(JacksonUtils.toJson(payload))`
      计算摘要与落库比对：同 key 同 payload 直接反序列化 `result_text` 返回原结果；
      同 key 不同 payload 抛 `BusinessException(WorkflowErrorCode.IDEMPOTENCY_CONFLICT,
      ...)`（新增 `WorkflowErrorCode` 常量类，取值 4090）。`FlowableWorkflowService`
      的 `approve`/`reject`/`returnTask`/`withdraw`/`transfer`/`delegate`/`addSign`
      七处调用点与 `WorkflowV2ReassignmentService.reassign` 均已改传对应 command
      对象（或组装的 `Map`）作为 payload。真实单元测试
      `IdempotencyServiceImplTest` 覆盖同 payload 命中/不同 payload 冲突两个场景（Mock
      DAO）；真实数据库并发集成测试
      `IdempotencyServiceImplConcurrencyIntegrationTest`
      `executeOnce_concurrentSamePayload_onlyOneExecutesBusinessLogic`（两线程各自
      `TransactionTemplate(REQUIRED)` 包一个真实独立物理事务，几乎同时提交同一幂等键+
      同一 payload，断言业务逻辑只真正执行一次、两边拿到完全一致的结果）与
      `executeOnce_shouldRejectConflict_whenSecondCallUsesDifferentPayload`（同 key 不同
      payload 真实抛 `IDEMPOTENCY_CONFLICT`）均已用 `gradlew.bat test` 针对远程共享
      MySQL 开发库真实跑通。
      2) **`rollback-only` 恢复路径——按真实测试结果调整了实现方式，未照抄
      design.md 字面提到的 `REQUIRES_NEW` 示例写法**：`design.md` 第8节原文列举
      "比如 TransactionTemplate 配合 PROPAGATION_REQUIRES_NEW" 作为可选写法之一。
      按此实现后，真实跑
      `WorkflowModelCompilerV2IntegrationTest`（`@Transactional` 测试事务，方法内连续
      两次调用同一幂等键的 `reassignmentService.reassign(...)` 验证幂等——这是
      3.5/5.4 已有且合理的既有测试模式）出现真实回归："幂等记录查询异常"：`REQUIRES_NEW`
      会挂起当前（测试）事务、换一个物理连接重新查询，而第一次插入尚未提交（无论是
      测试事务未提交，还是生产代码里调用方本就在同一个更大的业务事务内两次触发同一幂等
      操作），新事务天然看不到"当前事务里已插入但未提交"的那一行，被误判为"记录不存在"。
      同时核实确认（`IdempotencyServiceImplConcurrencyIntegrationTest` 的第一个用例已
      验证）：MySQL InnoDB 下单条语句触发的唯一键冲突并不会让当前事务进入不可用/
      rollback-only 状态（这一点与 Postgres 不同），本类捕获该异常后不重新抛出，调用方
      事务不会被标记为 rollback-only。据此改为**在当前事务内**对命中冲突的行补一次
      `SELECT ... FOR UPDATE`（而不是切新事务）：加锁读取绕开 REPEATABLE READ 快照限制，
      总读最新已提交版本，因此同时正确处理"同一事务内自身刚插入的行"（本就对自己可见）与
      "另一个事务已提交的行"（加锁读取穿透快照）两种场景，且不破坏既有的"同一事务内重复
      调用幂等操作"测试模式。修复后 `WorkflowModelCompilerV2IntegrationTest` 两个原本
      被本轮改动带崩的用例恢复通过。
      3) **业务活动锁接入**：核实"发起申请"真正入口在
      `ApprovalRequestServiceImpl.submit()`（`ApprovalProcessService.start()` 的
      `requestId` 参数正是从这里的 `entity.getId()` 传入，`start()` 本身不掌握
      `targetId`），新增 `BusinessLockEntity`/`BusinessLockMapper`/
      `BusinessLockService`/`BusinessLockServiceImpl`（复合主键 `(bizType,
      targetKey)`，不使用假设单列主键的 `selectById`/`updateById`）；`submit()` 在
      `approvalRequestMapper.insert(entity)` 之后、调用
      `approvalProcessService.start()` 之前，同一事务内 `acquire(bizType,
      targetKey, entity.getId(), applicantId)`；`targetKey` 非 CREATE 操作用
      `targetId` 文本，CREATE 操作（无已存在目标）用 `"REQUEST:" + entity.getId()`
      临时键（design.md 明确允许，天然不与其他申请冲突，即 CREATE 场景不做跨申请去重，
      只是复用同一套加锁/释放生命周期）。到达终态（`finalizeApproval`/
      `terminateAsRejected`/`reject()`/`cancel()` 四个真正写终态的代码路径，`advanceNode`
      非最终节点通过不释放）后调用 `release(...)` 清空 `activeRequestId`（锁行本身
      保留复用，不删除）。真实数据库集成测试
      `ApprovalRequestServiceImplBusinessLockIntegrationTest`
      （用 `USER`+`DISABLE` 组合，`validateScope` 对 `USER` 类型直接放行、无需构造
      真实用户数据即可覆盖锁本身行为）覆盖：同目标连续两次 `submit` 第二次被拒绝、
      `cancel` 到达终态后同目标可再次 `submit`、不同目标互不影响三个场景；
      `BusinessLockServiceImplTest` 单独覆盖 `acquire`/`release` 的新建/复用/拒绝/
      非持有者释放跳过四个场景，均对着真实数据库跑通。
      **真实发现的并发缺陷（`acquire_concurrentCalls_onlyOneShouldSucceed` 首次运行即
      100% 复现）**：最初实现是"先 `SELECT ... FOR UPDATE` 判断行不存在、不存在再
      `INSERT`"，两个并发线程对同一个全新 `(bizType, targetKey)` 做该操作时，MySQL
      InnoDB 对不存在键的加锁读取会各自持有该键位置的间隙锁（间隙锁彼此不互斥），随后
      两边的 `INSERT` 都需要插入意向锁并等待对方的间隙锁释放，构成对称等待，被数据库判定
      为真实死锁（`Deadlock found when trying to get lock`），其中一个事务被强制回滚，
      在测试里表现为 `DeadlockLoserDataAccessException`（未被当时的
      `DuplicateKeyException` 分支捕获，直接抛出）。改为"先尝试 `INSERT`（乐观假设行
      不存在），命中 `DuplicateKeyException` 才对已经确定存在的行做
      `SELECT ... FOR UPDATE`"（与 `IdempotencyServiceImpl` 处理幂等 insert 冲突同一
      套路），两个并发 `INSERT` 只会是"一个成功、一个因唯一键冲突短暂等待后报错"的正常
      竞争，不再出现对称间隙锁互相等待；改造后同一并发测试连续多次运行稳定通过（断言
      "成功数=1 且冲突数=1"）。未使用 `INSERT ... ON DUPLICATE KEY UPDATE`（design.md
      第9节明确禁止的厂商专属 upsert）。
      4) **固定加锁顺序**：核实 `FlowableWorkflowService` 的 `completeTask`
      （`approve`/`reject` 共用）/`doReturnTask`/`doTransfer`/`doDelegate`/
      `doAddSign` 此前**完全没有任何行锁**（`requireTask`/`requireInstance` 都是
      普通 `selectById`），且 `doReturnTask`/`doTransfer`/`doDelegate`/`doAddSign`
      四个方法都是"先取任务行、再取实例行"，与 design.md"业务活动锁 → 实例行 → 任务行
      → 节点轮次"顺序相反；`completeTask` 更是直到方法末尾（`finalizeInstanceIfEnded`）
      才附带读一次实例行。统一改造：新增 `requireInstanceForUpdate`/
      `requireTaskForUpdate`（`SELECT ... FOR UPDATE`），五个方法均改为"先用不加锁的
      `requireTask` 读一次任务行拿到 `processInstanceId` → 加锁实例行 →
      加锁任务行 → 按加锁后的实体继续原逻辑"，加锁顺序统一为实例行先于任务行；
      `doWithdraw` 只涉及实例行这一层（撤回不针对具体任务，批量关闭任务沿用原有
      `closeOpenTasks` 不加行锁，未在本轮加固范围内）。业务活动锁属于更上层
      `ApprovalRequestServiceImpl.submit()` 的加锁范畴，与本层"实例行→任务行"衔接一致，
      不重复加锁。第四层"节点轮次" `tab_wf_node_run` 表当前仍完全未被任何动作方法使用
      （6.3 会签计票落地前不存在该层级需要加锁的代码路径，如实记录、未臆造），本轮加锁
      顺序统一只覆盖前三层，第四层留待 6.3。加锁顺序改造未新增专门的死锁/顺序验证测试
      （任务描述本身未明确要求"补测试"，只要求"核实并统一"），依赖既有引擎集成测试
      （`TaskOperationsIntegrationTest`/`MultiInstanceApprovalIntegrationTest` 等）
      继续全部通过作为功能不受影响的回归证据。
      **迁移版本号说明**：本轮编码期间发现远程共享开发库的 `flyway_schema_history`
      已存在 `V15`/`V16`/`V17`（`add assignee source node`/`add typed workflow
      request result`/`add node vote policy`，均于当天由另一个并行会话真实提交并已在
      共享库执行成功），但对应 `.sql` 文件当时不在本工作树内——判断为同一开发库被多个
      并行 OpenSpec 变更/会话共享导致的迁移版本号时序错位，本轮新增列迁移改用下一个
      可用版本号 `V18`（而非最初使用的 `V15`，已在提交前重命名），避免版本号冲突；
      另外因为 Flyway 默认会校验"schema_history 中记录但本地缺失"的迁移版本并直接
      拒绝启动（`Detected applied migration not resolved locally`），导致本地在这三个
      版本号短暂缺失期间**全部** `@SpringBootTest` 集成测试（不止本轮新增的）都无法启动
      Spring 上下文，这不是本轮改动引入的缺陷，而是共享开发库多会话协作的固有时序问题；
      为了本轮及后续会话能继续正常跑测试，在 `application.yml` 的
      `spring.flyway` 下新增 `ignore-migration-patterns: "*:missing"`
      （只放宽"已记录但本地缺失"这一种校验失败场景，不放宽本地已存在迁移文件的
      checksum 校验），已在注释里说明原因。
      **真实测试结果**：`./gradlew.bat test`（全量）稳定输出
      `1244 tests completed, 6 failed`，失败集合与开始本任务前 6.1 记录的既有 6 项
      order-dependent 缺陷（`MultiInstanceApprovalIntegrationTest` 2 项 +
      `TaskOperationsIntegrationTest` 4 项，均为 `BadSqlGrammarException`）完全一致，
      未引入新失败；本轮新增/修改的全部测试类单独运行与随全量一起运行均稳定通过（含
      两个真实并发测试各自重复运行三次以上验证非偶然通过）。
      **后续跟进（同一 change 内，6.2 结束后核实）**：本地开发库 `rbac`（`127.0.0.1:3306`，
      `application.yml` 唯一配置的数据源）复核时发现已变为空库（`information_schema.tables`
      中 0 条记录，`flyway_schema_history` 表本身也不存在），与上文记录的"远程共享开发库
      已存在 V15-V17"不再一致——判断为该共享开发库在本任务完成后被重置（如实记录观察到的
      现象，未深究重置的具体操作者/方式）。V15-V17 的版本号冲突因此已不复存在：空库上的
      下一次 Flyway 迁移会从 `V1` 完整执行到 `V18`，不会再触发"记录存在但本地文件缺失"的
      校验失败。已撤销 `application.yml` 中的 `ignore-migration-patterns: "*:missing"`
      （不再需要，保留只会掩盖以后真实的漏迁移文件问题）。
- [ ] 6.3 **本轮范围（核心，已确认当前完全未实现）**：已核实
      `FlowableWorkflowService.completeTask` 目前只传一个 `approved` 布尔变量给
      `taskService.complete(...)`，`MultiInstanceCompletionEvaluator`（v1 遗留）
      对所有 DSL v2 会签节点也套用同一套"任一驳回即一票否决终止，与 mode 无关"的
      判定，完全没有实现 design.md 第7节的 `rejectPolicy=VETO/THRESHOLD` 区分、
      `N/A/R/U` 计票（总票数/同意/反对/未决）、`DISAGREE`（阈值制反对票）与
      `REJECT`（节点内终止拒绝）的语义区分、`tab_wf_node_run` 表（已建表完全未使用）
      的每轮 `round_no`/N/A/R 落库。本轮实现：
      1) `WorkflowV2MultiInstanceExecutionListener`（新建或扩展现有 v1 监听器为
      v2 专用版本，不要直接改 v1 监听器的既有行为）在每个 MI 实例任务完成时把
      决策写入节点执行作用域变量（而不是 v1 的根级 `approved`/`miVeto`，按
      design.md"变量隔离到节点执行/轮次"要求），同时累加/更新对应 `tab_wf_node_run`
      行的 A/R 计数；
      2) 完成条件表达式按 `rejectPolicy` 区分生成：`VETO` 保持"任一 REJECT 立即
      终止"；`THRESHOLD` 改为 `A>=K` 通过、`A+U<K` 拒绝、其余等待（K 按
      `ALL=N/ANY=1/PERCENT=ceil(N×percent/100)` 整数计算，不用浮点比较，参考
      design.md 第7节公式）；
      3) 任务处理动作区分 `DISAGREE`（THRESHOLD 模式下的反对票，不立即终止流程，
      只计入 R）与 `REJECT`（节点允许配置的终止拒绝，必须填写意见，直接终止整个
      流程实例）——当前 `ApprovalAction` 常量和 approve/reject 接口是否已经能表达
      这个区分需先核实，不够则扩展（新增 `disagree` 动作或复用现有 `reject` 语义，
      按实际情况调整，不要凭空新增用不到的接口）；
      4) 只有真实任务决策才计票，取消/委派归还不计票（核实 `resolve`/取消路径确实
      不会误触发计票）；
      5) 补真实引擎集成测试覆盖：1人、3人 THRESHOLD 边界（如 3 人中 2 票通过）、
      百分比边界（如 66%）、VETO 模式下第一票反对立即终止且不等待其余人、重入节点
      重新计票（轮次隔离，`round_no` 递增）。
- [ ] 6.4 **本轮范围**：验证串行 MI（`vote.execution=SEQUENTIAL`）、并行 MI、以及
      3.4 已支持的嵌套配对并行块（`PARALLEL_SPLIT`/`PARALLEL_JOIN`）三者与 6.3 新
      计票逻辑组合后仍然正确汇合；任一并行分支触发全流程终止拒绝
      （`REJECT`/`TerminateEventDefinition`）时验证其余开放分支、任务、定时器均被
      取消，业务投影记录 `CANCELLED`+原因而非误记为已同意。真实引擎集成测试补充
      "两个并行分支，一个 VETO 一个 THRESHOLD"这类混合场景。
- [ ] 6.5 **本轮范围**：现状核实——`claim`（`autoClaimIfNeeded`）、`transfer`
      （`doTransfer`）、`delegate`/`resolve`（`doDelegate`+`completeTask` 内
      `DelegationState.PENDING` 分支）在 `FlowableWorkflowService` 已有实现；本轮
      重点是补齐验证测试而非重新实现：候选任务原子 claim（并发 claim 竞争只有一人
      成功，用真实并发测试而不是假设）；已分配任务只允许 assignee 操作（越权测试）；
      `resolve` 归还后流程确实不推进（受托人 `resolve` 不产生 `DISAGREE`/`REJECT`
      计票，不完成 Flowable 任务），owner 归还后仍需自己再次 approve/reject 才计票
      （核实与 6.3 新计票逻辑的交互不冲突）；委派链路不允许链式委派（受托人不能再次
      delegate，只能 resolve 或 approve/reject）。
- [ ] 6.6 **本轮范围**：`doReturnTask` 当前用
      `runtimeService.createChangeActivityStateBuilder().moveActivityIdTo(...)`
      退回历史节点，已有 `allowReturn` 规则校验，但**未核实**是否拒绝跨并行块/跨
      MI 边界/未经过节点的退回目标（当前实现看起来只校验目标节点规则的
      `allowReturn`，未校验"目标节点是否与当前节点在同一串行域、是否真的是历史
      路径上的节点"）。本轮补齐：退回前校验目标节点必须是当前实例真实审批轨迹
      （`tab_wf_approval_record`）里已完成的节点，且与当前节点在同一并行块作用域
      内（不允许跨块）、不允许退到已在 MI 内部的中间状态；退回后正确取消当前节点
      被撤销的任务并重建轮次（关联 6.3 的 `round_no`）。跨并行域退回本轮继续拒绝
      （design.md Non-Goals 明确排除），补测试证明确实被拒绝而非静默允许。
- [ ] 6.7 **本轮范围**：`doAddSign` 当前已用
      `runtimeService.addMultiInstanceExecution` 实现真实加签，但未核实：加签用户
      与现有候选人去重（重复加签同一人应拒绝或忽略）；加签后 N（`tab_wf_node_run`
      总票数）与 K（阈值）是否随 6.3 新增的计票机制同步更新且在同一实例锁内完成，
      避免与并发的最后一票产生竞争（需要真实并发集成测试：加签与最后一票并发提交，
      结果确定性）；串行 MI/候选池/已结束节点拒绝加签（核实当前是否已有此校验，
      没有则补上）。
- [ ] 6.8 **本轮范围**：`doWithdraw` 已实现"存在任何审批记录则不可撤回"
      （`WithdrawPolicy`），核实是否已经"与首票竞争串行化"（撤回和第一次 approve/
      reject 并发提交时，只有一个成功，不能出现"已撤回的流程又被计入一票"这种
      竞态）——补真实并发测试验证。`terminate`（design.md 表格里的运维终止动作）
      当前代码是否已存在需先核实（未见于已读的 `FlowableWorkflowService`，若确实
      缺失则新增：独立运维权限点、必填终止原因、结束流程并取消全部开放任务，不
      触发任何业务执行事件）。任务投影全生命周期同步：核实 MI 提前结束/退回/终止
      时 `tab_wf_approval_task.status`/`cancel_reason` 是否都被正确同步（已有
      `cancel_reason` 列，核实是否已写入）。
- [ ] 6.9 **本轮范围**：核实 `WorkflowTaskService`（待办/已办查询实现）是否已经
      "数据库过滤分页"而非"加载全量到 Java 后过滤"（design.md 明确禁止的反模式）；
      核实分页结果按 `time+id` 稳定排序、去重。核实流程实例详情返回的"当前节点"是
      否已经是"节点集合"（并行场景下同时有多个开放节点）而不是单一
      `current_node_id` 字段（`ProcessInstanceEntity.currentNodeId`/
      `currentNodeName` 目前是单值字段，并行场景下需要额外聚合查询开放任务所在的
      全部节点，不能只信任这两个单值列）；旧的"按申请 ID 审批"接口
      （`ApprovalRequestServiceImpl.approve(Long id, String opinion)`）在存在多个
      可操作任务时（并行场景）核实是否已经消歧拒绝（要求明确 taskId）而不是
      "任取第一条"——已核实当前实现用 `requireCurrentTask` 按 `processInstanceId`
      查询，若命中多条需确认其行为，不符合"歧义报错"语义则修复。

## 7. 可靠执行与通知

- [ ] 7.1 实现Outbox同事务写入、MySQL5.7兼容CAS租约领取、退避重试和超限人工队列。
- [ ] 7.2 实现消费唯一键、过期租约fencing、成功业务结果与消费标记原子提交。
- [ ] 7.3 为ORG与USER实现可靠执行适配，复用既有业务规则并验证用户任职整体更新。
- [ ] 7.4 为POSITION与APP实现可靠执行适配，覆盖创建/更新/状态/删除及resultTargetId。
- [ ] 7.5 实现LEGACY_SYNC/RELIABLE_ASYNC按申请冻结、执行前提交人权限/目标版本检查及失败分类。
- [ ] 7.6 实现抄送可见性、站内通知去重、WebSocket刷新提示和通知失败独立重试。
- [ ] 7.7 实现受控AUTO异步结果确认；不支持外部幂等的动作不开放自动重试。

## 8. 前端完整体验

- [ ] 8.1 复用已有Vue Flow节点、api与store补齐设计器画布及模型列表，提供撤销重做、定位错误和保存冲突处理。
- [ ] 8.2 补齐条件/并行/表单/审批人/会签/超时属性面板，隐藏后端未支持选项。
- [ ] 8.3 实现试运行报告、审核发布、版本比较、只读历史图和业务绑定页面。
- [ ] 8.4 完善我的申请/待办/已办/抄送详情，按服务端actions显示认领、归还、反对票、拒绝等操作。
- [ ] 8.5 实现并行高亮、轮次轨迹、审批结果与业务执行双状态及失败处理入口。
- [ ] 8.6 同步路由、菜单、按钮与权限资源.txt，保留既有圆点虚线视觉语言和统一错误提示。

## 9. 超时与运行维护

- [ ] 9.1 实现有限提醒、催办冷却、幂等升级和任务完成后的过期事件忽略。
- [ ] 9.2 实现异常查询、Job公开API查询重试、审计化重分配与终止。
- [ ] 9.3 实现游标分批对账、确定性投影修复、明确APPROVED才可补执行事件的规则。
- [ ] 9.4 建立Outbox延迟、空人、执行失败、Job、DB锁等待与链路标识监控及告警。
- [ ] 9.5 实现经批准的历史归档与恢复校验，排除运行中、执行未完和事件未消费数据。

## 10. 真实验收与上线

- [ ] 10.1 在固定Flowable7.2.0及目标MySQL上运行编译、发布、事务回滚、旧版本存量测试。
- [ ] 10.2 用真实引擎验证会签全部/任一/比例、VETO/THRESHOLD、反对票、串行MI、并行汇合与全局拒绝。
- [ ] 10.3 验证退回、转办、委派归还、加签、空人恢复与离职重分配，断言任务投影和引擎状态一致。
- [ ] 10.4 双应用实例模拟重复发起/抢办/加签与完成/撤回与首票/重复发布，验证单次副作用。
- [ ] 10.5 注入审批提交后宕机、业务提交后重投、租约过期、通知失败、目标版本冲突，验证恢复路径。
- [ ] 10.6 浏览器跑design第13节“人员变更”高低风险流程，从画图到生效，再验证显式版本回滚。
- [ ] 10.7 回归四业务审批开关关闭、旧同步模式、权限门控、敏感字段和旧申请展示。
- [ ] 10.8 在backend运行gradlew.bat build，在frontend运行npm run build；保存真实结果与环境限制，未运行的测试不得记为通过。
- [ ] 10.9 按实际峰值压测待办/审批/Outbox，确认容量指标、连接池与线程池配置，完成小范围灰度和回滚演练。
- [ ] 10.10 实施完成后依据diff和测试结果委托项目文档同步agent更新proposal/design/tasks；按约定另行同步spec与归档。
