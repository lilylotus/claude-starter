## Context

已通过只读代码走查（不含代码修改）确认以下事实：

- `PendingApprovalRequestView.vue` 里 `handleApprove`（批准）只调用
  `ElMessageBox.confirm(...)` 然后 `approvalApi.approveApprovalRequest(row.id)`，不传意见；
  同文件"拒绝"已有完整的 `rejectDialogVisible`/`rejectForm`（`el-form` + `el-input
  type="textarea"`，`rejectRules.opinion` 必填）弹窗模式，可以直接照搬结构做批准弹窗（意见
  不强制必填）。`frontend/src/api/approval.ts` 里 `approveApprovalRequest(id, opinion?)`
  本来就接受可选意见参数，未被调用方使用。
- `ApprovalRequestVO`（`backend/.../approval/dto/ApprovalRequestVO.java`）没有
  `processInstanceId` 字段，尽管 `ApprovalRequestEntity`（持久层实体）本身有这一列；
  `ApprovalRequestServiceImpl.toVO(...)` 转换时没有带出来。前端因此无法知道一条申请对应
  哪个流程实例，也就无法调用已经存在的流程实例详情接口。
- `GET /api/v1/workflow/process-instances/{id}`（`WorkflowTaskController.java:97-102`）→
  `WorkflowTaskServiceImpl.getProcessDetail(processInstanceId)`
  （`workflow/service/impl/WorkflowTaskServiceImpl.java:133-187`）：按 id 查
  `tab_wf_process_instance`，不存在则抛异常，**存在即直接返回**——没有任何校验当前登录
  用户是否是这个流程实例的申请人、审批参与人或候选人。`IdentityAuthFilter`
  的 `FIXED_PERMISSION_MAPPINGS`（`auth/filter/IdentityAuthFilter.java:124-`）里没有这条
  路径，不在固定权限映射表命中范围内的接口按"直接信任 `menu` 请求头值"的既有兜底逻辑处理
  （见 production-approval-lifecycle change tasks.md 5.5 的映射表设计说明），也就是说，只
  要客户端在请求头里带上任意一个自己确实拥有的 `menu` 值（哪怕跟审批完全无关），就能拿到
  任意 `processInstanceId` 的完整审批轨迹（含审批人身份与全部审批意见文本）。这与
  `approval-runtime-safety` spec.md 已经写明的"操作授权和访问控制"需求"详情与抄送数据
  SHALL 按参与关系及字段权限返回"直接矛盾——现状是一个已经存在、但因为前端从未调用这个
  接口而从未被触发的真实漏洞。
- `ProcessInstanceDetailVO`（`workflow/dto/ProcessInstanceDetailVO.java`）已有 `records`
  （完整审批轨迹，谁在哪个节点何时给了什么意见）与 `openNodes`（当前开放节点集合，并行
  分叉场景下可能多个），但**没有**流程定义的完整节点/连线拓扑——它只知道"现在在哪"和
  "已经发生过什么"，不知道"这条流程一共长什么样"。
- 完整拓扑数据只存在于 `ProcessDefinitionEntity.modelJsonSnapshot`
  （`workflow/entity/ProcessDefinitionEntity.java:87`，发布时落库的不可变 DSL JSON 快照），
  `ProcessInstanceEntity.processDefinitionId` 记录了某个运行中实例具体绑定的是哪一个
  版本的定义。唯一现有的读取入口 `GET /api/workflow/process-models/{id}/versions` 按
  `processModelId`（不是 `processDefinitionId`，也不是 `processInstanceId`）查询，且挂在
  `WorkflowDesign:model:view` 权限点下——这是流程设计器管理员才有的权限，普通申请人/
  审批人不持有，也拿不到 `processModelId`。
- DSL 有两套并存的 schema：v1（`workflow/designer/dto/{StartNodeDsl,ApprovalNodeDsl,
  ConditionNodeDsl,EndNodeDsl,EdgeDsl,ProcessModelDsl}.java`）与 v2
  （`workflow/dslv2/dto/*NodeDslV2.java`，多了并行分叉/汇合、抄送、自动任务、会签配置等）。
  `ProcessDefinitionEntity` 有 `schemaVersion` 字段区分（`production-approval-lifecycle`
  change 已交付）。两套 DSL 都已经是标准 Jackson 可反序列化的 DTO，不需要新写解析器，只需
  要一个"DSL → 精简只读图 DTO"的映射层。
