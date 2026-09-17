## Why

用户要求在"审批管理"菜单下新增一个"审批历史记录"菜单，支持查看当前登录用户自己已经审批
过（同意/拒绝/转办/委派/加签/退回）的历史记录。

已通过只读代码走查（不含代码修改）确认：

- 后端**已经有**这个能力的核心数据来源：`GET /api/v1/workflow/tasks/done`
  （`cn.nihility.rbac.workflow.controller.WorkflowTaskController#done` →
  `WorkflowTaskServiceImpl.findDoneTasks`）已经实现了"查询当前用户已处理完成的审批记录"
  （数据来自 `tab_wf_approval_task` 关联 `tab_wf_approval_record` 命中
  `operator_id=当前用户` 且动作属于 APPROVE/REJECT/RETURN/TRANSFER/DELEGATE/ADD_SIGN 的
  最新一条记录，SQL 已经用"自关联子查询 GROUP BY MAX(id)"这种 MySQL 5.7 兼容写法实现，
  `backend/src/main/resources/mybatis/mapper/ApprovalTaskMapper.xml` 里的
  `selectDonePage`）。这个接口从 workflow 引擎落地时就写好了，但**从未被前端调用过**
  （`add-approval-remark-and-process-flowchart` change 的调研已经确认过这一点）——这次
  新增菜单基本上是"把已经存在的后端能力接到前端页面上"，不是从零实现。
- 缺口集中在两处，都不大：
  1. `selectDonePage`/`findDoneTasks` 目前**没有总条数查询**，只支持 `LIMIT/OFFSET` 翻页，
     返回裸的 `List<ApprovalTaskVO>`（`WorkflowTaskController.done` 也是直接
     `Result<List<ApprovalTaskVO>>`），与本项目其余分页接口统一用 `PageResult<T>`（含
     `total`）的约定不一致，前端做不了"共 N 条"这种标准分页表格。
  2. `ApprovalTaskVO`（`cn.nihility.rbac.workflow.dto.ApprovalTaskVO`）没有携带"这条历史
     记录当时做了什么决定"（同意/拒绝/转办…）和"当时写的意见"——虽然 SQL 已经
     `INNER JOIN tab_wf_approval_record rec` 关联到了那条记录，但只 `SELECT t.*`（任务表
     字段），没有把 `rec.action`/`rec.remark` 一并带出来。
- 前端"审批管理"菜单目前是"我的申请"（`ApprovalManagement:request:view`，自助）、"待我
  审批"（`ApprovalManagement:request:approve`）、"审批设置"（`ApprovalManagement:switch:
  view`/`edit`）三项（`frontend/src/router/menu.ts`），新增"审批历史"是第四项，同一组
  内的既有菜单项/路由/权限点写法可以直接照抄结构。
- `frontend/src/types/workflow.ts` 已经有 `APPROVAL_RECORD_ACTION_LABEL`（动作编码 → 中文
  标签的映射，`add-approval-remark-and-process-flowchart` change 里给"审批流程"tab 的轨迹
  弹窗用的），新页面的"处理结果"列可以直接复用，不需要再造一份映射。

## What Changes

- **后端**：
  - `ApprovalTaskMapper` 新增一个与 `selectDonePage` 同结构（同样的 JOIN、同样的过滤条件）
    的总条数查询方法，供分页使用。
  - `selectDonePage` 查询补上 `rec.action`/`rec.remark` 两列（关联的那条审批轨迹的动作
    与意见），`ApprovalTaskVO` 新增 `action`/`remark` 两个字段承载。
  - `WorkflowTaskService.findDoneTasks`（及 `WorkflowTaskController.done` 接口）改为返回
    `PageResult<ApprovalTaskVO>`（含 `total`），与项目其余分页接口的既有约定一致——这是对
    现有接口返回类型的破坏性变更，但已确认目前没有任何调用方（前端此前从未接入），不影响
    现有功能。
- **前端**：
  - "审批管理"菜单下新增"审批历史"第四项菜单，新增页面
    `views/approval/history/ApprovalHistoryView.vue`，展示当前用户的已办分页列表：业务
    对象类型、节点名称、处理结果（复用既有 `APPROVAL_RECORD_ACTION_LABEL`）、处理意见、
    申请人、处理时间，支持按业务对象类型过滤、分页。
  - 新增/扩展 `api/workflow.ts` 对应的接口封装与类型。
- **权限**：新增一个权限点 `ApprovalManagement:record:view`（审批历史页面访问），同步登记
  到 `权限资源.txt`；不复用"待我审批"的 `ApprovalManagement:request:approve`，理由见
  design.md。新增一条 Flyway 迁移，在写入 `tab_menu`/`tab_permission` 的同一条脚本内把
  该权限点默认授予 `SUPER_ADMIN` 角色（用户明确要求"默认给管理员分配这个菜单资源"），
  `admin` 账号迁移后即可直接看到新菜单，不需要额外手动到角色管理页面勾选；其余自定义角色
  仍按现有约定手动分配。

## Capabilities

### Modified Capabilities

- `workflow-approval-engine`："待办与已办查询不依赖 Flowable 运行时表"需求补充"已办查询
  SHALL 支持分页总条数"与"已办查询结果 SHALL 携带处理动作与处理意见"两条约束。
- `master-data-approval-workflow`："管理页面的审批入口"需求补充"审批历史"第四个前端页面
  的访问权限门控说明。

## Impact

- **后端**：`cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper`（新增方法）、
  `backend/src/main/resources/mybatis/mapper/ApprovalTaskMapper.xml`（`selectDonePage`
  补列 + 新增总条数查询）、`cn.nihility.rbac.workflow.dto.ApprovalTaskVO`（新增字段）、
  `cn.nihility.rbac.workflow.service.WorkflowTaskService`/`WorkflowTaskServiceImpl`、
  `cn.nihility.rbac.workflow.controller.WorkflowTaskController`（`done` 接口返回类型变更）。
  不改动已有表结构；新增一条 Flyway 迁移写入新权限点的 `tab_menu`/`tab_permission`/
  `tab_role_permission`（授予 `SUPER_ADMIN`）种子数据。
- **前端**：`router/menu.ts`、`router/index.ts`（新增路由）、新增
  `views/approval/history/ApprovalHistoryView.vue`、`api/workflow.ts`/
  `types/workflow.ts` 补充。
- **权限**：新增 `ApprovalManagement:record:view` 权限点，需要在 `权限资源.txt` 登记，并
  确认现有角色的权限分配页面能正常勾选到这个新权限点（不需要额外改角色管理页面代码，
  权限点是数据驱动的）。
- **范围收敛（design.md 有详细取舍）**：本次只做"已办历史列表"，不做"点开某条历史记录看
  完整流程图/申请详情"这层深入查看——列表本身已经携带了处理动作、意见、申请人、时间这些
  用户最关心的信息，点进去看完整流程图属于锦上添花，且现有的申请详情弹窗
  （`ApprovalRequestDetailDialog.vue`）是围着 `ApprovalRequestVO`（审批申请）这个概念设计
  的，跟这里的 `ApprovalTaskVO`（工作流引擎任务记录）不是同一套数据形状，直接复用需要额外
  的桥接工作，不在本次范围内。
- 不涉及"待我审批"/"我的申请"/"审批设置"三个既有页面的行为，不改动它们的代码。
