## Why

组织、人员、任职、应用四个管理页面目前只有"下载导入模板"与"批量导入"，没有把当前查询范围内的数据导出为 Excel 的能力。日常盘点、离线核对、跨系统对账等场景下，管理员需要把（按自己的管辖组织范围收窄后的）数据落地成 Excel 文件，而不必逐页复制或直接查库。

## What Changes

- 组织、人员、任职、应用四个管理页面的工具栏，在"批量导入"按钮旁新增"导出Excel"按钮，点击后触发对应 `bizType` 的导出接口并下载生成的 `.xlsx` 文件。
- 新增按 `bizType`（ORG/USER/POSITION/APP）导出 Excel 的后端接口：列的选取与顺序取自该 `bizType` 下状态为启用、且"是否导出"为真的表单字段定义，按 `showOrder` 升序；字典单选/多选字典列导出时换算为字典项 `label`（与操作日志变更快照的展示口径一致），而不是原始 `code`。
- 组织/任职/应用的导出复用 `org-scope-data-permission` 现有的"解析当前登录用户管辖组织范围"能力收窄结果；人员导出维持与现有 `GET /api/users` 一致的"不做组织范围收紧"现状（说明见 design.md）。
- `tab_form_field_definition` 新增 `showInExport`（是否导出）布尔列，与既有 `showInList`/`showInCreate`/`showInEdit` 同级独立开关；表单管理页面的字段定义新增/编辑弹窗新增对应勾选项。
- 数据库迁移为存量字段定义按其当前 `showInList` 的值回填 `showInExport` 初始值；此后两者相互独立，管理员可在表单管理页面单独调整"是否导出"。
- 新增四个按钮级权限编码 `OrgManagement:org:export`、`UserManagement:user:export`、`PositionManagement:position:export`、`AppManagement:app:export`，并同步更新仓库根目录 `权限资源.txt`。

## Capabilities

### New Capabilities
- `master-data-excel-export`：按业务对象类型（ORG/USER/POSITION/APP）导出 Excel 的后端接口、列选取规则（基于字段定义的"是否导出"开关）、字典列的 label 换算、按管辖组织范围收窄导出结果，以及四个管理页面新增的"导出Excel"入口与对应按钮级权限编码。

### Modified Capabilities
- `form-field-definition-management`：`tab_form_field_definition` 新增 `showInExport` 字段及其默认初始化规则（迁移时与 `showInList` 保持一致）、表单管理页面新增/编辑弹窗新增对应勾选配置项、渲染元数据/分页/详情查询返回该字段。

## Impact

- 后端：新增 `cn.nihility.rbac.excelexport` 包（controller/service/impl，按 `bizType` 分发到组织/用户/任职/应用各自的查询与列组装逻辑），复用 `excelimport` 模块中已有的 POI 依赖（`org.apache.poi:poi`/`poi-ooxml`，无需新增依赖）与字典 label 换算、管辖组织范围解析等既有工具；修改 `formfield` 模块的实体/DTO/Service/Mapper 增加 `showInExport` 字段；新增一条 Flyway 迁移脚本为 `tab_form_field_definition` 加列并回填初始值。
- 前端：修改 `frontend/src/views/identity/org/OrgManagementView.vue`、`.../user/UserManagementView.vue`、`.../position/PositionManagementView.vue`、`views/application/` 下的应用管理页面工具栏，新增"导出Excel"按钮及触发下载逻辑；修改表单管理页面（`/system/form-fields`）字段定义新增/编辑弹窗，新增"是否导出"开关。
- 文档：`权限资源.txt` 新增四条导出按钮权限编码。
- 不涉及修改现有批量导入功能行为，不涉及修改 `org-scope-data-permission` 已有解析/校验能力的实现。
