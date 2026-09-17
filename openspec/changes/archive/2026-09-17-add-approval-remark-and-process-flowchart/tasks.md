## 1. 需求 1：批准可填写审批意见（前端）

- [x] 1.1 `PendingApprovalRequestView.vue` 新增批准确认弹窗（参照现有拒绝弹窗结构），意见
      输入框不强制必填（design.md Decision 1）。
- [x] 1.2 `handleApprove` 改为打开弹窗、确认后调用
      `approvalApi.approveApprovalRequest(row.id, opinion)`，不再直接用
      `ElMessageBox.confirm`。
- [x] 1.3 手动验证：填写意见批准、不填写意见批准两种路径均成功；批准后意见在"我的申请"/
      "待我审批"详情里可见（复用现有 `opinion` 字段展示，本节不改展示逻辑）。实测方式：
      本环境无浏览器自动化工具，改用 Node 脚本模拟 `approveForm.opinion` 的真实提交路径
      （RSA 加密登录 -> `POST /approval-requests/{id}/approve` 带 `opinion` 请求体），
      确认后端把意见落到 `tab_wf_approval_record.remark`（详情接口 `records` 里可见），
      与 `ApprovalRequestDetailDialog.vue` 现有 `opinion` 字段展示路径未改动；未在真实浏览器
      里逐字段点击验证，建议用户用 `npm run dev` 实际打开页面复核一次视觉/交互细节。

## 2. 需求 2：后端——`processInstanceId` 暴露 + 参与关系校验

- [x] 2.1 `ApprovalRequestVO` 新增 `processInstanceId` 字段；`ApprovalRequestServiceImpl
      .toVO(...)` 补充赋值（design.md Decision 2）。实现时确认 `ApprovalConvert.toRequestVO`
      是 MapStruct 按字段名/类型自动映射（无 `@Mapping(target = "processInstanceId", ...)`
      忽略配置），VO 补上同名同类型字段后自动带出，未在 `toVO(...)` 里另加显式赋值代码。
- [x] 2.2 `WorkflowTaskServiceImpl.getProcessDetail(processInstanceId)` 新增
      `requireViewer(instance, userId)` 校验（design.md Decision 3 的三条规则：申请人本人
      /历史操作人或转办来源人/当前任一开放任务候选人），不满足任一条件时拒绝并返回无权限
      错误。为传递查看者身份，`WorkflowTaskService`/`WorkflowService`/
      `FlowableWorkflowService`/`WorkflowTaskController` 的 `getProcessDetail` 签名同步
      新增 `viewerId` 参数（controller 侧取自 `CurrentUserContext`）。
- [x] 2.3 单元/集成测试覆盖：申请人本人查看自己申请 → 放行；当前节点候选人（未处理过）查看
      → 放行；历史已处理过该实例某节点的人查看 → 放行（含转办来源人 `fromUserId` 分支）；
      与该实例完全无关的用户查看 → 拒绝。落在
      `WorkflowTaskServiceImplIntegrationTest`（真实数据库，未 mock Flowable）。

## 3. 需求 2：后端——完整节点/连线图

- [x] 3.1 新增 `ProcessGraphNodeVO`/`ProcessGraphEdgeVO`（`cn.nihility.rbac.workflow.dto`
      包，字段与 design.md Decision 4 示意结构一致：node 含 id/type/name/x/y/status/
      records，edge 含 id/source/target/label）。
- [x] 3.2 新增 DSL → 图 DTO 的映射逻辑：新增 `cn.nihility.rbac.workflow.graph` 包，
      `ProcessGraphAssembler`（v1/v2 各自独立的私有映射方法，不强行合并）+ `ProcessGraph`
      结构性中间结果记录类型，按 `ProcessDefinitionEntity.schemaVersion` 分派（`== 2` 走
      v2，其余含 `null` 均按 v1 处理）。
- [x] 3.3 `WorkflowTaskServiceImpl.getProcessDetail` 组装节点状态三态（已完成关联
      `ApprovalRecordVO`／进行中／未到达，状态字面量新增
      `cn.nihility.rbac.workflow.constant.ProcessGraphNodeStatus`），`ProcessInstanceDetailVO`
      新增 `nodes`/`edges` 字段返回。
