## Context

已通过只读代码走查（不含代码修改）确认以下事实：

- `WorkflowTaskController.done`（`backend/.../workflow/controller/WorkflowTaskController.java:81-89`）
  → `WorkflowTaskServiceImpl.findDoneTasks(userId, query)`
  （`backend/.../workflow/service/impl/WorkflowTaskServiceImpl.java:136-143`）→
  `approvalTaskMapper.selectDonePage(operatorId, DONE_ACTIONS, businessType, offset, limit)`
  （SQL 在 `backend/src/main/resources/mybatis/mapper/ApprovalTaskMapper.xml`）——这条链路
  已经完整实现，`DONE_ACTIONS`（`WorkflowTaskServiceImpl.java:57-59`）取值为
  `APPROVE`/`REJECT`/`RETURN`/`TRANSFER`/`DELEGATE`/`ADD_SIGN`（即"计入已办"的动作类型），
  SQL 用"自关联子查询 `GROUP BY MAX(id)`"取每个 `taskId` 命中当前操作人、动作属于上述集合
  的最新一条 `tab_wf_approval_record`，符合仓库 MySQL 5.7 兼容约定（不使用窗口函数）。
- `TaskQuery`（`cn.nihility.rbac.workflow.dto.TaskQuery`）已经有 `businessType`/`page`/
  `pageSize` 三个参数，`GET /api/v1/workflow/tasks/done` 接口签名也已经接受这三个查询参数，
  不需要新增查询条件字段——缺的只是"总条数"和"返回结构包成 `PageResult`"。
- `selectDonePage` 的 SQL 已经 `INNER JOIN tab_wf_approval_record rec ON rec.id =
  latest.max_id`，但 `SELECT t.*` 只选了 `tab_wf_approval_task` 表的列，`rec.action`/
  `rec.remark` 虽然已经在查询范围内、却没有被选出来传给上层。`ApprovalTaskVO`
  （`cn.nihility.rbac.workflow.dto.ApprovalTaskVO`）目前没有对应字段承接。
- `WorkflowTaskController.todo`/`done` 目前都是 `Result<List<ApprovalTaskVO>>`（裸列表，无
  总条数），与本项目"我的申请"/"待我审批"等其余分页接口统一用 `PageResult<T>`（含
  `total`/`page`/`pageSize`）的既有约定不一致——`PageResult`
  （`cn.nihility.rbac.common.result.PageResult`）是项目通用分页响应类，`todo`/`done` 是
  少数几个没有用上它的分页接口。
- `frontend/src/router/menu.ts`（约55-76行）里"审批管理"分组现有三个菜单项（我的申请/
  待我审批/审批设置），各自的 `permissionKey` 对应 `frontend/src/router/index.ts`（约
  43-45行）里 `path → 动态 import 组件` 的映射表；新增菜单照抄这两处的既有写法即可。
- `frontend/src/types/workflow.ts`（约247行）已有 `APPROVAL_RECORD_ACTION_LABEL`
  （动作编码→中文标签，`add-approval-remark-and-process-flowchart` change 为"审批流程"
  tab 的审批轨迹弹窗新增），新页面"处理结果"列直接复用这份映射。
- `权限资源.txt`"审批管理"小节已登记 `ApprovalManagement:request:view`/
  `ApprovalManagement:request:approve`/`ApprovalManagement:switch:view`/
  `ApprovalManagement:switch:edit` 四个权限点，新增页面需要按同样的三段式格式登记一条新
  记录，并在中文说明里注明用途（参照既有四条的写法）。

## Goals / Non-Goals

**Goals:**

1. "审批管理"菜单下新增"审批历史"页面，展示当前登录用户自己已经处理过（同意/拒绝/转办/
   委派/加签/退回）的历史记录，支持标准分页（含总条数）与按业务对象类型过滤。
2. 每条历史记录展示：业务对象类型、节点名称、处理结果（同意/拒绝/…）、处理意见、申请人、
   处理时间——用户点开这个页面最想知道的信息，不需要再跳转别的页面就能看到。
3. `GET /api/v1/workflow/tasks/done` 接口改造为标准分页响应（`PageResult`），顺带也让
   `todo`（待办）接口在语义上有了一致的改造空间，但本次只改 `done`（`todo` 不在本次范围，
   见 Non-Goals）。

**Non-Goals:**

- 不做"点开某条历史记录，弹出完整申请详情（新旧字段对照）/完整流程图"这层深入查看——
  proposal.md Impact 已说明理由（`ApprovalTaskVO` 与 `ApprovalRequestVO` 不是同一套数据
  形状，桥接是独立工作量）。历史列表本身携带的字段已经覆盖了"我当时同意/拒绝了什么、写了
  什么意见"这个核心诉求。如果用户后续需要"从历史记录跳转到完整详情"，作为独立后续 change
  处理。
