## 1. 后端：已办查询补总条数与处理动作/意见

- [x] 1.1 `ApprovalTaskMapper.xml` 的 `selectDonePage` 补 `rec.action`/`rec.remark` 两列
      （design.md Decision 2）；新增 `selectDonePageCount(operatorId, actions,
      businessType): Long`，与 `selectDonePage` 保持完全一致的 JOIN/WHERE 结构。
- [x] 1.2 承接 `action`/`remark`：`selectDonePage` 的 `resultType` 直接改为
      `ApprovalTaskVO`（design.md Decision 2 备选方案之一），不再新增专用查询结果类；
      `buildTaskVOList`/`toVO` 重构为直接在 `ApprovalTaskVO` 上原地补填展示字段（新增
      `fillVO`），`todo` 路径改为先经 `WorkflowConvert.toTaskVOList` 转换出基础 VO 再复用
      同一个 `buildTaskVOList`。`ApprovalTaskVO` 新增 `action`/`remark` 字段，并补充
      `@NoArgsConstructor`/`@AllArgsConstructor`（MyBatis 直接以该类作为 resultType 时
      需要公开无参构造器，否则会退化为构造器自动映射导致列错位类型转换异常，实测踩坑后
      修复）。
- [x] 1.3 `WorkflowTaskService.findDoneTasks`/`WorkflowTaskServiceImpl`/
      `WorkflowTaskController.done` 改为返回 `PageResult<ApprovalTaskVO>`（design.md
      Decision 1，不保留旧的裸列表返回方式）；同步调整了 `WorkflowService`（engine 抽象
      接口）/`FlowableWorkflowService` 的签名（`findDoneTasks` 委托链路的一部分，
      proposal.md Impact 未单独列出但同属该接口变更范围）。
- [x] 1.4 单元/集成测试覆盖（真实数据库，`WorkflowTaskServiceImplIntegrationTest`）：
      分页总条数正确（多页数据验证 `total`，`findDoneTasks_shouldSortByLatestRecordTime_
      andPaginateFromDatabase`）；`action`/`remark` 正确对应到触发该条历史记录的那次审批
      操作（`findDoneTasks_shouldKeepOnlyLatestRecordPerTask`）；按业务对象类型过滤正确
      （新增 `findDoneTasks_shouldFilterByBusinessType_inDatabase`）；当前用户无任何已办
      记录时返回 `records` 为空数组的整页对象（新增
      `findDoneTasks_shouldReturnEmptyPage_whenNoRecords`）；`todo` 接口行为未受影响
      （既有 `findTodoTasks_*` 两个测试保持通过）。`./gradlew test --tests
      "cn.nihility.rbac.workflow.*"` 全部通过（21/21，无回归），`./gradlew build` 通过。

## 2. 权限登记

- [x] 2.1 `权限资源.txt`"审批管理"小节新增 `ApprovalManagement:record:view` 条目（三段式
      编码 + 中文用途说明，参照既有四条的格式，design.md Decision 3）。
- [x] 2.2 新增 Flyway 迁移脚本 `V6__add_approval_record_view_permission.sql`（确认当前
      仓库实际最新迁移版本号为 `V5`，未凭假设编号），在同一条脚本内完成三件事（design.md
      Decision 3，参照 `V5__grant_binding_delete_permission.sql` 的幂等写法）：1)
      `tab_menu` 新增"审批历史"菜单行（挂在 `approval` 分组下，`code` 为
      `ApprovalManagement:record:view`，`show_order=5`，小于既有最小值"审批设置"的 10，
      排在其后）；2) `tab_permission` 新增对应权限点行；3) `tab_role_permission` 补一条
      把该权限点授予 `SUPER_ADMIN` 角色（`NOT EXISTS` 防重复插入）。
- [x] 2.3 `./gradlew test`（会对 `127.0.0.1:3306/rbac` 触发 Flyway 执行全部迁移，含
      `V6`）后直接用 SQL 核对：`tab_menu` 已有 `code='ApprovalManagement:record:view'`
      的行（`parent_id` 正确指向 `approval` 分组）；`tab_permission` 已有对应权限点行；
      `tab_role_permission` 已有一条 `SUPER_ADMIN` 关联该权限点的记录。`admin` 账号已
      关联 `SUPER_ADMIN` 角色，故可直接看到新菜单；前端页面层面的端到端验证留给负责前端
      部分的实现一并完成。