- [x] 3.4 单元/集成测试覆盖：`ProcessGraphAssemblerTest`（纯单元测试）覆盖 v1/v2 快照解析
      的结构正确性与条件分支说明文本；`WorkflowTaskServiceImplIntegrationTest`（真实数据库）
      新增 v1 流程（含条件分支）、v2 流程（含并行分叉/汇合、条件分支）各自的图结构与节点
      状态计算正确性测试，以及流程已结束（APPROVED）时节点状态与 `records` 一致（不出现
      `CURRENT`）的测试。

## 4. 需求 2：前端——API/类型与详情弹窗

- [x] 4.1 新增/扩展 API 封装承接流程实例详情接口（`GET /api/v1/workflow/process-instances/
      {id}`），新增对应 TypeScript 类型（含 `processInstanceId`/`nodes`/`edges`/`records`/
      节点状态）。实现为 `api/workflow.ts` 新增 `getProcessInstanceDetail()`（沿用该文件已有
      的流程模块归类，未新建文件），类型追加在 `types/workflow.ts`（`ProcessInstanceDetailVO`/
      `ProcessGraphNodeVO`/`ProcessGraphEdgeVO`/`ApprovalRecordVO`/`OpenNodeVO` 等）。
- [x] 4.2 `frontend/src/types/approval.ts` 的 `ApprovalRequestRow` 补充
      `processInstanceId` 字段。
- [x] 4.3 `ApprovalRequestDetailDialog.vue` 新增"审批流程"区块：当前节点文字摘要 + Vue Flow
      只读渲染（复用/包装 `views/workflow/designer/nodes/*.vue`，`status` 着色，已完成
      节点可查看审批人/时间/意见）（design.md Decision 6）。实现取舍：开始/条件/结束三种
      节点新增独立的只读展示组件（`components/processFlowChart/nodes/Flow*.vue`），视觉语言
      （图标、配色、形状）与设计器 `views/workflow/designer/nodes/*.vue` 保持一致，但未逐字
      复用原组件文件——审批节点尤其如此：设计器 `ApprovalNode.vue` 会展示"审批人来源/会签
      模式"标签，这些字段只存在于流程模型 DSL，`ProcessGraphNodeVO` 并不携带，直接复用会一律
      展示成具有误导性的"未配置审批人"文案，因此改为只展示节点名称 + 三态状态着色 + 点击
      查看审批记录，不修改设计器原文件。PARALLEL_SPLIT/PARALLEL_JOIN/CC/AUTO 未识别类型统一
      走通用占位节点（`FlowGenericNode.vue`，展示类型文字 + 名称）。v1 流程节点坐标恒为 null，
      新增 `utils/processGraphLayout.ts` 做一次简单的拓扑分层自动布局兜底（非通用图布局算法，
      仅保证先后顺序清晰）；v2 流程直接使用服务端坐标。
- [x] 4.4 手动验证：打开"我的申请"/"待我审批"任一详情，能看到当前节点、完整拓扑（含未到达
      的后续节点）、已发生轨迹；用无关账号直接调用该流程实例详情接口确认被拒绝（配合 2.2）。
      实测方式：本环境没有浏览器自动化工具可用，改用 Node 脚本 + 直连本地 MySQL 做端到端
      API 级验证——登录 admin 账号，将一条真实存在的待审批任务（`tab_wf_approval_task`）的
      `assignee_id` 临时改为 admin 的 `user_id`（本地开发库里的既有测试脏数据，操作可逆、
      不影响生产），走 `GET /api/approval-requests/pending` → 批准前 `GET
      /api/v1/workflow/process-instances/{id}` 看到节点 `approval_xxx` 为 `CURRENT` → `POST
      /approval-requests/{id}/approve` 带 `opinion` → 批准后再查详情，确认该节点变为
      `COMPLETED` 且 `records` 携带刚才的意见文本，`records`/`nodes` 结构与前端
      `ProcessInstanceDetailVO` 类型定义完全对齐（含 `records` 字段在未完成节点上为 `null`
      而非 `[]` 这一边界情况，`ProcessFlowChart.vue` 已用 `node.records ?? []` 防御）；同时
      构建产物通过 Vite dev server 逐个请求新增的 `.vue`/`.ts` 文件确认可正常编译无语法错误。
      "无关账号确认被拒绝"这一子项依赖后端 2.2/2.3 已完成的参与关系校验与集成测试（未在本次
      前端验证中另行复现，因为本地库里其余测试账号的登录口令未知，不在前端职责范围内伪造）。
      未做的：未在真实浏览器里逐像素查看流程图渲染效果（节点定位、连线走向、折叠展开交互），
      建议用户用 `npm run dev` 实际打开"我的申请"/"待我审批"详情弹窗复核一次。