- `TaskAuthorizationService.isAuthorized(task, operatorId)`
  （`workflow/assignee/support/TaskAuthorizationService.java:36-66`）已经实现了"单个任务
  的指定处理人/候选用户/候选角色"三维度判定，是现成、可直接复用的构件；补充实例级别的
  参与关系校验时不需要重新实现任务候选人判定逻辑。
- 前端 `package.json` 已依赖 `@vue-flow/core`/`@vue-flow/background`/`@vue-flow/controls`，
  `views/workflow/designer/nodes/{StartNode,ApprovalNode,ConditionNode,EndNode}.vue` 已经
  是可运行的节点渲染组件（当前用于可编辑设计器画布），没有其它图表库依赖。
- `权限资源.txt` 已登记 `ApprovalManagement:request:view`（我的申请）与
  `ApprovalManagement:request:approve`（待我审批，含批准/拒绝）两个权限点，覆盖本次两个
  页面的入口访问控制；本次改动不新增页面/按钮，不需要新登记权限编码。

## Goals / Non-Goals

**Goals:**

1. "待我审批"批准操作可以像拒绝一样填写意见（不强制必填），意见随批准请求一起提交、落库、
   进入审批轨迹。
2. "我的申请"/"待我审批"详情弹窗能看到：当前所处审批节点、该流程完整的节点/连线拓扑图、
   已经发生的每一步审批轨迹（谁、何时、什么节点、什么意见）。
3. 把已经写在 `approval-runtime-safety` spec 里、但现状未落实的"详情数据按参与关系返回"
   约束真正落实到 `GET /api/v1/workflow/process-instances/{id}`，确保这次新增的前端调用
   不会把一个此前"存在但未触发"的越权查看漏洞变成任何登录用户都能轻易触发的问题。

**Non-Goals:**

- 不精确预测流程当前节点之后、经过未评估条件分支的具体走向（见 Decision 4 的取舍说明）——
  这次只展示完整设计拓扑 + 已发生部分的精确状态，不承诺"剩余步骤列表"对分支流程 100%
  准确。
- 不改动 `WorkflowDesign:model:view` 权限点下的流程设计器只读查看能力，本次新增的是一个
  独立的、面向普通申请人/审批人、以 `processInstanceId` 为 key 的读取路径。
- 不涉及 `RELIABLE_ASYNC`/历史僵尸申请数据等其它 change 已记录、尚未实施的范围。
- 不改变已有的批准/拒绝业务语义（多级推进、最终业务写入等）——只新增展示能力和批准弹窗，
  不碰 `ApprovalRequestServiceImpl.approve()`/`finalizeApproval()` 内部逻辑本身。

## Decisions

### 1. 批准弹窗：意见可选，不改后端契约

新增 `approveDialogVisible`/`approveForm`（结构参照现有 `rejectDialogVisible`/
`rejectForm`），唯一区别是 `el-form-item` 不加 `required` 规则（`ApprovalOpinionRequest`
后端本来就没有 `@NotBlank`，保持一致，不额外收紧）。提交时
`approvalApi.approveApprovalRequest(row.id, approveForm.opinion || undefined)`。

**备选方案（未采用）**：把批准意见也做成必填，与拒绝对称。未采用原因：拒绝必填意见是因为
"必须说明拒绝理由"这一业务语义（spec.md "审批拒绝" 需求已经写明"必须携带非空的拒绝意见"），
批准没有对应的既有业务约束，强行改成必填属于扩大需求范围之外的行为变更，且要同时改
`ApprovalOpinionRequest` 校验注解（会影响 `WorkflowTaskController` 的 `ApproveRequest`
是否也要同步改，牵连面更大）。如果用户希望批准也强制填写意见，可以在确认阶段提出，作为
本 change 范围内的一个小调整（后端加 `@NotBlank`，工作量很小），而不是默认这么做。

### 2. `ApprovalRequestVO` 新增 `processInstanceId`

`ApprovalRequestServiceImpl.toVO(entity, displayNames)` 里补一行
`vo.setProcessInstanceId(entity.getProcessInstanceId())`。`entity` 本身已经有这个字段
（`submit()`/后续各终态分支都会写），只是转换成 VO 时被漏掉了，纯粹是补齐已有数据的
暴露面，没有新的数据来源。

### 3. `getProcessDetail` 参与关系校验（落实 approval-runtime-safety 既有约束）

