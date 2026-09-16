## Why

"业务绑定"管理页面目前只有启用/停用两种状态操作，没有删除能力。停用只能让一个绑定维度停止生效，但这条记录、以及它占用的绑定维度（业务类型+操作类型+范围类型+范围 id）会一直留在表里——想要彻底清理一个不再需要的组织范围覆盖绑定、或者腾出这个维度以便重新配置，现在做不到。需要补上删除能力，让业务绑定管理具备和项目里其它模块（组织/用户/角色/管理员等）一致的启用/停用/删除三态生命周期。

## What Changes

- `tab_wf_process_binding` 表新增 `status` 字段（启用=2000/停用=3000/已删除=-1000，与项目里 `AdminStatus`/`RoleStatus` 等状态常量类同一套编码风格），替换现有的 `enabled` 布尔字段；新增 `BindingStatus` 常量类。
- 因为现有 `(biz_type, operation_type, scope_type, scope_id)` 数据库级 UNIQUE 索引会阻止"已删除的绑定"和"新建的同维度绑定"共存，本次把这个 UNIQUE 索引降级为普通索引，改由应用层查询时过滤 `status != DELETED` 来判断"该维度是否已存在有效绑定"——与本项目里角色/管理员等实体"编码唯一性只在未删除记录范围内保证、无法用数据库级 UNIQUE 索引表达"的既有惯例一致。
- 新增 `DELETE /api/workflow/process-bindings/{bindingId}` 接口（软删除，`status` 置为已删除），新增 `WorkflowDesign:binding:delete` 权限点（区别于现有的 `:edit`，与项目里其它模块删除操作独立成一个权限点的惯例一致）。
- 删除后：该绑定从列表查询中消失（不再展示，也不参与审批发起时的绑定解析），但保留数据库记录（`create_by`/`create_time` 等审计字段不丢失），且该绑定原来占用的维度可以重新新建绑定。
- 前端"业务绑定"管理页面新增"删除"按钮（二次确认弹窗），复用已有的启用/停用按钮布局位置。

## Capabilities

### Modified Capabilities
- `approval-design-release`："精确版本绑定及显式回滚"需求补充：业务绑定新增删除（软删除）能力，删除后维度可重新使用；同时把"绑定状态"的存储方式从单一启用布尔值改为启用/停用/已删除三态。

## Impact

- 数据库迁移：`backend/src/main/resources/db/migration/V4__add_process_binding_status.sql`（新增 `status` 字段、降级 UNIQUE 索引为普通索引、从 `enabled` 回填、删除 `enabled` 字段、补齐 `WorkflowDesign:binding:delete` 的 `tab_menu`/`tab_permission` 种子数据）。
- 改动文件：
  - `backend/src/main/java/cn/nihility/rbac/workflow/entity/ProcessBindingEntity.java`（`enabled` → `status`）
  - `backend/src/main/java/cn/nihility/rbac/workflow/constant/BindingStatus.java`（新增）
  - `backend/src/main/java/cn/nihility/rbac/workflow/dslv2/binding/WorkflowProcessBindingService.java`（新增 `deleteBinding`，既有方法按 status 改写）
  - `backend/src/main/java/cn/nihility/rbac/workflow/dslv2/binding/ProcessBindingResolutionService.java`（按 status 过滤已启用绑定）
  - `backend/src/main/java/cn/nihility/rbac/workflow/dslv2/binding/WorkflowProcessBindingController.java`（新增 `DELETE` 接口）
  - `backend/src/main/java/cn/nihility/rbac/auth/filter/IdentityAuthFilter.java`（新增 `DELETE /api/workflow/process-bindings/*` 的固定权限映射）
  - `frontend/src/api/processBinding.ts`、`frontend/src/views/workflow/binding/ProcessBindingView.vue`（新增删除按钮与调用）
- 文档：`权限资源.txt` 补充 `WorkflowDesign:binding:delete` 条目。
- 不涉及 `build.gradle` 依赖变更。