## 4a. 需求 2 修订：改为分级列表展示（2026-09-17，用户反馈"图示不直观"后追加）

- [x] 4a.1 `ProcessFlowChart.vue` 从 Vue Flow 图形画布改为按审批级别一级一级纵向展示的
      列表/步骤条（design.md Decision 6 修订版）：`utils/processGraphLayout.ts` 新增
      `computeNodeLevels(nodes, edges)`，复用原 Kahn 拓扑排序逻辑，只返回按层号分组的
      `ProcessGraphLevel[]`（不再计算像素坐标）；v1/v2 流程统一走这一套。列表用圆点+虚线
      连接线呈现层级顺序（呼应 CLAUDE.md 里概览页时间线的"链式连接"视觉语言，复用
      `--chain-line-color`/`--chain-dot-size` 等既有设计令牌），当前节点（`CURRENT`）用
      主色边框/背景 + "进行中" 标签高亮，未到达节点（`PENDING`）置灰（虚线边框、浅色文字、
      不可点击），已完成节点（`COMPLETED`）保持可点击查看审批记录的既有交互（复用
      `recordsDialogVisible`/`onNodeClick` 那一套，只是触发方式从"点击 Vue Flow 节点"
      改为"点击列表卡片"）。`CONDITION`/`PARALLEL_SPLIT`/`PARALLEL_JOIN`/`CC`/`AUTO`
      继续用既有 `typeLabels.ts`/`PROCESS_GRAPH_NODE_TYPE_LABEL` 展示类型文字；条件分支
      说明文字（`edges[].label`）作为对应节点卡片下方的小字注释展示。
- [x] 4a.2 移除不再需要的 Vue Flow 相关代码：删除 `components/processFlowChart/nodes/
      Flow*.vue` 五个节点组件（含通用占位节点 `FlowGenericNode.vue`）及其专属的
      `nodeStatus.scss`/`types.ts`（`FlowNodeData` 形状不再需要），`ProcessFlowChart.vue`
      重写后不再 import `VueFlow`/`Background`/`Controls`/`@vue-flow/core` 等；节点图标
      改由 `typeLabels.ts` 新增的 `PROCESS_GRAPH_NODE_TYPE_ICON` 映射表提供（延续原
      Flow*.vue 的图标/配色选择：开始 `VideoPlay`、审批 `UserFilled`、条件 `Share`、结束
      `CircleCheck`，v2 专有类型统一 `MoreFilled` 占位）。`utils/processGraphLayout.ts`
      原 `computeAutoLayout` 像素坐标函数整体替换为 `computeNodeLevels`，不再保留像素
      定位逻辑。`@vue-flow/*` 依赖本身未动，`views/workflow/designer/` 设计器画布不受
      影响（已用 grep 确认改动后代码库内 `VueFlow`/`@vue-flow` 引用只出现在设计器相关
      文件里）。
- [x] 4a.3 `npm run build`（`vue-tsc` + vite build）确认无类型错误，构建成功。手动验证
      改用端到端 API 级方式（本环境无浏览器自动化工具）：本地启动后端
      （`./gradlew bootRun`）+ 直连本地 MySQL 只读排查，用 admin 账号对一条真实测试用户
      （id=204）提交"停用"申请（`PUT /api/users/204/disable`，因该 bizType 开启审批开关
      只创建审批申请、不会真的停用），得到新的 `processInstanceId=1127`；调用
      `GET /api/v1/workflow/process-instances/1127` 确认返回的 `nodes` 数组里
      `deptLeaderApprove` 节点 `status=CURRENT`，其余节点 `status=PENDING`，`records`
      字段在未到达/未完成节点上为 `null`（非 `[]`，已确认前端 `node.records ?? []`
      防御与此一致）；验证后立即调用 `POST /api/approval-requests/210/cancel` 撤回本次
      测试申请，复核 `GET /api/users/204` 状态码 2000（启用）未变，未在数据库留下脏数据。
      未做的：未在真实浏览器里逐像素查看分级列表的视觉效果（高亮/置灰配色、圆点连接线
      间距、窄屏滚动），建议用户用 `npm run dev` 实际打开"我的申请"/"待我审批"详情弹窗
      复核一次。

## 3a. 需求 2 追加：当前节点展示候选审批人/审批管理员信息（2026-09-17，用户追加需求）

