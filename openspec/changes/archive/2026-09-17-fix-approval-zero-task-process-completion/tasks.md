## 1. 引擎层：`start()` 后正确收尾同步跑完的流程实例

- [x] 1.1 `FlowableWorkflowService.start(StartProcessCommand)` 在 `runtimeService.startProcessInstanceById(...)`
      之后、插入 `SUBMIT` 轨迹记录之后，补一次 `finalizeInstanceIfEnded(instance.getId())` 调用（design.md
      Decision 1）。
- [x] 1.2 集成测试覆盖：`start → condition(仅默认分支) → end(outcome=APPROVED)` 这类零任务流程，
      `start()` 返回后 `tab_wf_process_instance.status` 立即为 `APPROVED`，`currentNodeId`/
      `currentNodeName` 为空。新增
      `cn.nihility.rbac.workflow.dslv2.ZeroTaskProcessCompletionIntegrationTest
      #zeroTaskDefaultBranchApproved_shouldFinalizeInstanceImmediately`。
- [x] 1.3 集成测试覆盖对称场景：`start → condition(仅默认分支) → end(outcome=REJECTED)`，`start()` 返回后
      `status` 立即为 `REJECTED`。新增同一测试类
      `#zeroTaskDefaultBranchRejected_shouldFinalizeInstanceImmediately`。
- [x] 1.4 回归验证：既有"至少一个审批节点"的正常流程（单人审批、会签、并行分支等）`start()` 返回后
      `status` 仍为 `RUNNING`，不因本次改动被误收尾；跑一遍现有 `workflow.*` 测试确认无回归。新增同一
      测试类 `#normalFlowWithApprovalNode_shouldRemainRunningAfterStart`；`workflow.*` 全量套件通过。

## 2. 审批服务层：`submit()` 补齐终态分支

- [x] 2.1 `ApprovalRequestServiceImpl.submit(...)` 在调用 `approvalProcessService.start(...)` 之后，查询
      `tab_wf_process_instance.status` 并按 `RUNNING`/`APPROVED`/`REJECTED` 分支处理（design.md Decision 2
      示意代码），未覆盖到的状态值抛出明确异常，不静默吞掉。
- [x] 2.2 `finalizeApproval(entity, approverId, opinion)` 调整为可以接受 `approverId=null`：核实并修正
      `finally { CurrentUserContext.setUserId(approverId); }` 不会把线程上下文恢复成 `null`（应恢复为本次
      提交的申请人 id），必要时拆分参数或在 `submit()` 侧包一层调用（design.md Decision 2 要点一）。实现
      方式与 design.md 示意代码略有出入：未在 `submit()` 侧包一层小方法，而是直接给
      `finalizeApproval` 新增第四个参数 `contextRestoreUserId`（`approve()` 调用方传
      `approverId` 本身、`submit()` 调用方传 `applicantId`），语义与 design.md 描述完全一致，仅实现
      形态不同。
- [x] 2.3 新增私有方法处理"流程同步跑到 `REJECTED` 终态"分支（不得直接复用
      `terminateAsRejected`/`latestTerminateReason`，理由见 design.md Decision 2 要点二），标记申请
      "已拒绝"、固定文案说明原因、释放业务活动锁、写操作日志。新增 `finalizeAutoRejected` 私有方法。
- [x] 2.4 `submit()` 在 `switch` 结束后重新 `selectById` 一次 `entity`，确保构造响应 VO 时使用的是各分支
      真正落库后的最新状态，不使用调用分支方法前的旧值（design.md Decision 2 要点三）。
