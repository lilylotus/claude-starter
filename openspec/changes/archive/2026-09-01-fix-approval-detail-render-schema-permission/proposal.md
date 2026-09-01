## Why

排查"用户 test 打开审批相关页面报无权限"时，先确认了"审批管理"侧边分组常驻展示是
`add-master-data-approval-workflow` change 里刻意做的自助设计（"我的申请"
`selfService: true`，不受权限点约束，类比"修改密码"页面），这一点没有问题。

真正的缺陷在用户后续反馈中定位：打开"我的申请"（或"待我审批"）任意一条 `UPDATE`
类型申请的详情弹窗（`ApprovalRequestDetailDialog.vue`，两个页面共用同一个组件）时，
调用 `GET /api/form-fields/render-schema?bizType=...` 报无权限。完整链路：

1. 该弹窗为了展示"新旧字段对照"，挂载时无条件依次拉取 `ORG`/`USER`/`POSITION`/`APP`
   四个业务对象类型的渲染元数据（`useDynamicFormFields(bizType).fetchSchema()`），
   不管当前用户实际拿到的这条申请属于哪个 `bizType`。
2. 这四次请求都没有显式指定 `menu` 请求头，`frontend/src/api/request.ts` 的请求拦截器
   在这种情况下回退取"当前路由"的 `meta.permissionKey`：在"我的申请"页面是
   `ApprovalManagement:request:view`，在"待我审批"页面是
   `ApprovalManagement:request:approve`。
3. 后端 `IdentityAuthFilter` 用这个 `menu` 值调用
   `AuthorizationService.hasPermission(userId, menu)` 判断是否放行；`/api/form-fields/
   render-schema` 这个路径本身并不在 `IdentityAuthFilter.FIRST_LOGIN_WHITELIST`
   自助白名单里（该白名单目前只覆盖 `/api/approval-requests/mine` 与
   `/api/approval-requests/*/cancel` 这两个审批自助接口本身），所以照常走权限点判断。
4. `ApprovalManagement:request:view` 按 `权限资源.txt` 与代码注释里的既有设计
   "仅作为该页面请求头 menu 的编码占位"，从未真正通过任何角色授予给任何账号；
   `ApprovalManagement:request:approve` 只授予实际的审批人角色，而审批人也不一定
   同时拥有被审批对象（ORG/USER/POSITION/APP）的管理权限点。

结果是：**这不是 test 账号独有的问题**，而是审批详情弹窗自身的实现缺陷——几乎任何
账号（含拥有真实审批权限的审批人）打开一条 `UPDATE` 类型申请的详情，都会在这四次
`render-schema` 请求上被判定无权限，字段展示名/新旧对照渲染不出来（前端表现为该处
报错或字段名缺失，具体取决于 `useDynamicFormFields`/组件对失败请求的兜底处理）。

## What Changes

- `backend/src/main/java/cn/nihility/rbac/auth/filter/IdentityAuthFilter.java`：
  `FIRST_LOGIN_WHITELIST` 增加 `/api/form-fields/render-schema`，使其与
  `/api/approval-requests/mine` 等既有自助接口一样，豁免首登强制改密拦截与运行时
  权限点校验，但仍然要求携带有效 `identity-token`（未登录不能调用）。
- 不改变 `/api/form-fields` 其余接口（分页查询、详情、新增、编辑、启用、停用、删除）
  的权限校验：这些接口仍然只能由拥有 `FormFieldManagement` 相关权限点的用户调用，
  本次改动范围仅限 `render-schema` 这一个只读渲染元数据接口。
- 不改变组织/用户/任职/应用四个管理页面调用 `render-schema` 时的现有行为——它们本来
  就依赖"当前路由 `permissionKey` 恰好等于该页面自身管理权限点"这一巧合让请求通过，
  豁免运行时权限点校验后这几个页面的调用只会更宽松地放行，不会产生任何回归。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `password-login-auth`：新增一条 Requirement，明确 `/api/form-fields/render-schema`
  接口豁免"操作资源编码校验"（运行时权限点判断），仍需有效 `identity-token`。
- `master-data-approval-workflow`：在"管理页面的审批入口"相关描述中补充说明，审批
  详情弹窗对四个业务对象类型渲染元数据的读取不受调用者是否拥有对应业务管理权限点
  约束，确保"我的申请""待我审批"两个页面都能正常展示 `UPDATE` 类型申请的新旧字段
  对照，不因当前用户缺少 ORG/USER/POSITION/APP 某个具体管理权限点而报错。

## Impact

- **后端**：`IdentityAuthFilter` 一行常量列表改动，不涉及数据库迁移、不新增依赖。
- **安全影响**：`render-schema` 只返回字段展示名/控件类型/校验规则/字典选项等渲染
  元数据，不返回任何组织/用户/任职/应用的实际业务数据；豁免后任何已登录用户都能
  查询任意 `bizType` 的字段渲染元数据（此前需要"当前路由权限点恰好匹配"这个弱约束
  才能拿到，本来也不是有意设计的强隔离），可接受，与该白名单里其余跨模块自助接口
  （`/api/auth/permissions`、`/api/dashboard/stats`、`/api/dashboard/recent-operations`）
  性质一致。
- **前端**：无需改动——问题出在后端权限校验，不是前端请求头拼装或组件逻辑有误。
- **兼容性**：纯粹放宽一个只读接口的权限校验范围，不影响任何现有已授权调用方的行为。