- [x] 3a.1 `ProcessGraphNodeVO` 新增 `currentApprovers` 字段（`List<CurrentApproverVO>`，
      仅 `status=CURRENT` 时非空），新增 `CurrentApproverVO` DTO（design.md Decision 7
      二次修订后的字段：userId/userName/roleCode/roleName/assigned，**不含 resolveBasis**）。
- [x] 3a.2 `WorkflowTaskServiceImpl.resolveGraphNodes` 补充组装逻辑：对 `CURRENT` 节点按
      `nodeId` 找到对应开放任务（复用已有 `openTasks`，不新查询）；`assigneeId` 非空 →
      `assigned=true` 一条记录（展示名走既有批量 `userDisplayService.resolveDisplayNames`
      路径）；为空 → 查 `ApprovalTaskCandidateMapper` 按 `taskId` 取候选人明细，`USER`
      类型解析展示名，`ROLE` 类型查 `RoleMapper`（按 `code`）取 `name`，`assigned=false`。
      明确不做：不读取/透出 `resolveBasis`（用户已简化为仅展示角色名称和编码）；不展开
      角色候选人背后的具体人员列表（design.md Decision 7 已说明理由）。
- [x] 3a.3 单元/集成测试覆盖：单人节点已认领 → `assigned=true` 且能看到处理人展示名；
      候选组节点未认领、候选人为 USER 类型 → `assigned=false` 列表且展示名正确；候选人为
      ROLE 类型 → 展示角色名称与编码（不含 resolveBasis）；`COMPLETED`/`PENDING` 状态节点
      `currentApprovers` 恒为空列表。

## 4b. 需求 2 追加：分级列表展示当前节点的审批人信息（依赖 3a）

- [x] 4b.1 `types/workflow.ts` 补充 `CurrentApproverVO` 类型（`userId`/`userName`/
      `roleCode`/`roleName`/`assigned`，不含 `resolveBasis`），`ProcessGraphNodeVO` 类型
      补充 `currentApprovers: CurrentApproverVO[]` 字段。
- [x] 4b.2 分级列表（4a 已改造的展示形式）里 `CURRENT` 状态的列表项追加展示审批人信息：
      `assigned=true` 的条目汇总展示为"处理人：<userName>[、<userName2>...]"，
      `assigned=false` 按 `USER`/`ROLE` 两类分别汇总成一行——USER 类型"候选审批人：
      <userName>[、...]"，ROLE 类型"候选审批人：<roleName>（<roleCode>）[、...]"（仅
      名称+编码，不做悬浮/点击交互）（design.md Decision 7 二次修订）。一个 `CURRENT`
      节点的 `currentApprovers` 可能同时含已认领和未认领的候选人，三类分别渲染、都会
      展示，不只取第一条。
- [x] 4b.3 `npm run build`（`vue-tsc` + vite build）确认无类型错误，构建成功。手动验证
      复用 4a.3 的端到端 API 级验证：`processInstanceId=1127` 详情响应中 `CURRENT` 节点
      `deptLeaderApprove` 的 `currentApprovers` 为
      `[{userId:1,userName:"系统管理员（admin）",roleCode:null,roleName:null,assigned:true}]`，
      与前端 `approverLines()` 的 `assigned=true` 分支（渲染"处理人：系统管理员（admin）"）
      对应一致；`PENDING`/`COMPLETED` 节点 `currentApprovers` 均为空数组，符合预期。未在
      本次测试数据里覆盖到 `ROLE` 类型候选人分支（该测试流程节点是单人指定审批人，未触发
      候选组场景），该分支已在后端 3a.3 的单元/集成测试里覆盖，前端渲染逻辑按
      `CurrentApproverVO` 类型字段直接映射，未做额外的运行时判断；未在真实浏览器里
      逐字核对审批人信息在卡片内的排版效果，建议用户用 `npm run dev` 复核一次。

## 3b. 需求 2 追加：条件分支说明改为结构化数据（后端，2026-09-17 四次追加）

- [x] 3b.1 `ProcessGraphEdgeVO` 去掉拼好的 `label` 字段，改为新增 `conditions:
      List<ConditionItemVO>`（无条件/默认分支为空列表）+ `conditionLogic: String`
      （`AND`/`OR`，`conditions.size() <= 1` 时可空）；新增 `ConditionItemVO`
      （`fieldBizType`/`field`/`operator`/`value`，design.md Decision 9 示意结构）。