新增私有方法 `requireViewer(ProcessInstanceEntity instance, Long userId)`，满足以下任一
条件即放行，否则抛 `BusinessException`（无权限）：

1. `userId` 等于 `instance.getApplicantId()`（申请人本人）；
2. `userId` 出现在该实例 `records`（`tab_wf_approval_record`）任意一条的 `operatorId`
   或 `fromUserId` 中（历史上处理过、或曾是转办/委派来源的人）；
3. `userId` 对该实例当前任一开放任务满足 `TaskAuthorizationService.isAuthorized(task,
   userId)`（复用既有单任务候选人判定，遍历 `findOpenTasks(instance.getId())` 或等价查询，
   命中任意一个即放行——覆盖"当前候选人但尚未处理过、所以不在 records 里"的场景，这正是
   "待我审批"最常见的情形）。

三条規則合起来准确覆盖"我的申请"（条件1）与"待我审批"（条件3；条件2 覆盖"曾经审批过、
现在看已办历史"这种未来可能出现的已办页面复用场景）两个当前页面的全部合法访问路径。

**未采用的备选**：直接复用某个 RBAC 权限点（如 `ApprovalManagement:request:approve`）做
校验。未采用原因：RBAC 权限点是"能不能进这个功能模块"的粗粒度门禁，`详情...SHALL 按参与
关系...返回` 这句 spec 文字明确要求的是**数据行级别**的校验——持有 `request:approve`
权限点的审批人 A 不应该能看到跟自己完全无关、由审批人 B 处理的另一条申请的详情，两者是
正交的两层校验，不能互相替代（`production-approval-lifecycle` change design.md 第8节已有
同样的区分先例："任务动作授权为'接口固定权限 ∩ 当前任务资格 ∩ 数据范围 ∩ 节点允许动作'"）。

### 4. 只读节点/连线图 DTO 与状态计算

新增（暂定命名，具体以实现时接口现状核实为准）：

```java
public class ProcessGraphNodeVO {
    private String id;
    private String type;   // START/APPROVAL/CONDITION/PARALLEL_SPLIT/PARALLEL_JOIN/CC/AUTO/END
    private String name;
    private Double x;
    private Double y;
    private String status; // COMPLETED / CURRENT / PENDING
    private List<ApprovalRecordVO> records; // 该节点关联的历史轨迹条目，status=PENDING 时为空
}

public class ProcessGraphEdgeVO {
    private String id;
    private String source;
    private String target;
    private String label;  // 条件分支说明（如"默认分支"/条件文本摘要），普通边为空
}
```

`ProcessInstanceDetailVO` 新增 `nodes`/`edges` 两个字段承载上述结构。组装逻辑：

1. 按 `instance.getProcessDefinitionId()` 查 `ProcessDefinitionEntity`，取
   `modelJsonSnapshot` 与 `schemaVersion`。
2. 按 `schemaVersion` 反序列化为 `ProcessModelDsl`（v1）或 `ProcessModelDslV2`（v2），映射
   出 `nodes`/`edges` 的基本信息（id/type/name/position），v1/v2 两套映射函数各自独立、
   结构小，不强行合并成一套（参考 `fix-approval-zero-task-process-completion` change
   design.md 里"两个方法各自职责单一"的同类取舍）。
3. 状态计算：`records` 里出现过的 `nodeId` → `COMPLETED`（附带对应的
   `ApprovalRecordVO` 列表，允许同一节点出现多条记录，如会签场景多人审批）；
   `openNodes`/`currentNodeId` 命中的 `nodeId` → `CURRENT`；其余 → `PENDING`。

### 5. 不精确预测条件分支之后的路径（Non-Goal 的具体取舍）

流程发起时，路由用的字段值（`route_field_codes` 对应的提交数据）会被冻结进 Flowable 流程
变量，条件分支的走向理论上是确定性的、原则上可以用既有的 `ConditionAstEvaluator` 重放
计算出来。但本次不实现这一层"精确预测未到达的条件分支"，原因：

- 会签/并行节点的"是否通过"取决于尚未发生的人工投票结果，不取决于提交数据，无法用同样的
  方式预测——即使条件分支能精确预测，并行结构之后的部分依然只能是"结构性展示"，两者混在
  一起容易给用户"系统精确知道后续所有步骤"的错误印象。
