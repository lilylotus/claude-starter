## Why

组织(ORG)/用户(USER)/任职(POSITION)/应用(APP)四类业务对象的新增/更新/启用/停用/删除，通过"业务绑定"（`tab_wf_process_binding`，维度 `bizType × operationType × scopeType × scopeId`）决定由哪个已发布的流程模型版本承接审批。这套绑定机制（含精确组织绑定优先于全局绑定的解析顺序、版本切换、启停）在后端已经完整实现（`WorkflowProcessBindingController`/`ProcessBindingResolutionService`），并且已经通过数据库种子数据为全部 20 种业务类型×操作类型组合配置了指向内置流程 `MASTER_DATA_APPROVAL` 的全局兜底绑定。但目前**没有任何前端页面**能查看或修改这些绑定——管理员如果想让"组织删除"走一个不同于默认的审批流程，或者想给某个子公司组织单独配置一套流程，只能直接调用 REST 接口，普通管理员无法自助完成。本 change 补齐这个前端管理页面。

## What Changes

- 新增"业务绑定"前端管理页面：按 `bizType`（组织/用户/任职/应用）分组展示 `CREATE`/`UPDATE`/`ENABLE`/`DISABLE`/`DELETE` 五种操作类型当前生效的绑定情况（指向的流程模型名称+版本、执行模式、启用状态）。
- 支持新建绑定：选择 `bizType`+`operationType`，选择 `scopeType`（全局或指定组织，组织通过组织选择器选取），从该 `bizType` 下已发布的流程模型版本列表中选择一个 `definitionId`。
- 支持切换已有绑定指向的 `definitionId`（对应后端"显式回滚/切换版本"能力），支持启用/禁用某条绑定。
- 绑定列表按"全局 / 该 bizType+operationType 下的组织范围覆盖"两个层级展示，让管理员能直观看到解析优先级（精确组织 > 祖先组织 > 全局），而不是一个打平的表格。
- 不支持在本页面内创建/发布新的流程模型——流程模型的设计与发布仍通过已有的"流程模型"/设计器页面完成，本页面只负责选择已发布版本并配置绑定关系。
- 不支持配置 `executionMode=RELIABLE_ASYNC`——该模式后端 `resolveForStart` 目前仍会拒绝，页面只暴露 `LEGACY_SYNC`，避免用户配置出一个实际不可用的绑定。

## Capabilities

### Modified Capabilities
- `approval-design-release`: "精确版本绑定及显式回滚"需求补充前端管理页面，覆盖全局绑定配置、组织范围覆盖配置、版本切换与启停的可视化操作。

## Impact

- 前端：新增 `frontend/src/api/processBinding.ts`（绑定 CRUD/启停接口封装）、新增视图（暂定 `frontend/src/views/workflow/binding/ProcessBindingView.vue`，具体路径见 design.md）、路由与菜单项（复用已有 `WorkflowDesign:binding:view`/`WorkflowDesign:binding:edit` 权限点，无需新增权限点）、`权限资源.txt` 若菜单项名称变化需同步核对（权限点本身已存在，预期不需要新增/修改编码，只需确认对应菜单条目文案）。
- 后端：不涉及后端接口改动，`WorkflowProcessBindingController` 已提供全部所需接口。