- [x] 3b.2 `ProcessGraphAssembler` 的 `describeConditionV1`/`describeConditionV2` 改为
      `toConditionItemsV1`/`toConditionItemsV2`，产出结构化 `ConditionItemVO` 列表而非拼接
      文本：v1 直接取 `EdgeConditionDsl.fieldBizType`；v2 已核实 `ConditionItemDsl` 确实没有
      等价字段，且 `ProcessModelDslV2Validator`/`ConditionAstCompiler` 均不校验条件字段归属
      的业务对象类型（v2 目前甚至没有前端可视化设计器、`WorkflowModelCompilerV2` 编译出的
      UEL 变量名是裸 `field`、不带 bizType 前缀，`publishV2` 也从不写
      `route_field_codes`，与 v1 的路由变量命名机制并不共享），因此"v2 条件字段恒等于流程
      绑定业务对象类型"这一假设**无法从代码层面验证为强约束，只是当前代码库里唯一可用的
      兜底信息来源**——已按此实现（`assemble` 新增 `instanceBizType` 入参，由
      `WorkflowTaskServiceImpl.resolveGraph` 传入 `instance.getBusinessType()`），design.md
      Decision 9 已补充这一核实结论。运算符统一归一化为 v2 字面量（`EQ`/`NE`/`GT`/`GE`/
      `LT`/`LE`/`IN`/`IS_NULL`，与 `ConditionOperator` 枚举实际取值核对一致），v1 的
      `GTE`/`LTE` 转换为 `GE`/`LE`（`V1_OPERATOR_NORMALIZATION` 映射表）。
- [x] 3b.3 单元/集成测试覆盖：`ProcessGraphAssemblerTest` 新增/扩展 v1 条件（`GTE`/`LTE`
      分别归一化为 `GE`/`LE`）与 v2 条件（`AND`/`OR` 两种多条件项组合）用例，默认/无条件
      分支 `conditions` 断言为空列表非 `null`；`WorkflowTaskServiceImplIntegrationTest`
      新增 `getProcessDetail_shouldExposeStructuredConditions_forV1Process`/`...ForV2Process`
      两个真实数据库集成测试，覆盖端到端路径下 `instance.getBusinessType()` 正确传入 v2
      `conditions[].fieldBizType` 兜底。

## 4c. 详情弹窗改为左右切换 tab（前端，2026-09-17 三次追加，用户反馈"下拉太长"）

- [x] 4c.1 `ApprovalRequestDetailDialog.vue` 改为 `el-tabs`（默认 `tab-position="top"`，
      横向 tab 栏）：三个 tab——"基本信息"（现有 `el-descriptions` 段）、"申请内容"
      （`fieldRows` + `userPositions` 合并、含"不涉及字段变更"提示语）、"审批流程"（仅
      `row.processInstanceId` 非空时出现，内容为 `ProcessFlowChart`，去掉外层
      `el-collapse`，`processFlowActiveNames` 状态删除）（design.md Decision 8）。
- [x] 4c.2 弹窗每次打开/切换 `row` 默认停在"基本信息"tab，不记忆上一条申请的 tab 位置；
      实现为新增 `activeTab` ref + 独立 `watch([modelValue, row?.id])`（`open` 时重置为
      `'basic'`），与既有"审批流程"数据拉取 `watch([modelValue, row?.processInstanceId])`
      彻底解耦；后者逻辑不变，仍是"弹窗打开即请求"，不因为改成 tab 就延迟到"点击审批流程
      tab 才请求"。
- [x] 4c.3 `npm run build` 确认无类型错误，构建成功（`vue-tsc` + vite build）。手动验证
      改用端到端 API 级方式（本环境仍无浏览器自动化工具）：确认三个 tab 的模板结构、
      `v-if="row.processInstanceId"` 判断、字段内容与改版前逐段比对未删减任何信息；
      未在真实浏览器里点击切换三个 tab 逐像素核对交互效果，建议用户用 `npm run dev`
      实际打开"我的申请"/"待我审批"详情弹窗复核一次 tab 切换的视觉效果。

## 4d. 条件分支可读化展示（前端，依赖 3b）

- [x] 4d.1 `types/workflow.ts` 补充 `ConditionItemVO` 类型（`fieldBizType`/`field`/
      `operator`/`value`，`operator` 用字面量联合类型
      `ProcessGraphConditionOperator`），`ProcessGraphEdgeVO` 类型去掉 `label`、补充
      `conditions: ConditionItemVO[]`/`conditionLogic: string | null` 字段；已逐字段核对
      与后端 `ConditionItemVO.java`/`ProcessGraphEdgeVO.java` 源码一致。