- 需要读取 Flowable 历史流程变量、重新执行一遍编译期条件规则、并处理"路由字段在会签汇合
  之后又依赖另一个尚未完成的并行分支"这类组合场景，工作量与本次"看得到完整拓扑 + 当前
  位置 + 已发生轨迹"这一核心诉求不成比例。

如果用户后续认为"精确预测剩余步骤"是必须的，可以作为独立后续 change 处理，不阻塞本次交付
的核心可见性诉求。

### 6. 前端渲染：一级一级的分级列表，不用拓扑图（2026-09-17 修订）

**修订说明**：本节原方案是用 `VueFlow` 画完整节点/连线拓扑图（见下方"已废弃的原方案"）。
已实现并经用户在浏览器里实际查看后反馈"按照图示不直观"，改为本节描述的分级列表方案；
后端 `nodes`/`edges` 数据结构（design.md Decision 4）不变，只改前端 `ProcessFlowChart.vue`
及其子组件的展示形式，`nodes[].status`（COMPLETED/CURRENT/PENDING）语义不变。

`ApprovalRequestDetailDialog.vue`"审批流程"折叠区块改为：

- 顶部一行文字摘要保持不变："当前节点：<currentNodeName>"（流程已结束时展示"已结束
  （已通过/已拒绝）"）。
- 下方改为**按审批级别一级一级纵向展示的列表/步骤条**，不再是可缩放拖拽的图形画布：
  1. 复用 `utils/processGraphLayout.ts` 里已经写好的 Kahn 拓扑排序，但不再用它计算像素
     坐标——改为只算"层号"（level），同一层号的节点归为同一"级"，按层号升序纵向排列
     （原有 `computeAutoLayout` 的像素定位逻辑不再需要，替换为一个只返回
     `Map<nodeId, level>` 或 `level -> node[]` 分组结果的新函数；v1/v2 流程统一走这一套，
     不再区分"是否有服务端坐标"）。
  2. 每一级渲染成一个列表分组（如"第 N 级"或直接用该级节点名称做标题，同级出现多个节点时
     —— 并行分叉常见场景——横向并排或纵向堆叠展示在同一分组内，具体样式实现时定，不强行
     做成两列表格）。
  3. 单个节点项按 `status` 三态区分：
     - `CURRENT`（当前审批点）：高亮突出（如强调色边框/背景、"进行中"标签），是本次改版
       用户明确要求的重点。
     - `COMPLETED`（已完成）：正常展示（不置灰），可点击展开/弹出该节点的审批人、时间、
       意见（数据已在 `ProcessGraphNodeVO.records` 里，不需要额外请求，交互方式沿用原方案
       "点击查看审批记录"）。
     - `PENDING`（未到达/未审批）：置灰展示（用户本次明确要求），不可点击、不展示
       `records`（本来也是空）。
  4. `CONDITION`/`PARALLEL_SPLIT`/`PARALLEL_JOIN`/`CC`/`AUTO` 等非"审批节点"类型继续按各自
     类型标签展示（复用既有 `typeLabels.ts`/`PROCESS_GRAPH_NODE_TYPE_LABEL`），不单独隐藏，
     保持"完整流程步骤"的可见性，只是不再用图形连线表达它们之间的分支关系——条件分支的
     说明文字（`edges[].label`）可以作为该级下一级列表项的小字注释，具体呈现方式实现时定。
  5. 不再需要 Vue Flow 画布交互（缩放、拖拽视口、`Background`/`Controls`）；`@vue-flow/*`
     依赖本身不动（设计器画布 `views/workflow/designer/` 仍在用），只是这个只读详情场景不
     再依赖它。
- 移动端/窄屏：折叠区块默认收起，展开后列表在 `overflow: auto` 容器内滚动，不强行压缩到
  单屏塞不下的程度。

#### 已废弃的原方案（保留记录，不再实施）

~~下方用 `VueFlow`（只读：不注册拖拽/连线/删除交互）渲染 `nodes`/`edges`，节点组件复用/
包装 `views/workflow/designer/nodes/*.vue`，按 `status` 加不同的边框色/图标（已完成绿色、
进行中蓝色高亮、未到达灰色），点击已完成节点弹出审批人/时间/意见。~~ 已实现并验证可用
（`ProcessFlowChart.vue` + `components/processFlowChart/nodes/Flow*.vue` +
`utils/processGraphLayout.ts` 的像素坐标版本），因不够直观被替换，替换时这批文件按需
删除/大幅改写，不保留两套并存。

