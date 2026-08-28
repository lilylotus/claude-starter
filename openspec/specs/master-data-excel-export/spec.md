# master-data-excel-export

## Purpose

为组织、用户、任职、应用四类主数据提供按当前登录用户管辖组织范围收窄的 Excel 导出能力，导出列的
选取与顺序由表单字段定义的"是否导出"配置驱动，与批量导入使用的"导入字段配置"相互独立。

## Requirements

### Requirement: 按业务对象类型导出 Excel
系统 SHALL 提供按 `bizType`（ORG/USER/POSITION/APP）导出 Excel 的接口，返回一个 `.xlsx` 文件；
文件名 SHALL 为该 `bizType` 的中文名称、固定后缀"导出"与生成时刻时间戳（`yyyyMMddHHmm`）依次以
短横线连接（如 `任职导出-202608281530.xlsx`）。首行为表头，之后每一行对应一条业务数据记录。

#### Scenario: 导出组织数据
- **WHEN** 客户端请求 `bizType=ORG` 的导出接口
- **THEN** 系统返回一个 `.xlsx` 文件，文件名形如 `组织导出-202608281530.xlsx`，首行为表头，之后
  每行对应一条组织记录

#### Scenario: 导出人员数据
- **WHEN** 客户端请求 `bizType=USER` 的导出接口
- **THEN** 系统返回一个 `.xlsx` 文件，文件名形如 `人员导出-202608281530.xlsx`

### Requirement: 导出范围按当前登录用户的管辖组织范围收窄（用户导出除外）
组织、任职、应用三类导出 SHALL 复用"解析当前登录用户管辖组织范围"的既有能力：解析结果为不受
限制时导出该 `bizType` 下全部未删除记录；解析结果为受限时，组织导出只包含允许集合内的组织，
任职、应用导出只包含所属组织（`orgId`）落在允许集合内的记录。人员导出 SHALL NOT 按管辖组织范围
收窄，始终导出全部未删除用户记录，与 `GET /api/users` 当前不做组织范围收紧的行为保持一致。

#### Scenario: 管辖组织范围受限时任职导出只包含范围内的记录
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，允许集合不包含组织 D；客户端请求
  `bizType=POSITION` 的导出接口
- **THEN** 返回的 Excel 中不包含所属组织为 D 的任职记录，只包含所属组织落在允许集合内的记录

#### Scenario: 管辖组织范围受限时人员导出仍包含全部用户
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限；客户端请求 `bizType=USER` 的导出接口
- **THEN** 返回的 Excel 包含当前全部未删除用户记录，不因管辖组织范围受限而减少

#### Scenario: 管辖组织范围不受限时导出全部数据
- **WHEN** 当前登录用户的管辖组织范围解析结果为不受限制；客户端请求 `bizType=ORG`/`POSITION`/
  `APP` 中任一的导出接口
- **THEN** 返回的 Excel 包含该 `bizType` 下全部未删除记录

### Requirement: 导出列由表单字段定义的"是否导出"配置驱动
导出 Excel 的列（除"任职、应用导出包含固定关联展示列"描述的固定列外）SHALL 取自该 `bizType`
下状态为启用（`2000`）且"是否导出"（`showInExport`）为真的表单字段定义，按 `showOrder` 升序
排列表头顺序与列顺序，与"导入字段配置"（`tab_import_field_config`）完全无关——两者是相互独立的
配置，调整导入字段配置 SHALL NOT 影响导出列，反之亦然。

#### Scenario: 只有勾选是否导出的字段出现在导出结果中
- **WHEN** `bizType=ORG` 下某条状态为启用的字段定义 `showInExport=false`，其余字段定义
  `showInExport=true`
- **THEN** 导出的 Excel 表头与数据列中不包含该字段定义对应的列，其余字段正常导出

#### Scenario: 停用状态的字段定义不参与导出
- **WHEN** `bizType=USER` 下某条字段定义状态为停用（`3000`）且 `showInExport=true`
- **THEN** 该字段定义不出现在导出结果的列中

#### Scenario: 导出列顺序按显示序号排列
- **WHEN** `bizType=APP` 下多条 `showInExport=true` 的启用字段定义具有不同的 `showOrder`
- **THEN** 导出结果中这些字段对应的列按 `showOrder` 升序排列

### Requirement: 导出的字典/多选字典列展示标签而非编码
导出 Excel 时，关联的表单字段定义 `controlType` 为下拉单选字典（`DICT`）的列，单元格内容 SHALL
为该字段当前存储编码对应的字典项标签（`label`）；`controlType` 为多选字典下拉（`MULTI_DICT`）的
列，单元格内容 SHALL 为按逗号切分后逐个换算标签、再用顿号"、"拼接的文本。任一存储编码在当前
启用字典项中找不到匹配时（如字典项已停用或删除），该编码位置 SHALL 回退展示原始编码，不留空、
不报错。

