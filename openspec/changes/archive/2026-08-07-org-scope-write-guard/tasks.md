## 1. 管辖组织范围校验断言能力

- [x] 1.1 `OrgScopeService` 接口新增 `boolean isOrgIdAllowed(Long userId, Long orgId)`：不受限（`resolveAllowedOrgIds` 返回空 `Optional`）恒返回 `true`；受限时返回 `orgId` 是否在允许集合内。
- [x] 1.2 `OrgScopeServiceImpl` 实现该方法，直接基于已有的 `resolveAllowedOrgIds` 结果判断，不引入新查询。

## 2. 组织管理（`OrgServiceImpl`）写操作校验

- [x] 2.1 `create`：在 `checkCodeUnique`/`validateDynamicFields` 之前或之后（写库之前）校验 `request.getParentId()` 是否被 `isOrgIdAllowed` 允许，不允许时抛"无权限"类 `BusinessException`。
- [x] 2.2 `update`：`getExistingEntity(id)` 之后立即校验 `id` 是否被 `isOrgIdAllowed` 允许，不允许时抛与"组织不存在"相同文案的 `BusinessException`；随后校验 `request.getParentId()`（新上级组织）是否被允许，不允许时抛"无权限"类 `BusinessException`。
- [x] 2.3 `changeStatus`（`enable`/`disable` 共用）：`getExistingEntity(id)` 之后校验 `id` 是否被允许，不允许时抛与"组织不存在"相同文案的 `BusinessException`。
- [x] 2.4 `delete`：`getExistingEntity(id)` 之后、子组织数量校验之前或之后均可，校验 `id` 是否被允许，不允许时抛与"组织不存在"相同文案的 `BusinessException`。

## 3. 任职管理（`PositionServiceImpl`）写操作校验

- [x] 3.1 `create`：写库之前校验 `request.getOrgId()` 是否被 `isOrgIdAllowed` 允许，不允许时抛"无权限"类 `BusinessException`。
- [x] 3.2 `update`：`getExistingEntity(id)` 之后校验实体当前 `orgId` 是否被允许，不允许时抛与"任职记录不存在"相同文案的 `BusinessException`；随后校验 `request.getOrgId()`（新所属组织）是否被允许，不允许时抛"无权限"类 `BusinessException`。
- [x] 3.3 `changeStatus`（`enable`/`disable` 共用）：`getExistingEntity(id)` 之后校验实体当前 `orgId` 是否被允许，不允许时抛与"任职记录不存在"相同文案的 `BusinessException`。
- [x] 3.4 `delete`：`getExistingEntity(id)` 之后校验实体当前 `orgId` 是否被允许，不允许时抛与"任职记录不存在"相同文案的 `BusinessException`。

## 4. 应用管理（`AppServiceImpl`）写操作校验

- [x] 4.1 `create`：写库之前校验 `request.getOrgId()` 是否被 `isOrgIdAllowed` 允许，不允许时抛"无权限"类 `BusinessException`。
- [x] 4.2 `update`：`getExistingEntity(id)` 之后校验实体当前 `orgId` 是否被允许，不允许时抛与"应用不存在"相同文案的 `BusinessException`；随后校验 `request.getOrgId()`（新所属组织）是否被允许，不允许时抛"无权限"类 `BusinessException`。
- [x] 4.3 `changeStatus`（`enable`/`disable` 共用）：`getExistingEntity(id)` 之后校验实体当前 `orgId` 是否被允许，不允许时抛与"应用不存在"相同文案的 `BusinessException`。
- [x] 4.4 `delete`：`getExistingEntity(id)` 之后校验实体当前 `orgId` 是否被允许，不允许时抛与"应用不存在"相同文案的 `BusinessException`。

## 5. 组织更新时上级组织范围校验精确化（后端校验上线后补充）

- [x] 5.1 `OrgServiceImpl.update`：读到 `entity` 并通过自身 id 的管辖范围校验后，记录其更新前的 `parentId`；仅当请求的 `parentId` 与该值不同时才调用 `assertParentOrgInScope`，相同则跳过。
- [x] 5.2 补充单元测试：受限管理员编辑一个自身在管辖范围内、但真实 `parentId` 不在管辖范围内的组织（虚拟根节点），请求携带的 `parentId` 与当前值相同时应更新成功。

## 6. 暴露 `orgScopeRestricted` 供前端收紧选择器（后端校验上线后补充）

- [x] 6.1 `PermissionCodesVO` 新增 `orgScopeRestricted` 布尔字段。
- [x] 6.2 `AuthController.myPermissions`：注入 `OrgScopeService`，按 `orgScopeService.resolveAllowedOrgIds(userId).isPresent()` 填充该字段。
- [x] 6.3 未新增 `AuthControllerTest`：`myPermissions()` 是薄封装（组合两个已各自被单元测试覆盖的服务方法），与仓库里其余 controller 一致不做单独单元测试；`orgScopeService.resolveAllowedOrgIds(...).isPresent()` 的受限/不受限两种取值已由 `OrgScopeServiceImplTest` 覆盖。
- [x] 6.4 前端 `src/types/auth.ts`：`PermissionCodesResult` 新增 `orgScopeRestricted: boolean`。
- [x] 6.5 前端 `src/stores/currentUserPermission.ts`：新增 `orgScopeRestricted` 状态（默认 `true`，保守当作受限，避免加载完成前的一瞬间多展示选项），随 `loadCodes` 一并写入，`reset` 时一并清空。
- [x] 6.6 前端 `OrgManagementView.vue`：`treeSelectData` 按 `orgScopeRestricted` 决定是否拼接虚拟"顶级组织"根节点；`treeSelectExpandedKeys` 的兜底默认值（`fallbackExpandedKeys`）同步调整，受限时兜底展开空数组而不是不存在的 `0` 节点。

## 7. 验证

- [x] 7.1 `cd backend && ./gradlew test` 全量跑通（316 个既有用例 + 本次新增 23 个用例，全部通过）。
- [x] 7.2 单元测试层面覆盖核心场景（`OrgScopeServiceImplTest`/`OrgServiceImplTest`/`PositionServiceImplTest`/`AppServiceImplTest` 新增用例）：受限管理员创建 `parentId=0` 的顶级组织被拒绝；受限管理员对管辖范围外已有组织/任职记录/应用调用编辑、启用、停用、删除均被拒绝且报错文案与"不存在"一致；更新时新上级组织/所属组织超出范围同样被拒绝；编辑虚拟根节点但不改变上级组织时更新成功；不受限管理员（既有用例默认桩 `Optional.empty()`）的全部写操作行为不变。未额外做真实数据库 + HTTP 层面的手工验证。
- [x] 7.3 前端 `cd frontend && npm run build`（`vue-tsc` 类型检查 + `vite build`）通过，无类型错误。未启动开发服务器做浏览器手工验证（本地无可用数据库/后端运行环境）。
- [x] 7.4 本文件已按实际实现更新勾选状态；`proposal.md`/`design.md` 记录的方案与实际实现一致，无需改动。
