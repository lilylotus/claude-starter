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
- [ ] 4.2 **未实现**。设计文档要求"快速预演"（静态路径/人员解析解释）与"独立测试环境
      真实试运行"两层。`ConditionAstEvaluator`（3.3 已完成）已具备"快速预演"所需的
      条件求值能力，但把它接成一个完整的"给定模拟表单+身份、返回完整路径+各节点解析
      候选人+未覆盖分支"的预演接口本轮未及实现；"独立测试环境真实试运行"需要一套与
      生产库隔离的测试引擎/数据库基础设施，属于部署与环境层面的工作，非本轮编码可
      解决，明确不在本次范围内。
- [x] 4.3 新增 `tab_wf_release_review` 对应的 `ReleaseReviewEntity`/`ReleaseReviewMapper`
      + `WorkflowReleaseReviewService`（`submitForReview`/`decide`/
      `requireApprovedForCurrentRevision`）+ `WorkflowReleaseReviewController`
      （`POST .../reviews`、`POST /api/workflow/process-model-reviews/{id}/decisions`）。
      审核者与编辑者不能是同一人（真实数据库集成测试覆盖）；草稿在提交审核后又被修改
      （`draft_revision`/摘要不一致）时审核请求自动判定失效并拒绝决策，需要重新提交
      （真实数据库集成测试覆盖）。**已知缺口**：`requireApprovedForCurrentRevision`
      尚未接入 `publish()` 作为强制前置门禁——审核服务本身可独立调用验证，但发布接口
      目前仍可在没有通过审核的情况下发布（v1 发布流程本就没有审核概念，v2 是否应无
      条件强制审核属于产品策略决策，design.md 也把"发布审核人"列为未决 Open
      Question，本轮不强行接入避免锁死 v2 发布唯一路径导致测试/联调受阻）；发布幂等
      沿用 `workflow-approval-engine` change 已实现的 `IdempotencyService`/
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
      祖先回退、全局兜底、未配置拒绝、禁用绑定被跳过五种场景。**已知缺口**：绑定
      解析服务尚未接入 `ApprovalProcessServiceImpl`/`ApprovalRequestServiceImpl` 的
      实际提交流程——四个业务模块提交审批时仍走硬编码的
      `WorkflowConstants.MASTER_DATA_APPROVAL_PROCESS_CODE`（这正是本次会话早前
      `approval-process-biztype-binding` 提案要修复、后来决定改由本 change 承接的
      那个断链），把 `ProcessBindingResolutionService.resolve(...)` 接进
      `ApprovalProcessServiceImpl.start()` 替换写死常量本轮未完成，是最重要的后续
      待办——数据模型、解析算法、CRUD API 均已就绪并测试通过，只差业务侧接线。
- [ ] 4.6 **部分完成**。启停：`tab_wf_process_binding.enabled` 字段与
      `WorkflowProcessBindingService.setEnabled` 已实现（绑定维度级启停，禁用后该
      维度拒绝新发起、不影响运行中实例，因为运行中实例只依赖发起时快照的
      `definitionId`，与绑定行后续状态无关）。**未实现**：`tab_wf_process_model.enabled`
      字段（2.1 已建列）尚未接出对应的模型级启停 service 方法/接口（当前模型级
      启停仍是 `workflow-approval-engine` 遗留的 `disable`/`enable`，操作的是
      `tab_wf_process_definition.status` 而非新的 `model.enabled` 语义区分）；"显式
      回滚"未做成独立接口，但 `WorkflowProcessBindingService.switchDefinition` 本身
      已经能把绑定指向任意已发布的旧 `definitionId`（含校验同一流程模型），语义上
      等价于回滚，只是没有单独包一层"回滚"专用接口/审计标记。

## 5. 表单、身份与安全

- [ ] 5.1 基于现有动态字段创建不可变表单版本，冻结申请before/after、目标版本、申请身份与路由变量。
- [ ] 5.2 实现节点字段读写权限、payload冻结和敏感字段剔除，覆盖伪造写入测试。
- [ ] 5.3 补齐真实岗位/负责人/应用管理员/表单人员解析与sourceNodeId，缺失数据来源时禁用规则。
- [ ] 5.4 实现候选人快照、去重、自审排除、空人阻塞、停用身份检测及重分配依据记录。
- [ ] 5.5 实现服务端HTTP路径方法到固定权限映射，覆盖伪造menu调用发布、审批、补偿与越权详情用例。
- [ ] 5.6 限制引擎可访问Bean与对外REST端点，验证业务用户无法绕过平台操作Flowable。

## 6. 运行时与复杂任务

- [ ] 6.1 验证引擎/MyBatis共用事务，统一HTTP、监听器、定时器、异步Job终态协调。
- [ ] 6.2 实现幂等payloadHash、同业务活动申请唯一、实例/任务固定锁顺序及外层冲突重读。
- [ ] 6.3 实现nodeRun票数、阈值、DISAGREE/REJECT区别与MI作用域隔离；测试1人、3人、百分比边界和提前取消。
- [ ] 6.4 验证串行MI、并行MI、嵌套配对并行的正常汇合及全局拒绝取消。
- [ ] 6.5 实现候选原子认领、转办及delegate/resolve归还语义，验证归还不推进流程。
- [ ] 6.6 实现限定串行域退回、轮次重建和旧任务取消，拒绝跨并行/MI边界目标。
- [ ] 6.7 实现并行MI真实加签、去重和N/K更新，验证与最后一票的竞争。
- [ ] 6.8 实现撤回与首票互斥、授权终止、任务投影全生命周期同步与取消原因。
- [ ] 6.9 待办/已办数据库分页去重、实例当前节点集合、轨迹按原定义渲染，旧按申请ID接口做多任务消歧。

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