- [x] 2.5 单元/集成测试覆盖（真实数据库 + 真实 Flowable 引擎，不 mock）：
      - 零任务流程默认分支到 `APPROVED`：`submit()` 返回后，`tab_user`（或其他三类业务对应主表）确实新增
        了记录；`tab_approval_request.status` 为"已通过"、`resultTargetId` 回填正确、`approverId` 为空；
        响应 VO 里 `approvalRequest.status` 与数据库一致；业务活动锁已释放（同一目标可以立即再次提交）。
        新增 `cn.nihility.rbac.approval.service.impl.ApprovalRequestServiceImplZeroTaskSubmitIntegrationTest
        #submit_shouldAutoApproveAndCreateBusinessRecord_whenNoApprovalNodeOnHitPath`。
      - 零任务流程默认分支到 `REJECTED`：`submit()` 返回后不创建任何业务记录，`tab_approval_request.status`
        为"已拒绝"；业务活动锁已释放。新增同一测试类
        `#submit_shouldAutoRejectWithoutBusinessWrite_whenDefaultBranchLeadsToRejectedEnd`。
      - 零任务流程自动通过分支中业务写入失败（如触发唯一性校验）：整个 `submit()` 调用抛出异常，
        `tab_approval_request`/`tab_wf_process_instance`/业务活动锁均不残留任何记录（事务整体回滚）。
        新增独立测试类（不加测试事务，理由见类注释，与既有
        `EngineBusinessSharedTransactionIntegrationTest` 同样的"同一事务内脏读无法证明真实回滚"考量）
        `cn.nihility.rbac.approval.service.impl.ApprovalRequestServiceImplZeroTaskSubmitFailureIntegrationTest
        #submit_shouldRollBackEverything_whenAutoApprovedBusinessWriteFails`。
      - 正常需要人工审批的流程（有开放任务）：`submit()` 行为与修复前完全一致，仍返回"待审批"，
        `flowableTaskId` 正确回填。新增
        `ApprovalRequestServiceImplZeroTaskSubmitIntegrationTest
        #submit_shouldStayPendingAndRecordOpenTask_whenApprovalNodeExistsOnHitPath`；另调整既有
        `ApprovalRequestServiceImplTest`（单元测试）里全部 `submit_*` 用例的 mock 桩，补齐新增的
        `processInstanceMapper.selectById`/`mapper.selectById` 调用，确认无回归。
      - `CurrentUserContext` 修复验证：零任务自动通过分支结束后，同一线程后续代码（如
        `userDisplayService.resolveDisplayNames`）能正确读取到申请人 id，不因 2.2 的调整而出错。已在
        `#submit_shouldAutoApproveAndCreateBusinessRecord_whenNoApprovalNodeOnHitPath` 内一并断言
        `CurrentUserContext.getUserId()`。

## 3. 全量回归

- [x] 3.1 运行 `./gradlew test --tests "cn.nihility.rbac.workflow.*" --tests "cn.nihility.rbac.approval.*"`，
      确认全部通过、无回归。实施过程中发现并修复一个与本次业务修复无关、但阻塞该验证命令可靠通过的
      预置测试基础设施缺陷（见下方"额外发现"）。
- [x] 3.2 运行 `./gradlew build` 全量回归（1371 个测试，含新增的 9 个用例），确认未影响其它模块，构建
      成功。

### 额外发现：修复一个预置的测试基础设施缺陷（超出本 change 原定范围，但为完成 3.1 所必需）