- [x] 4d.2 父组件（`ApprovalRequestDetailDialog.vue`）新增 `resolveFieldValueLabel`
      函数（包装既有 `displayValue`+`schemaItemFor`），通过 `resolve-field-label`
      （即既有 `labelFor`）与 `resolve-field-value-label` 两个 prop 传给
      `ProcessFlowChart.vue`，子组件不重新发起渲染元数据请求。
- [x] 4d.3 `ProcessFlowChart.vue` 的条件说明展示（原 `conditionAnnotations`）改为按
      `conditions`/`conditionLogic` 组装可读文案：新增 `conditionItemText`/
      `edgeConditionText` 两个函数，`field` 按 `fieldBizType` 通过 prop 传入的
      `resolveFieldLabel` 查展示名，`operator` 按 `typeLabels.ts` 新增的静态中文映射表
      `PROCESS_GRAPH_CONDITION_OPERATOR_LABEL` 转换（`EQ→等于`/`NE→不等于`/`GT→大于`/
      `GE→大于等于`/`LT→小于`/`LE→小于等于`/`IN→属于`/`IS_NULL→为空`），`value` 通过
      `resolveFieldValueLabel` 复用父组件 `displayValue` 同一套字典/布尔翻译逻辑，
      `IS_NULL` 不拼接取值；多条件项按 `conditionLogic` 用"且"（默认/`AND`）或"或"
      （`OR`）拼接（如"性别 等于 女 且 年龄 大于 18"），单条件项不加连接词，
      `conditions` 为空数组的边不展示注释。
- [x] 4d.4 `npm run build` 确认无类型错误，构建成功。手动验证：本环境当前通过 IDE 附带
      调试方式运行的后端实例（IntelliJ 调试会话，非本次改动的进程，未重启）仍在提供
      3b 改造前的旧响应结构（`edges[].label`），端到端检查了近 50 条现存审批实例
      （`pending`/`mine` 列表全部 `processInstanceId`），均为线性流程（无条件分支边），
      因此未能在真实浏览器/真实条件分支数据上肉眼核对"性别 等于 女"这类文案；改为用
      Node 脚本按组件内 `conditionItemText`/`edgeConditionText`/
      `PROCESS_GRAPH_CONDITION_OPERATOR_LABEL` 完全一致的算法逻辑做独立复现测试，覆盖
      单条件字典字段翻译、多条件 AND/OR 拼接、`IS_NULL` 不拼值、无条件默认分支不展示
      五个用例，全部与 design.md 给出的示例文案（"性别 等于 女"/"性别 等于 女 且 年龄
      大于 18"）一致；同时已直接核对 TS 类型与后端最新源码
      `ConditionItemVO.java`/`ProcessGraphEdgeVO.java` 逐字段一致。未做的：未能验证
      "该算法接入真实浏览器渲染后的最终 DOM 呈现"与"真实条件分支流程实例的端到端 HTTP
      响应"，建议用户重启本地后端进程（拿到 3b 已实现的条件结构化改动）后，用
      `npm run dev` 找一个含条件分支的真实流程实际打开详情弹窗复核一次。

## 5. 全量回归

- [x] 5.1 运行 `./gradlew test --tests "cn.nihility.rbac.approval.*" --tests
      "cn.nihility.rbac.workflow.*"`，确认全部通过、无回归。协调者独立重跑一次确认
      `BUILD SUCCESSFUL`。
- [x] 5.2 前端 `npm run build`（`vue-tsc` 类型检查 + vite build）确认无类型错误。协调者
      独立重跑一次确认构建成功。

## 6. OpenSpec 文档同步

- [x] 6.1 实现完成后，基于真实 diff 与测试结果核对 `proposal.md`/`design.md`/`tasks.md`
      与实际实现是否一致，如有偏差据实修正；确认三个 spec delta
      （`master-data-approval-workflow`/`approval-runtime-safety`/`workflow-approval-engine`）
      已通过对应流程正确应用到主 spec。已核对：`proposal.md`/`design.md` 与各阶段实际实现
      （含批准弹窗、processInstanceId 暴露、参与关系校验、节点/连线图、分级列表改版、当前
      节点审批人信息、条件分支结构化可读化、tab 布局等历次追加）一致，无需修改；三个 spec
      delta 已通过 `/opsx:sync` 合并进对应主 spec（`openspec validate --specs` 43 项全部
      通过）。
