## Context

- `excel-import-export` 能力目前只有"下载导入模板"（`GET /api/excel-import/template?bizType=`）与
  "批量导入"（`POST /api/excel-import/batch`），两者都由 `cn.nihility.rbac.excelimport` 包实现，
  依赖已引入的 `org.apache.poi:poi`/`poi-ooxml:5.4.1`；模板生成用 `XSSFWorkbook`（只写一行表头 +
  数据校验，体量小）。列配置来自 `tab_import_field_config`（"导入字段配置"），语义是"这一列参与
  批量导入匹配/校验"，包含专为导入场景设计的固定标识列（`__userCode`/`__orgCode`/`__ownerCode`/
  `__parentCode`），与本次要新增的"导出"完全是两回事——见下方 Decision 2。
- `form-field-definition-management` 能力的 `tab_form_field_definition` 已有 `showInList`/
  `showInCreate`/`showInEdit`/`editable`/`isRequired`/`isUnique` 等独立布尔开关，"承重字段"（组织/
  用户/应用的 `name`/`code`，任职的 `position_type`）通过 `LockedFormFields` 白名单在读取时计算出
  只读的 `locked` 标记，`FormFieldDefinitionServiceImpl` 对锁定字段只保护
  `isRequired`/`showInCreate`/`showInEdit`/状态/删除这几项，其余属性（含展示名称、显示序号）仍可
  自由调整。
- `org-scope-data-permission` 提供 `OrgScopeService.resolveAllowedOrgIds(userId)`：返回空
  `Optional` 表示不受限，非空表示受限组织 id 全集（已展开 `include_children`）。该能力当前的
  接入范围：`OrgServiceImpl`（组织树/子组织分页，按组织 id 过滤）、`PositionServiceImpl`（任职分页，
  按"选中的单个 orgId 是否在允许集合内"整体放行/拒绝，不支持跨多个 org 一次性查询）、
  `AppServiceImpl`（应用分页，直接 `WHERE org_id IN (:allowed)`，天然支持跨组织查询，不依赖 UI
  是否选中某个组织节点）。`GET /api/users` 明确未接入组织范围收紧（spec 原文："用户列表暂不纳
  入——用户与组织是通过 `tab_user_position` 的间接关系，留待后续独立能力处理"）。
- 四个管理页面的列表表格列分两类：一类是由 `renderSchema`/`listColumns` 驱动的动态列（`col.fieldCode`
  对应表单字段定义，`col.columnName` 是前端从行对象取值的 key）；另一类是页面硬编码、不经过表单
  字段定义体系的"关联展示"列——任职页面在动态列**之前**固定展示"姓名"（`userName`）/"组织"
  （`orgName`）两列，应用页面在动态列**之后**固定展示"负责人"（`ownerName`）/"所属组织"
  （`orgName`）两列；组织、用户页面没有这类固定关联列。`orgId`/`userId`/`ownerId`/`parentId` 本身是
  选择器字段，不在 `tab_metadata_field`/`tab_form_field_definition` 体系内。

## Goals / Non-Goals

**Goals:**
- 新增按 `bizType` 导出 Excel 的接口与四个页面的入口，导出内容按当前登录用户的管辖组织范围收窄
  （复用 `OrgScopeService` 现有能力，不修改其实现）。
- 导出列由 `tab_form_field_definition` 新增的 `showInExport` 开关驱动，默认初始化与
  `showInList` 一致，此后两者独立可调。
- 导出内容与页面列表展示保持一致的可读性：字典/多选字典列展示 label 而非 code，任职/应用的关联
  展示列（姓名/组织、负责人/所属组织）随导出一并给出，否则脱离页面上下文的 Excel 行会因为看不到
  组织/人员而失去意义。

**Non-Goals:**
- 不支持导出时自定义筛选条件（如按关键字/状态过滤后再导出）——本次导出语义是"导出我当前权限
  范围内的全部数据"，不是"导出当前页面筛选/分页后的结果"。
- 不修改 `tab_import_field_config` 或批量导入现有行为。
- 不修改 `org-scope-data-permission` 已有解析/校验能力的实现，也不新增用户列表的组织范围收紧
  （见 Decision 2 关于用户导出范围的结论）。
- 不实现导出列的用户可视化拖拽排序等超出"复用 `showOrder`"之外的排版能力。

## Decisions