实施 2.5 的真实数据库集成测试时，发现 `--tests "cn.nihility.rbac.workflow.*" --tests
"cn.nihility.rbac.approval.*"` 这条过滤命令（相比不带过滤的 `./gradlew test`）会改变 JUnit5
在同一测试 JVM（Gradle 默认 `forkEvery=0`，全部测试类共享一个 JVM 进程）内的类发现/执行顺序，进而
触发一个与本次业务代码修复完全无关的既有缺陷：项目里 10 个纯 Mockito 单元测试文件（
`ApprovalRequestServiceImplTest`/`ApprovalSwitchServiceImplTest`/`AppServiceImplTest`/
`PermissionServiceImplTest`/`TaskAuthorizationServiceTest`/`PreviousApproverAssigneeResolverTest`/
`AdminRoleLookupServiceTest`/`BeforeFirstApprovalWithdrawPolicyTest`，另有 2 个已正确实现的文件
`LoginLogQueryServiceImplTest`/`DashboardStatisticsServiceImplTest` 中的 6 个文件存在同一缺陷）为了让
`LambdaQueryWrapper`/`LambdaUpdateWrapper` 在脱离 Spring 容器的场景下工作，手动调用
`TableInfoHelper.initTableInfo(...)` 预热 MyBatis-Plus 的实体元数据缓存，但构造用的
`org.apache.ibatis.session.Configuration` 是默认设置（`mapUnderscoreToCamelCase=false`），而
`TableInfoHelper`/`TableInfo` 的缓存是**按 `Class` 全局静态缓存、只初始化一次**——如果这类单元测试在
同一 JVM 内比任何真实 `@SpringBootTest` 集成测试更早触碰到同一个实体类，就会把该实体的列名永久
（对整个测试 JVM 生命周期）错误缓存成驼峰字段名本身（如 `assigneeId`）而非真实下划线列名（如
`assignee_id`，与本项目 `mybatis/mybatis.conf` 里 `mapUnderscoreToCamelCase=true` 的真实生产配置不
一致），导致后续所有真实集成测试对该实体生成的原生 SQL 直接报 `Unknown column 'xxx' in 'where
clause'`。已确认这在本次改动之前就已存在（把新增的 3 个测试类移出后，同一条过滤命令仍复现同样的
大范围级联失败），本次改动只是通过新增测试类改变了类发现顺序而"更容易踩中"。逐一修复全部 8 个存在
该缺陷的文件（`primeLambdaColumnCache`/`prime`/`primeEntity` 辅助方法里显式
`configuration.setMapUnderscoreToCamelCase(true)`），随附修正两个此前"恰好"断言在错误驼峰列名上的
单元测试用例（`ApprovalRequestServiceImplTest#approve_shouldOnlyAdvanceNode_whenNotFinalNode`
`#pageMine_shouldFilterByCurrentUserAndConditions`）与三处对生成 SQL 片段做字符串包含断言但断言值
用了驼峰写法的既有用例（`AppServiceImplTest` 两处 `orgId IN`、
`DashboardStatisticsServiceImplTest` 一处 `orgId IN`、`PermissionServiceImplTest` 一处
`showOrder ASC`），改为断言真实的下划线列名。修复后 `--tests "cn.nihility.rbac.workflow.*" --tests
"cn.nihility.rbac.approval.*"` 与不带过滤的 `./gradlew build`（1371 个测试）均稳定全部通过。

## 4. 历史数据（视用户确认阶段决定是否纳入）

- [ ] 4.1 如用户确认需要处理修复前已产生的僵尸申请（`tab_approval_request.status=待审批` 但
      `tab_wf_approval_task` 无任何记录且对应 Flowable 运行时实例已不存在），编写一次性运维脚本/接口按
      本 change 相同的终态判定逻辑补跑收尾；如用户确认不需要，本节任务保持空/不执行，仅在 proposal.md
      Impact 中已有的说明基础上不做代码改动。

## 5. OpenSpec 文档同步

- [x] 5.1 实现完成后，基于真实 diff 与测试结果核对 `proposal.md`/`design.md`/`tasks.md` 与实际实现是否
      一致，如有偏差据实修正；同步确认 `openspec/specs/master-data-approval-workflow/spec.md` 的 delta
      已通过 `openspec-sync-specs`（或对应 skill/流程）正确应用到主 spec。已核对：`proposal.md`/
      `design.md` 与实际实现（含 §3.1 发现并修复的预置测试基础设施缺陷）一致，无需修改；delta 已通过
      `/opsx:sync` 合并进 `openspec/specs/master-data-approval-workflow/spec.md`（`openspec validate
      --specs` 通过）。第4节历史僵尸数据处理用户尚未明确决定，保持未勾选，不阻塞本次归档。