## 3. 前端：新增菜单、路由与页面

- [x] 3.1 `frontend/src/router/menu.ts`"审批管理"分组新增"审批历史"菜单项
      （`permissionKey: 'ApprovalManagement:record:view'`，排在"审批设置"之后，与后端
      `V6__add_approval_record_view_permission.sql` 里 `show_order=5`——小于既有最小值
      "审批设置"的 10、按分组降序展示排在其后——保持一致），`frontend/src/router/
      index.ts` 补充 `/approval/history` → 新页面组件的动态 import 映射。
- [x] 3.2 新增 `frontend/src/views/approval/history/ApprovalHistoryView.vue`（design.md
      Decision 4）：业务对象类型筛选 + 标准分页表格，列为业务对象类型/节点名称/处理结果
      （复用 `APPROVAL_RECORD_ACTION_LABEL`）/处理意见（空值展示"-"）/申请人（为空兜底
      展示 applicantId 或"-"）/处理时间，不提供"查看详情"操作列（Non-Goals）；页面访问
      权限依赖路由守卫层的 `permissionKey` 门控，与其余三个既有列表页一致，页面内不重复
      权限判断（无操作按钮）。
- [x] 3.3 `frontend/src/api/workflow.ts` 新增 `getDoneApprovalTasks` 对接
      `GET /api/v1/workflow/tasks/done` 的分页查询函数（返回类型复用
      `types/approval.ts` 已有的通用 `PageResult<T>`，未重复定义）；
      `frontend/src/types/workflow.ts` 新增 `DoneApprovalTaskQuery`/`ApprovalTaskVO`
      两个类型（`ApprovalTaskVO` 含 `action`/`remark` 字段，此前该文件未定义过这个类型，
      是新增而非补充字段）。
- [x] 3.4 `npm run build`（`vue-tsc -b && vite build`）通过，无类型错误。手动验证：无
      浏览器自动化工具可用，改用 Node 脚本模拟浏览器登录流程（复用与
      `src/utils/rsa.ts` 相同的 RSA-OAEP/SHA-256 算法加密账号密码）对
      `admin`/`admin123` 完成登录后直接调用 `GET /api/v1/workflow/tasks/done` 验证：
      不带 `businessType` 时返回 `{records:[...1条...], total:1, page:1, pageSize:10}`，
      字段（`businessType`/`nodeName`/`action`/`remark`/`applicantName`/`finishedTime`
      等）与前端 `ApprovalTaskVO` 类型定义逐一对应；带 `businessType=ORG` 过滤时正确返回
      空分页（当前数据库该用户名下无 ORG 类型的已办记录）；`GET /api/auth/permissions`
      确认 `admin` 权限编码集合包含 `ApprovalManagement:record:view`。**这是 API 级别的
      端到端验证，未在浏览器里肉眼确认页面渲染、分页控件交互、菜单可见性/隐藏效果**，
      如需进一步确认建议用 `npm run dev` 实际打开页面看一眼。

## 4. 全量回归

- [x] 4.1 运行 `./gradlew test --tests "cn.nihility.rbac.workflow.*"`，确认全部通过、
      无回归。协调者独立重跑一次确认 `BUILD SUCCESSFUL`。
- [x] 4.2 前端 `npm run build` 确认无类型错误。协调者独立重跑一次确认构建成功。

## 5. OpenSpec 文档同步

- [x] 5.1 实现完成后，基于真实 diff 与测试结果核对 `proposal.md`/`design.md`/`tasks.md`
      与实际实现是否一致，如有偏差据实修正；确认两个 spec delta
      （`workflow-approval-engine`/`master-data-approval-workflow`）已通过对应流程正确
      应用到主 spec。已核对：`proposal.md`/`design.md` 与实际实现（含 `ApprovalTaskVO`
      直接作为 `selectDonePage` 的 resultType、`V6` 迁移脚本、前端页面结构）一致，无需
      修改；两个 spec delta 已通过 `/opsx:sync` 合并进对应主 spec（`openspec validate
      --specs` 43 项全部通过）。