### 1. 导出范围：全量权限范围内数据，而非"当前页面筛选/分页结果"
需求原文是"按当前登录用户权限范围导出组织、用户、任职、应用数据"，不是"导出当前列表视图"。
因此每个 `bizType` 的导出查询直接基于管辖组织范围解析结果构造（不受 UI 当前选中的组织树节点、
搜索关键字影响）：
- ORG：复用 `OrgServiceImpl` 内部"查询全部组织实体 + 按 `allowedOrgIds` 过滤"的既有逻辑（与
  `buildOrgTree` 用的是同一段过滤代码），导出前不做树形组装，按 `id` 升序输出为扁平行。
- USER：直接查询全部未删除用户，不做组织范围收紧（与 `GET /api/users` 现状保持一致，见
  Decision 2 的理由展开）。
- POSITION：新增一个"按管辖范围查询全部任职记录"的查询方法（区别于现有按单个 `orgId` 分页的
  `queryPage`），受限时 `WHERE org_id IN (:allowedOrgIds)`，不受限时不加过滤；复用现有
  `userPositionMapper` 的关联查询（同一个联表 SQL，只是去掉按单一 `orgId` 过滤、去掉分页）。
- APP：复用 `AppServiceImpl` 现有分页查询已经具备的"按 `allowedOrgIds` 过滤、不依赖 UI 选中
  组织"逻辑，去掉分页上限即可，无需新增查询方法。

为避免导出海量数据拖垮内存/接口耗时，导出行数设置安全上限 **50000 行**：查询到的行数超过该
上限时，接口拒绝生成文件，返回业务错误提示"待导出数据量过大（超过 5 万行），请缩小管辖范围后
再试"。该上限对本项目典型数据规模（组织/人员/任职/应用四类主数据）是相当宽松的安全阀，不是
预期会被触发的正常路径。

**备选方案**：导出当前页面已应用的筛选/分页参数（"导出当前视图"）。未采用，因为需求明确写的是
"按权限范围导出"而非"按当前筛选导出"，且任职页面的现有列表天然是"选中单个组织节点"的视图，
若照搬会导致导出功能同样只能导出单个组织下的数据，与"按权限范围导出全部数据"的诉求不符。

### 2. 用户导出维持现状：不做组织范围收紧
`org-scope-data-permission` 的 spec 明确把用户列表排除在收紧范围之外，理由是用户与组织是通过
`tab_user_position` 的间接关系，收紧规则本身还没定义清楚（一个用户可能在多个组织任职）。导出
功能不是重新设计这套间接关系收紧规则的合适时机，也超出本次改动范围（proposal.md 已声明"不涉及
修改 `org-scope-data-permission` 已有解析/校验能力的实现"）。因此用户导出行为与 `GET /api/users`
当前行为保持一致：不做组织范围收紧，管辖范围受限的管理员导出用户 Excel 时得到的是全量用户数据。
这一点与组织/任职/应用三者的导出行为不对称，是有意为之，不是遗漏；后续如果要为用户列表引入组织
范围收紧，应作为独立 change 同时修改 `GET /api/users` 与用户导出两处，保持二者语义一致。

### 3. 导出列的配置来源：`tab_form_field_definition.showInExport`，不复用 `tab_import_field_config`
新增布尔列 `show_in_export`，风格上与 `showInList`/`showInCreate`/`showInEdit` 同级独立开关，
不受 `LockedFormFields` 承重字段保护规则约束（现有保护范围只覆盖
`isRequired`/`showInCreate`/`showInEdit`/状态/删除，导出可见性与"字段是否可被误停用/放宽必填"
这类数据完整性保护无关，管理员可以自由调整任意字段——含承重字段——的导出可见性）。

不复用 `tab_import_field_config` 的原因：
- 语义不同。导入字段配置回答"这一列如何参与批量导入的表头匹配与主键判定"，天然包含只服务于
  导入场景的固定标识列（`__userCode`/`__orgCode`/`__ownerCode`/`__parentCode`），这些列导出时
  没有直接对应的展示需求（导出更适合展示关联对象的人类可读名称，见 Decision 4）。
- 独立可调性诉求冲突。需求明确要求"导出"是可以脱离"是否列表展示"独立配置的开关；如果复用导入
  配置，"是否导出"和"是否参与导入匹配/是否主键"会被绑在同一张表的同一条记录上，改动导出可见性
  就有误改导入行为的风险，且导入配置的必填/主键语义对导出没有意义。