### 7. 当前节点展示候选审批人/审批管理员信息（2026-09-17 追加，用户新提出的需求）

Decision 4 的 `records`（历史审批轨迹）只在节点 `status=COMPLETED` 时非空——`CURRENT`
（当前正在等待处理的）节点此前没有任何"谁能处理/谁正在处理"的信息，用户要求补上。

**数据来源（已通过只读代码走查确认，不是新造数据）**：

- `ApprovalTaskEntity`（`tab_wf_approval_task`）：`assigneeId` 非空表示任务已被认领/单人
  节点直接指定，该 id 就是"当前处理人"；为空表示候选组任务尚未认领。
- `ApprovalTaskCandidateEntity`（`tab_wf_approval_task_candidate`）：未认领任务的候选人
  明细，每行 `candidateType`（`USER`/`ROLE`）+ `candidateValue` + `resolveBasis`（已经是
  现成的可读解析依据文案，如"角色 SECURITY_ADMIN 命中管理员 3 人"，
  production-approval-lifecycle change tasks.md 5.4 已经落地）。`TaskAuthorizationService`
  已经在用这张表做越权校验，本次只是新增一条"读出来展示给最终用户看"的路径，不新增数据
  来源。

**DTO 设计**：`ProcessGraphNodeVO` 新增一个字段（仅 `status=CURRENT` 时非空，其余状态为
空列表）：

```java
public class ProcessGraphNodeVO {
    // ... 既有字段不变 ...
    private List<CurrentApproverVO> currentApprovers; // 仅 CURRENT 状态非空
}

public class CurrentApproverVO {
    private Long userId;        // 已认领/USER 类型候选人时非空
    private String userName;    // 已认领/USER 类型候选人时非空，展示名
    private String roleCode;    // ROLE 类型候选人时非空
    private String roleName;    // ROLE 类型候选人时非空，角色展示名（查 tab_role.name）
    private boolean assigned;   // true=已认领的指定处理人，false=候选人（尚未认领）
}
```

**（2026-09-17 二次修订，用户简化展示要求）**：角色类型候选人 **仅展示角色名称和编码**，
不展示 `resolveBasis` 解析依据说明，因此 `CurrentApproverVO` 不需要 `resolveBasis` 字段
（上面的字段列表已经去掉了这个字段，仅保留 `roleCode`/`roleName`）；`ApprovalTaskCandidateEntity
.resolveBasis` 仍然存在于数据库、继续供运维/审计场景使用，只是不再读出来给这个接口用。

**组装逻辑**（`WorkflowTaskServiceImpl.resolveGraphNodes` 补充，只对 `status=CURRENT` 的
节点执行）：按 `nodeId` 找到该节点当前的开放任务（已经有 `openTasks` 可用，无需新查询）；
对每个开放任务：`assigneeId` 非空 → 生成一条 `assigned=true` 的记录（复用既有
`userDisplayService.resolveDisplayNames` 批量解析展示名，与 `records`/`applicantName` 走
同一条批量解析路径，不额外发起单条查询）；`assigneeId` 为空 → 查
`ApprovalTaskCandidateMapper` 按 `taskId` 取候选人明细，`USER` 类型解析展示名，`ROLE`
类型查 `RoleMapper`（按 `code`）取角色 `name`，`assigned=false`。**不展开角色候选人背后的
具体人员列表**——展开成具体人名列表需要额外调用角色 → 用户成员解析
（`AdminRoleLookupService` 目前只有"某用户是否具备某角色"的正向查询，没有"某角色下有哪些
用户"的反向查询接口），且候选人数量在某些角色下可能较多，不适合都堆在一个节点卡片里；
如果用户后续认为需要展开到具体人名，留作独立后续 change。

**前端展示**（Decision 6 分级列表的延伸）：`CURRENT` 状态的列表项在原有"进行中"高亮基础上
追加一小段"审批人"信息——`assigned=true` 的条目展示为"处理人：<userName>"；`assigned=false`
的候选人条目展示为"候选审批人：<userName>"（USER 类型）或"候选审批人：<roleName>
（<roleCode>）"（ROLE 类型，只展示名称+编码，不需要额外的悬浮/点击说明交互）；多个候选人
逗号分隔或每行一个，具体样式实现时定。