- 不改动 `GET /api/v1/workflow/tasks/todo`（待办）接口的返回类型——虽然它和 `done` 有相同
  的"裸列表无总条数"问题，但"待我审批"页面目前走的是 `/api/approval-requests/pending`
  （`ApprovalRequestService.pagePending`，已经是标准 `PageResult`），不依赖 `todo`
  这个 workflow 引擎原生接口，改不改 `todo` 与本次需求无关，不顺带处理，避免扩大改动面。
- 不改动"我的申请"/"待我审批"/"审批设置"三个既有页面的任何代码。
- 不新增"审批历史"的导出/打印等衍生能力。

## Decisions

### 1. `done` 接口改为标准 `PageResult`，不新增一个并行的分页接口

直接修改 `WorkflowTaskService.findDoneTasks` 的返回类型为 `PageResult<ApprovalTaskVO>`（相应
调整 `WorkflowTaskServiceImpl`/`WorkflowTaskController.done`），而不是保留原方法、另外新增
一个 `pageDoneTasks` 方法。

**理由**：已确认这个接口目前没有任何调用方（前端从未接入），修改返回类型不存在破坏现有
功能的风险；项目 CLAUDE.md 明确要求"不使用 feature flags 或 backwards-compatibility shims"，
在没有真实兼容需求的情况下保留一个很快就没用的旧方法签名只是增加维护负担。

### 2. `selectDonePage` 补 `action`/`remark`，新增同结构的总条数查询

`ApprovalTaskMapper.xml` 的 `selectDonePage` 在 `SELECT t.*` 后面加两列
`, rec.action AS action, rec.remark AS remark`；`resultType` 改为一个新的结果类型（不再是
`ApprovalTaskEntity`——那是 `tab_wf_approval_task` 表的持久化实体，混入两个来自别的表的
只读列会污染实体语义，也不符合 MyBatis-Plus 实体与数据库列一一对应的既有约定）。建议新增
一个专用的查询结果类（如 `cn.nihility.rbac.workflow.dto.DoneTaskRow`，与 `ApprovalTaskEntity`
字段一致再加 `action`/`remark` 两列，或者直接让这条 SQL 的 `resultType` 就是
`ApprovalTaskVO` 本身——需要在 `buildTaskVOList`（批量补齐 `businessType`/`title`/
展示名等信息的既有方法）能否直接在这个新结果类型上复用之间做取舍，实现时根据
`buildTaskVOList` 的实际输入类型确定，不强行规定，保持"最小改动能让 action/remark 传递
到 `ApprovalTaskVO`"这个目标就行。

新增 `selectDonePageCount(operatorId, actions, businessType): Long`，与 `selectDonePage`
用完全相同的 JOIN 结构与 `WHERE` 条件，只是 `SELECT COUNT(*)` 且不带 `LIMIT/OFFSET`——
两条 SQL 保持结构一致，避免"分页查询和总数查询过滤条件不小心写岔了导致 total 和实际返回
条数对不上"这类常见 bug。

### 3. 新增独立权限点 `ApprovalManagement:record:view`，不复用 `request:approve`

**备选方案（未采用）**：复用"待我审批"的 `ApprovalManagement:request:approve` 权限点门控
"审批历史"页面的访问（理由：能审批的人才可能有历史记录）。

未采用原因：项目既有约定是"一个前端页面对应一个独立登记的 view 权限点"（我的申请/待我
审批/审批设置三个页面各自独立一条），"审批历史"是第四个独立页面，理应遵循同样的约定，
而不是复用另一个页面的权限点——复用会导致"关掉某个用户的待我审批权限"这个操作意外地也
关掉了他查看自己历史记录的入口，两个语义不同的操作（"还能不能接新的审批任务"vs"能不能看
自己以前处理过什么"）被耦合在同一个权限点上，不是好的权限设计。新权限点命名
`ApprovalManagement:record:view`，与既有的 `request:view`（我的申请）/`request:approve`
（待我审批）/`switch:view`/`switch:edit` 放在同一模块前缀下，符合"模块:资源:操作"三段式
约定。

