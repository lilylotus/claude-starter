## Why

组织、用户、任职、应用四类主数据的新增/编辑/启用/停用/删除目前调用对应接口即立即生效，没有任何审核环节。随着这四类数据成为权限体系的基础（组织范围、任职关系直接影响谁能看到什么、谁能审批什么），需要为这五类高影响操作引入审批流程：操作先提交为一条待审批的变更申请，只有获得审批权限的用户批准后才真正落库生效，拒绝则不产生任何数据变化。项目已经引入 Flowable（`backend/build.gradle` 已声明 `flowable-spring-boot-starter` 系列依赖，版本 7.2.0），但目前没有任何集成代码，本次借助 Flowable 的流程引擎能力实现这套审批状态机，为后续从单级审批演进到多级审批预留空间。

## What Changes

- **BREAKING（受审批开关状态影响，见下）**：组织、用户、任职、应用四个模块现有的新增（`POST`）、更新（`PUT .../{id}`）、启用（`PUT .../{id}/enable`）、停用（`PUT .../{id}/disable`）、删除（`DELETE .../{id}`）接口，按 `bizType` 各自独立的"审批开关"决定生效方式：开关开启时，调用后 SHALL NOT 立即生效——请求体校验通过且提交人有权限操作目标数据（管辖组织范围）后，系统生成一条待审批的变更申请并返回申请信息，不再返回创建/更新后的业务数据；只有当审批人批准该申请后，系统才执行原有的创建/更新/状态切换/删除逻辑（含其中已有的唯一性校验、父子关系约束等业务规则），审批人拒绝则业务数据保持不变。开关关闭时，行为与本 change 之前完全一致：调用即立即生效，直接返回业务数据，不生成审批申请。四个 `bizType` 的开关默认值均为"关闭"，系统初始化后行为与本 change 之前完全一致，需管理员在"审批设置"页面按需手动开启。
- 新增审批开关的数据模型与管理页面：组织、用户、任职、应用四个 `bizType` 各自独立的开关，新增权限点（`ApprovalManagement:switch:view`/`ApprovalManagement:switch:edit`）与"审批设置"页面，仅授权用户可查看/修改。
- 新增 `master-data-approval-workflow` 能力：审批开关、审批申请的数据模型、提交/审批/拒绝/撤回接口、基于 Flowable 的流程引擎集成（每条申请对应一个 Flowable 流程实例，单一用户任务节点承载审批动作）、固定审批权限点（`ApprovalManagement:request:approve`，持有该权限点的任意用户均可处理待审批任务，不依赖组织架构或指定审批人）、"我的申请"/"待我审批"前端页面。
- 四个管理页面的新增/编辑/启用/停用/删除交互调整为按接口响应区分"直接生效"（开关关闭）与"提交成功，等待审批"（开关开启）两种文案与后续动作。
- 不涉及批量导入（`excel-import-export`/`master-data-excel-export`）：批量导入产生的新增/更新继续直接生效，不接入审批流程。
- 不涉及组织、用户、任职、应用之外的其他资源（角色、权限点、管理员、菜单、字典等）。

## Capabilities

### New Capabilities
- `master-data-approval-workflow`：组织/用户/任职/应用四类主数据的新增、编辑、启用、停用、删除操作的通用审批流程能力——提交、单级审批（通过/拒绝）、撤回、查询（我的申请/待我审批），基于 Flowable 流程引擎驱动申请状态流转，审批通过后复用各业务模块既有的创建/更新/状态切换/删除逻辑落库。

### Modified Capabilities
- `org-management`：新增组织、更新组织、组织启用与停用、组织逻辑删除这四项需求的生效方式改为"提交审批、审批通过后生效"。
- `user-management`：新增用户、更新用户（含任职记录整体更新）、用户启用与停用、用户逻辑删除这四项需求的生效方式改为"提交审批、审批通过后生效"。
- `position-management`：新增任职记录、更新任职记录、任职记录启用与停用、任职记录逻辑删除这四项需求的生效方式改为"提交审批、审批通过后生效"。
- `application-management`：新增应用、更新应用、应用启用与停用、应用逻辑删除这四项需求的生效方式改为"提交审批、审批通过后生效"。

## Impact

- 后端：新增 `cn.nihility.rbac.approval` 包（entity/dto/mapper/service/controller/mapstruct/constant），新增 `tab_approval_request` 表；引入 Flowable BPMN 流程定义文件与相关 Spring 配置（`flowable.database-schema-update`、异步执行器等）；改造组织/用户/任职/应用四个模块现有的 Controller，由 Controller 在最外层查询审批开关：关闭时直接调用本模块原有 Service，开启时才调用审批申请 Service。审批通过后仍复用既有创建/更新/状态切换/删除逻辑，不重写业务规则。
- 前端：新增审批相关页面（我的申请、待我审批），四个管理页面的新增/编辑/启用/停用/删除交互文案与后续动作调整。
- 数据库：新增 `tab_approval_request` 表；Flowable 自身的流程引擎表由 `flowable-spring-boot-starter` 按配置自动建表。
- 权限：新增 `ApprovalManagement:request:view`、`ApprovalManagement:request:approve`、`ApprovalManagement:switch:view`、`ApprovalManagement:switch:edit` 四个权限点，同步更新 `权限资源.txt`。
- 数据库：另新增 `tab_approval_switch` 表（四个 `bizType` 各一条开关记录，默认关闭，需管理员手动开启）。
- 不影响 `excel-import-export`/`master-data-excel-export`、`org-scope-data-permission`、`operation-log-management` 已有能力的规范文本（管辖组织范围校验、操作日志记录仍复用现有实现，只是触发时机从"接口调用时"变为"审批通过时"，不改变这些能力对外的行为契约）。