### 8. 详情弹窗改为左右切换 tab，不再整页纵向堆叠（2026-09-17 三次追加，用户反馈"下拉太长"）

`ApprovalRequestDetailDialog.vue` 目前是"基础信息（`el-descriptions`）→ 变更内容/申请内容
（`fieldRows` 列表）→ 任职信息（`userPositions`，仅 USER 类型）→ 审批流程（折叠面板，本
change 新增）"四段纵向堆叠，弹窗内容一多就要一直往下滚动，尤其 UPDATE 类申请的新旧字段
对照 + 审批流程分级列表叠在一起。改为 `el-tabs`（默认 `tab-position="top"`，横向 tab 栏，
点击切换，不做成 `tab-position="left"` 的侧边竖排菜单——"左右切换"按常规理解就是普通横向
tab 栏切换内容区，不是另加一条侧边导航）：

- **"基本信息"tab**：现有 `el-descriptions` 那一段（业务对象类型/操作类型/目标记录 id/
  生效记录 id/申请状态/提交人/提交时间/审批人/审批时间/审批意见），内容本身不长，保持原样
  平铺展示，不需要再拆子 tab。
- **"申请内容"tab**：现有 `fieldRows` 字段列表 + `userPositions` 任职信息卡片（USER 类型）
  合并到这一个 tab——两者本来就是同一件事"这条申请具体改了什么"，没必要拆成两个 tab。
  `fieldRows.length === 0 && !userPositions` 时展示的"该操作不涉及字段变更..."提示语也在
  这个 tab 里。
- **"审批流程"tab**：仅 `row.processInstanceId` 非空时展示这个 tab（沿用现状的
  `v-if="row.processInstanceId"` 判断，没有关联流程实例时不出现这个 tab，不展示空 tab）；
  内容就是现有的 `ProcessFlowChart` 组件，不再套一层 `el-collapse`（改成 tab 之后不需要
  "默认收起"这层折叠了，切到这个 tab 本身就是用户主动展开的动作，`processFlowActiveNames`
  这个状态可以删掉）。
- 每次打开弹窗（或切换到不同 `row`）默认停在"基本信息"tab，不记忆上一条申请最后停留的
  tab（避免"上一条看的是审批流程 tab，切到下一条申请也直接跳到审批流程"这种違反直觉的
  状态残留）。
- "审批流程"tab 拉取流程实例详情的既有 `watch` 逻辑不变（弹窗打开/`processInstanceId`
  变化时请求，弹窗关闭时清空）——只是触发时机上不再受"用户有没有展开折叠面板"影响，
  改成只要弹窗打开、且这条申请有关联流程实例就直接请求（与现状一致，因为现状本来就是
  "折叠区块默认收起但数据仍然预取"，改 tab 后行为不变，只是外层容器从 `el-collapse` 换成
  `el-tabs`）。

### 9. 审批流程条件分支说明改为可读文案，不展示原始字段/运算符/值（2026-09-17 四次追加）

**现状问题**：`ProcessGraphAssembler`（后端）现在把条件分支拼成一段原始文本塞进
`ProcessGraphEdgeVO.label`——v1 `describeConditionV1` 是
`condition.getField() + " " + condition.getOperator() + " " + condition.getValue()`
（如"gender EQ female"），v2 `describeConditionV2` 类似（"字段 op 值"按 AND/OR 拼接）。
用户要求展示"性别 等于 女"这种可读文案，不能是原始字段编码/运算符字面量/原始值。

**方案**：不在后端拼接文本，而是把结构化的条件数据原样传给前端，复用前端已经在用的表单
字段渲染元数据（`ApprovalRequestDetailDialog.vue` 顶部已经为 ORG/USER/POSITION/APP 四类
拉取了一份 `useDynamicFormFields`，字段展示名和字典值翻译（`labelFor`/`displayValue`/
`dictOptionLabel`）都已经现成可用，不需要后端重新实现一遍字段元数据查询）。

