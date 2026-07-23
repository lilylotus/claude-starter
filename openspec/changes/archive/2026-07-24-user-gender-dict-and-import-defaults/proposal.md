## Why

人员（USER）管理与批量导入功能上线后，用户反馈三个相互关联的问题：

1. 人员的"性别"目前是一个后端 Java 常量类（`UserGender`：0=未知/1=男/2=女）硬编码驱动的
   字段，既不在"元数据字段"目录里，也不是"表单字段定义"能管理的动态字段——新增/编辑人员
   时性别下拉框的选项被写死在前端代码里，管理员没有任何入口调整这些选项（比如以后要新增
   "不愿透露"这个选项，只能改代码重新发版）。用户希望比照"任职类型"（`position_type`）
   已经用字典驱动的做法，把性别也改成字典驱动、可通过"字典管理"页面维护选项。
2. 正因为性别没有进入"元数据字段"目录、也就没有对应的"表单字段定义"，在"表单管理 -
   导入模板配置"里为人员（USER）配置 Excel 导入列时，字段选择器里根本看不到"性别"这个
   选项，导致管理员无法为人员批量导入配置性别这一列。
3. 人员批量导入时，如果 Excel 里"显示序号"或"性别"这两列留空，无论管理员是否已经把
   "导入字段配置"（导入表单）或"表单字段定义"（字段表单）里对应列的"是否必填"都改成
   非必填，系统仍然报"显示序号不能为空""性别不能为空"，配置形同虚设。

## What Changes

- 人员的"性别"改造为与"手机号""身份证号"同等地位的普通可配置字段：新增字典类型
  `gender`（性别，含 `unknown`/`male`/`female` 三个字典项，通过数据库迁移预置，可在
  "字典管理"页面追加/调整选项）；`tab_user.gender` 列由 `INT`（0/1/2）改为
  `VARCHAR(64)`（存字典项编码），历史数据按原有码值语义原地转换；新增对应的"元数据字段"
  与默认"表单字段定义"（控件类型为"字典下拉"，绑定 `gender` 字典类型），人员管理页面的
  性别选择改为动态渲染（走既有的"表单字段定义"动态表单/表格体系），不再走前端硬编码的
  `USER_GENDER_OPTIONS`；后端 `UserGender` 常量类同步移除。改造完成后，"表单管理 - 导入
  模板配置"里管理员就能像选择"手机号"一样为人员（USER）选择"性别"作为导入列（修复问题 2）。
- 修复批量导入引擎绑定字段值的一个通用缺陷：Excel 单元格留空时，系统会把该空字符串强行
  设置到请求对象对应的数值类型（`Integer`/`Long` 等）属性上，被 Spring 的类型转换隐式变成
  `null`，从而触发该属性上原有的非空校验（如"显示序号"）——即使管理员已经把这一列配置为
  非必填。修复后，数值类型属性在单元格留空时不再被设置，保留请求对象自身声明的默认值
  （如"显示序号"默认 `0`），不再触发非空校验；这个修复对组织/人员/任职/应用四类业务对象
  都生效，不只针对人员。字符串类型属性（如备注、扩展字段）留空时的行为不变，仍然显式清空，
  不受本次改动影响。

## Capabilities

### Modified Capabilities
- `user-management`：性别字段的数据模型与前端渲染方式调整。
- `form-field-definition-management`：新增人员（USER）性别的默认元数据字段与表单字段
  定义。
- `dict-management`：新增预置的 `gender` 字典类型及其字典项。
- `excel-import-export`：修复数值类型字段在 Excel 单元格留空时错误触发非空校验、无法
  通过"非必填"配置绕过的问题。

## Impact

- 后端：新增 Flyway 迁移（字典种子数据、`tab_user.gender` 列类型转换与历史数据重映射、
  元数据字段与表单字段定义种子数据）；`UserEntity`/`UserCreateRequest`/
  `UserUpdateRequest`/`UserVO` 的 `gender` 字段类型由 `Integer` 改为 `String`；移除
  `cn.nihility.rbac.user.constant.UserGender` 常量类及其全部引用；
  `cn.nihility.rbac.excelimport.service.support.ImportRowExecutor` 的字段绑定逻辑
  （`bindProperties`）调整。
- 前端：`UserManagementView.vue`/`UserDetailView.vue` 的性别相关硬编码（`USER_GENDER_*`
  常量、静态下拉选项、`genderLabel` 函数、表单静态 key）移除，改由
  `useDynamicFormFields('USER')` 动态渲染；`types/user.ts` 移除 `USER_GENDER_*` 常量与
  相关类型。
- 文档：`openspec/specs/user-management/spec.md`、
  `openspec/specs/form-field-definition-management/spec.md`、
  `openspec/specs/dict-management/spec.md`、`openspec/specs/excel-import-export/spec.md`
  更新对应需求条目。