- 一个字段可以"可导出但不可导入"（如系统生成的只读字段）或反过来，两套配置各自独立更灵活。

### 4. 导出列集合与顺序：字段定义驱动列 + 页面既有的固定关联展示列
导出列由两部分拼接而成，顺序与页面列表展示的相对位置保持一致：
- 字段定义驱动列：查询该 `bizType` 下状态为启用（`2000`）且 `showInExport=true` 的表单字段
  定义，按 `showOrder` 升序，与 `render-schema`/`listColumns` 使用同一条查询路径（追加
  `showInExport=true` 过滤条件）。
- 固定关联展示列：POSITION 在字段定义驱动列**之前**追加"姓名"（`userName`）、"组织"
  （`orgName`）；APP 在字段定义驱动列**之后**追加"负责人"（`ownerName`）、"所属组织"
  （`orgName`）；ORG、USER 不追加任何固定列。这两列本身不在表单字段定义体系内（`orgId`/
  `userId`/`ownerId` 是选择器，不是元数据字段），但页面列表一直固定展示它们，导出如果不带上，
  导出的 Excel 行会因为看不到人员/组织归属而失去可用性——这与批量导入需要 `__userCode`/
  `__orgCode` 等固定标识列才能定位记录是同一类必要性，只是导出场景要展示的是人类可读名称而不是
  用于匹配的编码。这两列固定包含，不受 `showInExport` 开关影响（页面上它们也不是可配置列）。

### 5. 列取值方式：复用既有 VO + 按 `columnName` 反射取值
导出查询直接复用各模块已有的列表 VO（`OrgVO`/`UserVO`/`PositionVO`/`AppVO`，与
`GET /api/orgs`/`/api/users`/`/api/positions`/`/api/apps` 返回的是同一套结构），对每个字段定义
驱动列，用 Spring `BeanWrapperImpl` 按 `columnName` 反射读取属性值——这与前端动态表格已经在用的
`(row as Record<string, unknown>)[col.columnName]` 取值方式是同一个约定，不需要为每个 `bizType`
新增一套硬编码的字段名到取值函数的映射表，新增字段定义时导出自动跟着覆盖到。

### 6. 字典/多选字典列展示 label 而非 code
新增一个 `ExportDictLabelSupport`（`cn.nihility.rbac.excelexport.support`），复用
`DictItemService.getEnabledOptions(dictTypeCode)` 得到 code→label 映射：`controlType=DICT` 的
列直接按 code 查 label；`controlType=MULTI_DICT` 的列按英文逗号切分后逐个查 label 再用顿号"、"
拼接，与 `FormFieldSnapshotSupport`/操作日志变更快照的展示口径完全一致；任一 code 在当前启用
字典项中找不到匹配时回退展示原始 code，不留空、不报错。

不直接复用 `FormFieldSnapshotSupport` 本体：该组件的输入输出是"操作日志 `Map<String, Object>`
快照、只处理 `ext1`~`ext10`"，是为操作日志这个具体场景写的，硬编码了 ext 列名集合与快照 map 结构，
不是一个通用的"给 controlType/dictTypeCode/rawValue，返回展示文案"的纯函数；新增的
`ExportDictLabelSupport` 只保留可复用的核心换算逻辑（分隔符、拼接符、回退规则与
`FormFieldSnapshotSupport` 保持一致，确保导出结果和操作日志、页面展示三处口径统一），服务于
导出场景按任意 `columnName`（不限于 ext1~10）取值后再换算的需求。

### 7. Excel 生成：复用现有 POI 依赖，新增 `excelexport` 包，用 `SXSSFWorkbook` 流式写入
不引入新依赖，继续用已声明的 `org.apache.poi:poi:5.4.1`/`poi-ooxml:5.4.1`。新增
`cn.nihility.rbac.excelexport` 包（`controller`/`service`/`service.impl`/`support`），结构与
`excelimport` 平行但不共享代码（两者对 POI 的使用场景不同：导入模板要写数据校验下拉与文本格式
保护，导出只需要写表头 + 只读展示文本，不需要任何单元格格式/校验规则）。导出用
`SXSSFWorkbook`（而非模板生成用的 `XSSFWorkbook`）做流式写入，因为导出行数上限（Decision 1）
比模板生成（固定只写一行表头）高出多个数量级，流式写入可以控制内存占用不随行数线性增长过快。