- **后端**：`ProcessGraphEdgeVO` 去掉拼好的 `label: String` 字段（或保留字段名但语义改成
  "结构化条件列表"，具体是加字段还是换字段实现时定，接口层面对前端是破坏性变化，两者选
  一个，不要同时保留新旧两套），改为：
  ```java
  public class ConditionItemVO {
      private String fieldBizType; // 字段所属业务对象类型：ORG/USER/POSITION/APP
      private String field;        // 字段编码（fieldCode），前端按此查渲染元数据取展示名
      private String operator;     // 统一取值 EQ/NE/GT/GE/LT/LE/IN/IS_NULL（下面"运算符
                                    // 归一化"说明）
      private Object value;        // 原始比较值，不做任何格式化，前端按字段的渲染元数据
                                    // （字典/布尔/其余）翻译成可读文案，复用既有 displayValue
                                    // 同一套逻辑
  }
  ```
  `ProcessGraphEdgeVO` 新增 `conditions: List<ConditionItemVO>`（无条件的默认/兜底分支为空
  列表）+ `conditionLogic: String`（`AND`/`OR`，`conditions.size() <= 1` 时可为空/无意义）。
  **运算符归一化**：v1 `EdgeConditionDsl.operator` 用的字面量是 `GTE`/`LTE`，v2
  `ConditionItemDsl.op`（`ConditionOperator` 枚举）用的是 `GE`/`LE`——两套 DSL 对"大于等于/
  小于等于"用了不同的字面量拼写。后端组装 `ConditionItemVO.operator` 时统一归一化成 v2 的
  枚举字面量（`EQ`/`NE`/`GT`/`GE`/`LT`/`LE`/`IN`/`IS_NULL`），前端只需要维护一套运算符→
  中文的映射，不用同时兼容两种拼写。**`fieldBizType` 来源**：v1 `EdgeConditionDsl` 本身就有
  显式的 `fieldBizType` 字段，直接取；v2 `ConditionItemDsl` 没有这个字段（核实过，
  `cn.nihility.rbac.workflow.dslv2.dto` 包下搜不到 `fieldBizType`），实现时需要核实 v2 的
  条件字段是否恒等于该流程绑定的业务对象类型（`ProcessInstanceEntity.businessType`）——
  如果核实后确实恒等，直接用 `instance.getBusinessType()` 兜底；如果发现 v2 允许跨
  bizType 引用字段，需要另外找一个字段来源，不能凭假设硬编码。

  **实现后核实结论（2026-09-17 五次追加）**：v2 的"条件字段恒等于流程绑定业务对象类型"这一
  假设**不是代码层面强制的约束，只是当前代码库里唯一可用的兜底信息来源**，具体核实到的
  事实：(1) `ProcessModelDslV2Validator.validateConditionAst` 只校验 `field` 非空/`op`
  非空/`value`（`IS_NULL` 除外）非空，完全不校验 `field` 是否属于任何特定业务对象类型的
  表单字段定义（对照 v1 `WorkflowModelCompilerImpl.resolveControlType` 会显式按
  `fieldBizType` 查表单字段定义、查不到直接编译失败，v2 没有这一层校验）；(2)
  `WorkflowModelCompilerV2`/`ConditionAstCompiler` 编译条件表达式时，UEL 变量名直接用裸
  `field`（如 `${(riskLevel == 'HIGH')}`），不像 v1 `WorkflowModelCompilerImpl
  .buildConditionExpression` 那样拼成 `fieldBizType_field` 变量名；(3) v2 的
  发布落库路径 `WorkflowProcessModelServiceImpl.publishV2` 从不写
  `ProcessDefinitionEntity.routeFieldCodes`（该列只在 v1 `publishV1` 路径写入），
  `ApprovalProcessServiceImpl.buildRouteVariables`（唯一从提交表单数据按
  `bizType_fieldCode` 组装 Flowable 流程变量的地方）对 v2 流程恒返回 `null`，即 v2 条件
  分支在当前代码库里事实上还没有接到"按提交表单数据路由"的生产链路
  （`WorkflowModelCompilerV2IntegrationTest` 里是测试代码直接用
  `Map.of("riskLevel", "HIGH")` 手工构造流程变量验证编译产物，不经过
  `ApprovalProcessServiceImpl.start`）；(4) 前端目前没有任何 v2 流程设计器 UI（`grep
  schemaVersion` 在 `frontend/src` 下零匹配），`ConditionItemDsl.field` 目前只能通过直接
  写 DSL JSON（人工/测试）产生，不存在"设计器强制只能选同一 bizType 字段"这类产品层面的
  隐性约束来源。综合以上，`instanceBizType` 兜底是当前唯一可行且不引入新数据源的选择，
  已按此实现（`ProcessGraphAssembler.assemble` 新增 `instanceBizType` 入参），但这是一个
  "尽力而为的近似值"而非已验证的不变量——如果未来 v2 条件分支真正接入按提交数据路由的
  生产链路且允许跨 bizType 引用字段，这里需要重新评估数据来源，不在本次范围内展开。