**新增权限点必须在同一条迁移里补授 `SUPER_ADMIN`（用户明确要求"默认给管理员分配这个菜单
资源"）**：已核实 `V1__init_schema.sql` 里 `SUPER_ADMIN` 角色关联全部权限点是用一条
`INSERT INTO tab_role_permission SELECT @super_admin_role_id, id FROM tab_permission`
一次性覆盖当时已插入的全部 `tab_permission` 行（该语句位于 V1 脚本末尾、所有权限点
INSERT 之后）；**之后新增的独立迁移脚本不会被这条一次性语句覆盖**，必须自己显式补一条
`INSERT INTO tab_role_permission`。仓库里已经有一个真实先例踩过这个坑：
`V4__add_process_binding_status.sql` 只登记了 `WorkflowDesign:binding:delete` 的
`tab_menu`/`tab_permission`，漏了 `tab_role_permission` 补授，导致该权限点在数据库里
存在但包括 `SUPER_ADMIN` 在内的任何角色都未被授予、前端按钮对所有人不可见，后来用
`V5__grant_binding_delete_permission.sql` 单独补了一条幂等的授权 INSERT（`NOT EXISTS`
防重复插入）修复。本次新增 `ApprovalManagement:record:view` 时必须在**同一条迁移脚本内**
把 `tab_menu` + `tab_permission` + 授予 `SUPER_ADMIN` 的 `tab_role_permission` 三部分
一次性写完，不要重复 V4 那个"漏了最后一步"的错误，参照 V5 的幂等写法（`WHERE ... IS NOT
NULL AND NOT EXISTS (...)`，防止迁移脚本被重复执行时产生唯一约束冲突或重复行）。

其它非 `SUPER_ADMIN` 角色（如自定义的部门管理员角色）不在本次自动授权范围内——这些角色
本来就是按需手动勾选权限点的，`SUPER_ADMIN` 之外的角色默认没有这个新权限点是正常、预期
的行为，不是缺陷，只有"默认管理员"这一条用户明确提出的诉求需要迁移脚本兜底。

### 4. 前端页面结构：复用既有列表页模式，不做详情弹窗

参照 `MyApprovalRequestView.vue`/`PendingApprovalRequestView.vue` 的既有结构（`el-table` +
分页组件 + 顶部筛选栏），新增 `views/approval/history/ApprovalHistoryView.vue`：

- 筛选栏：业务对象类型下拉（复用既有 `APPROVAL_BIZ_TYPE_OPTIONS`），无需按操作类型/状态
  过滤（历史记录本身没有"状态"概念，都是已经处理完的）。
- 列表列：业务对象类型（`businessType`，复用既有 bizType 标签映射）、节点名称
  （`nodeName`）、处理结果（`action`，复用 `APPROVAL_RECORD_ACTION_LABEL`）、处理意见
  （`remark`，为空展示"-"）、申请人（`applicantName`）、处理时间（`finishedTime`）。
- 标准分页（`page`/`pageSize`/`total`，与既有列表页一致的分页组件用法）。
- 不提供"查看详情"操作列（见 Non-Goals）。

## Risks / Trade-offs

- [`done` 接口返回类型变更是破坏性变更] → proposal.md Impact 已确认目前无调用方，属于
  安全的接口整形，不是功能回归。
- [新增 `action`/`remark` 两列绕开了 `ApprovalTaskEntity` 这个持久化实体、需要一个新的
  查询结果类型] → 比"污染持久化实体加两个不属于该表的字段"更清晰，多一个类的维护成本
  可以接受。
- [新权限点默认只有 `SUPER_ADMIN` 拥有，其它角色（如自定义的部门管理员角色）下的用户看
  不到新菜单，需要人工在角色管理页面勾选] → 这是权限驱动菜单可见性的既定设计
  （`permission-driven-visibility` 能力），`SUPER_ADMIN` 已通过迁移脚本默认拿到，其余
  角色按需手动勾选是预期行为，不是缺陷。

## Migration Plan

1. 后端：`ApprovalTaskMapper.xml` 补 `selectDonePage` 的 `action`/`remark` 列 + 新增
   `selectDonePageCount`；`ApprovalTaskVO` 新增字段；`WorkflowTaskService.findDoneTasks`/
   `WorkflowTaskServiceImpl`/`WorkflowTaskController.done` 改为返回 `PageResult`。
2. 后端测试：真实数据库集成测试覆盖分页总条数正确、`action`/`remark` 正确关联到对应任务、
   按业务对象类型过滤正确、无历史记录时返回空分页对象（不报错）。
3. 权限：`权限资源.txt` 新增 `ApprovalManagement:record:view` 条目；新增一条 Flyway 迁移
   脚本（`tab_menu` + `tab_permission` + 授予 `SUPER_ADMIN` 的 `tab_role_permission`
   三部分在同一条脚本内一次写完，参照 V5 的幂等写法，见 Decision 3）；`admin` 账号关联
   `SUPER_ADMIN` 角色，迁移执行后即可直接验证，不需要额外手动勾选。
4. 前端：新增菜单项、路由、页面、API 封装、类型；本地启动验证列表能正确展示、分页正常、
   按业务对象类型过滤正常、无权限用户看不到菜单入口。
5. 运行现有回归测试（`workflow.*`），确认无回归。
6. 实施结束后依真实 diff/测试结果同步本 change 的 `proposal.md`/`design.md`/`tasks.md`。