#### Scenario: 单选字典列展示标签
- **WHEN** 导出 `bizType=USER` 数据，某条记录的"性别"字段存储编码对应字典类型 `gender` 下一个
  当前启用的字典项
- **THEN** 导出结果中该记录"性别"列展示该字典项的标签（如"男"），而非原始存储编码

#### Scenario: 多选字典列展示多个标签
- **WHEN** 导出数据中某条记录一个多选字典下拉字段存储值为多个逗号分隔的有效字典项编码
- **THEN** 导出结果中该列展示用顿号"、"拼接的多个字典项标签

#### Scenario: 字典项找不到时回退展示原始编码
- **WHEN** 导出数据中某条记录一个字典类字段存储的编码在当前启用字典项中查不到匹配
- **THEN** 导出结果中该列展示原始存储编码，不影响其余可正常解析字段的展示

### Requirement: 任职、应用导出固定包含关联展示列
任职（POSITION）导出的表头与数据列 SHALL 在字段定义驱动列之前固定包含"姓名"（对应所属用户的
姓名）与"组织"（对应所属组织的名称）两列；应用（APP）导出的表头与数据列 SHALL 在字段定义驱动
列之后固定包含"负责人"（对应负责人的姓名）与"所属组织"（对应所属组织的名称）两列。这两列不受
任何表单字段定义的"是否导出"开关影响，恒定包含，与页面列表展示这两列的既有行为保持一致。组织、
人员导出 SHALL NOT 包含此类固定关联展示列。

#### Scenario: 任职导出表头包含姓名与组织
- **WHEN** 客户端请求 `bizType=POSITION` 的导出接口
- **THEN** 返回的 Excel 表头中，字段定义驱动列之前包含"姓名""组织"两列，数据行对应展示所属
  用户姓名与所属组织名称

#### Scenario: 应用导出表头包含负责人与所属组织
- **WHEN** 客户端请求 `bizType=APP` 的导出接口
- **THEN** 返回的 Excel 表头中，字段定义驱动列之后包含"负责人""所属组织"两列，数据行对应展示
  负责人姓名与所属组织名称

### Requirement: 导出数据量上限保护
系统 SHALL 对单次导出请求匹配到的记录数设置上限 50000 行；查询到的待导出记录数超过该上限时，
系统 SHALL 拒绝生成导出文件，返回业务错误提示待导出数据量过大、建议缩小管辖范围后重试，不生成
任何文件内容。

#### Scenario: 待导出记录数超过上限时拒绝导出
- **WHEN** 某个 `bizType` 在当前管辖组织范围内待导出的记录数超过 50000 行
- **THEN** 系统拒绝本次导出请求，返回业务错误，不生成 `.xlsx` 文件

#### Scenario: 待导出记录数未超过上限时正常导出
- **WHEN** 某个 `bizType` 在当前管辖组织范围内待导出的记录数不超过 50000 行
- **THEN** 系统正常生成并返回导出的 `.xlsx` 文件

### Requirement: 管理页面的导出入口
系统 SHALL 在组织、人员、任职、应用四个管理页面的工具栏，在"批量导入"按钮之后新增"导出Excel"
按钮，点击后触发对应 `bizType` 的导出接口并由浏览器下载返回的 `.xlsx` 文件。该按钮 SHALL 分别
受四个按钮级权限编码门控：`OrgManagement:org:export`、`UserManagement:user:export`、
`PositionManagement:position:export`、`AppManagement:app:export`；当前登录用户不拥有对应权限
编码时，该按钮 SHALL NOT 渲染。

#### Scenario: 有导出权限时按钮正常展示
- **WHEN** 当前登录用户的权限编码集合包含 `OrgManagement:org:export`
- **THEN** 组织管理页面工具栏在"批量导入"按钮之后展示"导出Excel"按钮

#### Scenario: 无导出权限时按钮不展示
- **WHEN** 当前登录用户的权限编码集合不包含 `PositionManagement:position:export`
- **THEN** 任职管理页面不渲染"导出Excel"按钮

#### Scenario: 点击导出按钮触发文件下载
- **WHEN** 用户在应用管理页面点击"导出Excel"按钮
- **THEN** 前端调用 `bizType=APP` 的导出接口，成功响应后浏览器触发下载该次响应返回的 `.xlsx`
  文件