- **前端**：`ProcessFlowChart.vue` 目前展示条件说明是直接拿 `edges[].label` 文本
  （`conditionAnnotations` 那个 computed）。改为拿到 `edges[].conditions`/`conditionLogic`
  后，对每个 `ConditionItemVO`：`field` 按 `fieldBizType` 到对应的渲染元数据里查展示名（复用
  `ApprovalRequestDetailDialog.vue` 已有的 `labelFor(bizType, field)` 逻辑——但这个函数目前
  定义在父组件里，`ProcessFlowChart.vue` 是子组件、本身不持有四份渲染元数据，需要父组件把
  解析函数（或者干脆把已加载的四份 `DynamicFields` 一起）作为 prop 传下去，不在子组件里
  重新发起一遍四个 bizType 的元数据请求）；`operator` 按一份新增的静态中文映射表（`EQ→等于`/
  `NE→不等于`/`GT→大于`/`GE→大于等于`/`LT→小于`/`LE→小于等于`/`IN→属于`/`IS_NULL→为空`）
  转换；`value` 复用父组件已有的 `displayValue(bizType, item, raw)`（字典字段翻译成选项
  文案、布尔转"是/否"，其余原样展示）。多个条件项按 `conditionLogic` 用"且"/"或"拼接
  （如"性别 等于 女 且 年龄 大于 18"），单条件项不需要连接词。

## Risks / Trade-offs

- [`getProcessDetail` 补上参与关系校验属于收紧现有行为] → proposal.md Impact 已确认目前
  没有任何调用方依赖"无校验可查任意实例"这一未文档化行为，属于安全加固，不是破坏性变更；
  仍建议在实现完成后跑一次仓库内对该接口的既有测试/集成测试确认无回归。
- [v1/v2 两套 DSL 图映射逻辑增加一点重复代码] → 与
  `fix-approval-zero-task-process-completion` change 同类场景一致的取舍：两套 schema
  差异是本次要处理的核心，体量小，拆两个独立方法比硬塞一个参数化方法更好维护。
- [并行/会签节点在图上如何清晰表达"多人票数进度"，本次只做结构展示不做票数进度条] →
  MVP 范围内只展示节点状态三态（已完成/进行中/未到达），会签节点内部票数详情可以点击后从
  已有的 `records`/`openNodes` 数据里读到，不在图上额外画进度条，作为后续可选增强。
- [Vue Flow 只读渲染在详情弹窗这种有限空间里可能拥挤] → 折叠展开 + 容器内可滚动/可缩放
  （`Controls` 组件已支持缩放），不追求默认铺满弹窗。

## Migration Plan

1. 后端：`ApprovalRequestVO` 补 `processInstanceId`；`WorkflowTaskServiceImpl
   .getProcessDetail` 补参与关系校验；新增 `ProcessGraphNodeVO`/`ProcessGraphEdgeVO` 与
   DSL→图的映射逻辑（v1/v2 各一个），`ProcessInstanceDetailVO` 补 `nodes`/`edges` 字段。
2. 后端测试：参与关系校验的正/反向用例（申请人本人、当前候选人、历史操作人 → 放行；无关
   用户 → 拒绝）；v1/v2 两套 DSL 定义各自的图映射正确性；节点状态三态（已完成/进行中/
   未到达）在含条件分支、并行分叉的真实流程里计算正确。
3. 前端：批准弹窗（新增，意见可选）；新增 API/类型承接 `processInstanceId` 与流程实例
   详情接口；`ApprovalRequestDetailDialog.vue` 新增审批流程区块（摘要文字 + Vue Flow
   只读图 + 已完成节点可查看轨迹详情）。
4. 本地启动前后端手动验证：批准时填写/不填写意见均能提交成功、意见正确落库并在"已办"/
   详情里可见；详情弹窗能看到当前节点、完整拓扑（含未到达的后续节点）、已发生轨迹；用一个
   与该申请无关的账号尝试直接调用 `GET /api/v1/workflow/process-instances/{id}` 确认被拒。
5. 运行现有回归测试（`approval.*`、`workflow.*`），确认无回归。
6. 实施结束后依真实 diff/测试结果同步本 change 的 `proposal.md`/`design.md`/`tasks.md`。
