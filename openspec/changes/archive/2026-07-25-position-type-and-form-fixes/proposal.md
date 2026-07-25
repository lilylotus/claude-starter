## Why

四个用户反馈的问题共同指向同一根因：任职类型（`positionType`）字段以及部分动态渲染表单项当前的实现，
偏离了"表单字段定义"驱动的统一渲染/保护体系，导致管理员可以误停用/删除任职类型字段、任职管理与用户
管理的新增编辑表单里任职类型仍是脱离表单管理配置的独立硬编码下拉框、动态扩展字段展示名称过长时表单
布局错位、以及导入模板配置的"关联字段"下拉未排除已占用字段导致可重复误配置。四处问题现在一起修复。

## What Changes

- 表单管理：把 `POSITION` 业务对象下 `positionType`（任职类型）纳入"承重字段"锁定保护名单，与既有
  ORG/USER/APP 的 `name`/`code` 六个字段一样，不可停用、删除、取消必填、取消新增/编辑展示。
- 任职管理（`/identity/positions`）：删除新增/编辑表单、详情页面中独立硬编码渲染"任职类型"下拉框及
  其单独的字典选项获取逻辑，改为并入"表单字段定义"驱动的动态渲染循环，与其余动态字段走同一套渲染/
  取值逻辑；列表页保持不变（任职类型列本就已是动态渲染的一部分）。**BREAKING**：`position-management`
  spec 中原先声明的"关联组织、关联用户、认证类型、状态字段保持硬编码渲染"这一约束不再适用于认证类型
  （`positionType`）。
- 用户管理（`/identity/users`）新增/编辑弹窗中"添加任职"子表单：修复动态扩展字段（`ext1`~`ext10`，
  展示名称来自表单管理中管理员自由配置的 `fieldName`）在展示名称超过 4 个汉字时标签换行导致与相邻列/
  下方控件重叠错位的问题，把"标签不换行、两列对齐"这一约束从"所属组织""任职类型"两个字段推广到子
  表单内全部字段（含动态扩展字段）。
- 导入模板配置（"表单管理"页面"导入模板配置" tab）：新增/编辑弹窗的"关联字段"选择器过滤掉当前
  `bizType` 下已被其他有效导入字段配置占用的表单字段定义，避免同一字段被重复配置；编辑现有配置时，
  该配置自身当前关联的字段仍需出现在选择器中（否则无法保留原值）。

## Capabilities

### New Capabilities
（无新增能力，四处修改均落在已有能力范围内）

### Modified Capabilities
- `form-field-definition-management`："承重字段的锁定保护"覆盖范围从 ORG/USER/APP 的 `name`/`code`
  扩展到 POSITION 的 `positionType`。
- `position-management`："任职记录字段的动态列表与表单渲染"需求调整：`positionType` 从"保持硬编码
  渲染"的排除名单中移出，改为随表单字段定义动态渲染；相应地"任职管理没有 `locked=true` 的字段定义"
  这一表述不再成立。
- `user-management`：任职子表单"标签不换行、两列对齐"的约束范围从"所属组织""任职类型"两个必填字段
  扩展到子表单内全部字段（含动态扩展字段）。
- `excel-import-export`：表单管理页面"导入模板配置"tab 的"关联字段"选择器新增过滤规则，排除当前
  `bizType` 下已被其他有效配置占用的表单字段定义（编辑态保留自身当前关联项）。

## Impact

- 后端：`backend/src/main/java/cn/nihility/rbac/formfield/constant/LockedFormFields.java`（新增一条
  白名单常量）；`position-management`/`excel-import-export` 相关 Controller/Service 可能需要为"关联
  字段"过滤或校验补充查询逻辑（视 design.md 而定）。
- 前端：`frontend/src/views/identity/position/PositionManagementView.vue`、`PositionDetailView.vue`
  （删除硬编码任职类型渲染，改走 `useDynamicFormFields`）；`frontend/src/views/identity/user/
  UserManagementView.vue`（任职子表单标签布局）；`frontend/src/views/system/formfields/
  ImportFieldConfigPanel.vue`（关联字段下拉过滤逻辑）。
- 数据库：无新增迁移脚本（`positionType` 的锁定判定是后端白名单常量计算得出，不落库）。
- 需要同步更新的既有能力 spec：`form-field-definition-management`、`position-management`、
  `user-management`、`excel-import-export`（见上方 Modified Capabilities）。
- `权限资源.txt`：本次改动不涉及新增/删除页面菜单或按钮，预计无需更新。
