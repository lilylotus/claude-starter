## Why

组织、人员、任职、应用四类主数据目前只能逐条录入，批量建档（如系统上线初始化、部门整体迁移）
没有高效手段。项目里"表单管理"页面已经按业务对象类型（`bizType`）维护了一套字段定义体系
（`tab_form_field_definition`），具备"哪些字段存在、字段标识是什么、是否必填、显示顺序"这些
元数据，可以在此基础上追加一层"Excel 导入字段配置"，让管理员为每个业务对象类型自行决定
导入 Excel 用哪些列、列头叫什么、哪些列是匹配已有记录的主键、哪些列必填，然后由系统据此生成
标准导入模板并驱动批量导入，避免为四个对象各自硬编码一套导入逻辑。

## What Changes

- 新增"Excel 导入字段配置"数据模型（`tab_import_field_config`），按 `bizType`
  （ORG/USER/POSITION/APP）维护一组导入列配置：关联已启用的表单字段定义、Excel 表头名称
  （`excelHeaderName`，可独立于表单展示名称自定义）、是否主键（`isPrimaryKey`，导入时用于
  匹配已有记录做更新而非新建）、是否必填（`isRequired`，导入语义下的必填，独立于表单定义的
  必填开关）、显示序号（`showOrder`，决定模板表头列顺序）。
- 表单管理页面（`/system/form-fields`）新增"导入模板配置"tab，在组织/人员/任职/应用四个
  分类下维护各自的导入字段配置列表（新增/编辑/删除/排序），必填列在表格中以红色字体标出。
- 新增按 `bizType` 生成 Excel 导入模板的下载接口：表头按 `showOrder` 升序排列，必填表头
  加粗标红，供管理员下载后按模板整理数据。
- 新增按 `bizType` 批量导入接口：上传 Excel 文件，按导入字段配置解析每一行，用主键列匹配
  已有记录（存在则更新、不存在则新建），执行必填校验与既有业务校验（复用各模块现有的
  create/update service），返回本次导入的成功条数与失败明细（行号 + 原因），采用逐行独立
  提交、汇总失败明细的事务边界（不因个别行失败回滚整批）。
- 任职（POSITION）导入的 Excel 行需要额外提供人员、组织、职位等关联维度的标识列（而非仅
  任职记录自身字段），映射规则在 design.md 中明确。组织（ORG）导入同理需要额外提供"上级
  组织编码"列才能定位上级组织（否则批量导入的组织只能是顶级节点，无法维护组织层级），
  规则见 design.md Risks 部分。
- 组织、人员、任职、应用四个管理页面的工具栏新增"下载导入模板"和"批量导入"按钮；批量导入
  用上传弹窗，导入完成后展示成功数与失败明细列表。
- 同步更新仓库根目录 `权限资源.txt`，为四个管理页面新增的导入相关按钮补充权限资源编码。

## Capabilities

### New Capabilities
- `excel-import-export`: 按业务对象类型配置 Excel 导入字段、生成导入模板、执行批量导入
  （含主键匹配更新、必填校验、失败明细回执）的通用能力，覆盖组织/人员/任职/应用四类对象。

### Modified Capabilities
（无——组织/人员/任职/应用四个管理页面新增的"下载模板/批量导入"入口是新增能力的前端接入点，
不改变这四个模块已有的 CRUD 相关需求条目。）

## Impact

- 后端：新增 `cn.nihility.rbac.excelimport`（或类似命名）模块：entity/mapper/service/
  controller/dto/mapstruct，新增 Flyway migration 建 `tab_import_field_config` 表；
  新增/复用 org、user、position、app 四个模块现有 service 的 create/update 方法用于批量
  写入；新增基于 Apache POI（已有依赖，无需改 `build.gradle`）的模板生成与解析工具。
- 前端：`views/system/formfields/` 新增导入配置 tab 及其弹窗组件；
  `views/identity/org/OrgManagementView.vue`、`views/identity/.../UserManagementView.vue`
  （需确认用户管理页面路径）、`views/identity/position/PositionManagementView.vue`、
  `views/application/app/AppManagementView.vue` 四个页面工具栏新增按钮与上传/结果弹窗；
  `src/api/`、`src/types/` 新增对应模块。
- 文档：`权限资源.txt` 新增导入相关按钮编码；`openspec/specs/` 新增
  `excel-import-export/spec.md`。