接口沿用导入模板下载接口的既有风格：
```
GET /api/excel-export/download?bizType=ORG|USER|POSITION|APP
→ 200，Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
   Content-Disposition: attachment; filename*=UTF-8''<urlencoded文件名>
```
文件名规则与导入失败明细文件保持同一风格（中文名 + 动作 + 时间戳，短横线连接）：
`<bizType中文名>导出-yyyyMMddHHmm.xlsx`（如 `任职导出-202608281530.xlsx`）。

### 8. 迁移与默认值回填
新增一条 Flyway 迁移脚本（下一个可用的 `V*` 序号），为 `tab_form_field_definition` 增加
`show_in_export` 列（`TINYINT(1) NOT NULL DEFAULT 1`，与项目里其余布尔展示开关列类型一致），
随后执行 `UPDATE tab_form_field_definition SET show_in_export = show_in_list`，对全部存量记录
（含承重字段）按当前 `show_in_list` 值回填，新增列不区分 `bizType`。迁移脚本遵循仓库约定
（不使用 MySQL 8.0+/窗口函数等版本相关写法——这里只是简单的 `ADD COLUMN` + `UPDATE`，本身就是
标准可移植 SQL）。

### 9. 权限编码
新增四个按钮级权限编码，命名与现有导入权限码同构：`OrgManagement:org:export`、
`UserManagement:user:export`、`PositionManagement:position:export`、`AppManagement:app:export`；
四个管理页面工具栏的"导出Excel"按钮分别用 `hasPermission('<对应编码>')` 门控，未拥有对应权限时
按钮不渲染（与 `permission-driven-visibility` 现有的按钮级权限门控规则一致）。同步更新仓库根目录
`权限资源.txt`。

### 10. 前端集成
新增 `frontend/src/api/excelExport.ts`，与现有 `api/excelImport.ts` 的"下载导入模板"函数同构：
`request.get('/excel-export/download', { params: { bizType }, responseType: 'blob' })`，拿到
`Blob` 后用 `URL.createObjectURL` 触发浏览器下载，文件名取响应头 `Content-Disposition`（或退化为
前端拼装的默认文件名，与导入模板下载现有实现一致）。四个管理页面工具栏在"批量导入"按钮之后、
"新增"按钮之前插入"导出Excel"按钮。

## Risks / Trade-offs

- [大数据量导出可能拖慢接口/占用较多内存] → Decision 1 的 5 万行安全上限 + Decision 7 的
  `SXSSFWorkbook` 流式写入；超限返回明确错误提示引导用户缩小范围，而不是让接口卡死或 OOM。
- [POSITION 新增"按管辖范围查询全部任职记录"的方法与现有按单一 `orgId` 分页的查询存在少量 SQL
  重复] → 可接受：两者的过滤条件不同（一个是精确匹配单个 `orgId`，一个是 `IN` 一组 `orgId` 或
  不过滤），勉强合并成一个方法会引入不必要的参数分支，保持两个独立、各自简单的方法更清晰，
  与 `AppServiceImpl` 现有列表查询已经是"不依赖 UI 选中组织"的直接实现保持同构。
- [反射按 `columnName` 取值（Decision 5）对 VO 字段命名产生隐性耦合] → 可接受：前端动态表格已经
  依赖同样的约定（`columnName` 对应行对象的属性名），导出复用这个既有约定不引入新的耦合面，
  只是把同一个假设也用在后端。
- [用户导出与组织/任职/应用三者的范围收紧行为不对称（Decision 2）] → 属于有意为之的现状延续，
  已在 Decision 2 写明理由与后续演进路径，不属于本次改动的缺陷。

## Migration Plan

1. 新增 Flyway 迁移脚本：加列 `show_in_export` + 按 `show_in_list` 回填初始值（Decision 8）。
2. 后端新增 `excelexport` 包与四个业务模块各自的"导出用查询方法"（Decision 1、5），`formfield`
   模块的实体/DTO/Service/Mapper 增加 `showInExport` 字段读写。
3. 前端新增导出 API 封装、四个管理页面新增按钮、表单管理字段定义弹窗新增"是否导出"勾选项。
4. `权限资源.txt` 同步新增四条导出权限编码。
5. 纯新增列 + 新增接口，不影响存量数据与既有接口行为，无需特殊回滚步骤；如需回滚，删除新增
   迁移脚本对应的列即可（项目当前迁移历史里没有"回滚脚本"先例，遵循现状不新增）。
