## ADDED Requirements

### Requirement: 导入字段配置数据模型
系统 SHALL 提供 `tab_import_field_config` 表，按业务对象类型（`bizType`，取值
ORG/USER/POSITION/APP，与 `tab_form_field_definition.biz_type` 一致）维护 Excel 导入
列配置，每条配置包含：可空的关联表单字段定义（`formFieldDefinitionId`，关联
`tab_form_field_definition.id`）、字段标识（`fieldCode`）、Excel 表头名称
（`excelHeaderName`）、是否主键（`isPrimaryKey`）、是否必填（`isRequired`）、显示序号
（`showOrder`）、状态（2000=启用，-1000=已逻辑删除）。同一 `bizType` 下 `fieldCode`
SHALL 唯一（未删除范围内）。

#### Scenario: 新增导入配置关联已启用的表单字段定义
- **WHEN** 系统管理员为 `bizType=ORG` 新增一条导入配置，关联 `fieldCode=remark` 的表单
  字段定义，`excelHeaderName` 设为"备注说明"
- **THEN** 系统保存该配置，`fieldCode` 冗余存储为 `remark`，查询 `bizType=ORG` 的导入
  配置列表时能看到该条记录

#### Scenario: 同一业务对象类型下字段标识不可重复
- **WHEN** `bizType=USER` 下已存在一条 `fieldCode=mobile` 的有效导入配置，管理员尝试
  新增另一条 `fieldCode` 同样为 `mobile` 的配置
- **THEN** 系统拒绝创建，返回业务错误

### Requirement: POSITION 与 APP 预置固定标识列
系统 SHALL 通过数据库迁移为 `bizType=POSITION` 预置两条不可删除、不可取消必填的固定导入
配置：`fieldCode=__userCode`（表头默认"人员编号"，匹配 `tab_user.code`）与
`fieldCode=__orgCode`（表头默认"组织编码"，匹配 `tab_org.code`）；并为 `bizType=APP`
同样预置两条：`fieldCode=__ownerCode`（表头默认"负责人编号"，匹配 `tab_user.code`）与
`fieldCode=__orgCode`（表头默认"组织编码"，匹配 `tab_org.code`）——APP 的 `ownerId`/
`orgId` 与 POSITION 的 `userId`/`orgId` 一样是关联选择器而非表单字段定义体系内的展示
字段，但对应的创建/更新请求要求二者必填，需要同样的固定标识列机制才能被批量导入覆盖到。
上述四条配置的 `formFieldDefinitionId` 均为 `NULL`、`isPrimaryKey` 默认 `true`、
`isRequired` 恒为 `true`。更新/停用/删除这四条配置的请求 SHALL 被拒绝；其
`excelHeaderName`、`showOrder` 仍可调整。

#### Scenario: 尝试删除人员编号标识列被拒绝
- **WHEN** 客户端调用删除接口，目标是 `bizType=POSITION` 下 `fieldCode=__userCode` 的
  配置
- **THEN** 系统拒绝删除，返回业务错误

#### Scenario: 尝试取消组织编码标识列的必填被拒绝
- **WHEN** 客户端更新 `bizType=POSITION` 下 `fieldCode=__orgCode` 的配置，请求体
  `isRequired=false`
- **THEN** 系统拒绝该次更新中 `isRequired` 的变更，返回业务错误

#### Scenario: 尝试删除负责人编号标识列被拒绝
- **WHEN** 客户端调用删除接口，目标是 `bizType=APP` 下 `fieldCode=__ownerCode` 的配置
- **THEN** 系统拒绝删除，返回业务错误

### Requirement: 导入字段配置管理接口
系统 SHALL 提供导入字段配置的分页/列表查询（按 `bizType` 过滤，按 `showOrder` 升序）、
新增、更新、逻辑删除接口，行为与项目内其他主数据保持一致的状态语义。新增/更新时，若
`formFieldDefinitionId` 非空，SHALL 校验其指向一个存在且状态为启用、`bizType` 与当前
配置一致的表单字段定义。

#### Scenario: 关联不存在或已停用的表单字段定义被拒绝
- **WHEN** 客户端新增导入配置，`formFieldDefinitionId` 指向一个不存在或状态非启用的表单
  字段定义
- **THEN** 系统拒绝创建，返回业务错误

### Requirement: 表单管理页面的导入模板配置界面
系统 SHALL 在"表单管理"页面（`/system/form-fields`）新增"导入模板配置"tab，与既有的
按业务对象类型切换一致，展示当前 `bizType` 下的导入字段配置列表（按 `showOrder`
升序），必填列的表头名称 SHALL 以红色字体展示；新增/编辑弹窗中提供"关联字段"选择器
（列出当前 `bizType` 下状态为启用的表单字段定义），选中后自动带出默认表头名称与字段标识，
管理员可继续调整表头名称、是否主键、是否必填、显示序号。POSITION 分类下的两条固定标识
列 SHALL 在列表中标记为"系统保护"，不展示编辑必填/删除入口。

#### Scenario: 切换到任职分类查看固定标识列
- **WHEN** 用户在"导入模板配置"tab 切换到"任职"分类
- **THEN** 页面展示的列表中包含表头为"人员编号""组织编码"的两条记录，均标记为系统保护
  且必填列以红色字体展示

### Requirement: 按业务对象类型下载 Excel 导入模板
系统 SHALL 提供按 `bizType` 生成并下载 Excel 导入模板的接口，模板首行为表头，列顺序按
该 `bizType` 下启用的导入字段配置的 `showOrder` 升序排列，必填列对应的表头单元格 SHALL
以加粗、红色字体渲染。

#### Scenario: 下载组织导入模板
- **WHEN** 客户端请求 `bizType=ORG` 的导入模板下载接口
- **THEN** 系统返回一个 `.xlsx` 文件，首行表头按 ORG 当前启用的导入字段配置顺序排列，
  必填列表头文字为红色加粗

### Requirement: 按业务对象类型批量导入
系统 SHALL 提供按 `bizType` 上传 Excel 文件批量导入的接口：解析首行表头，按表头文字匹配
该 `bizType` 下启用的导入字段配置得到列到 `fieldCode` 的映射（表头文字不匹配任何配置的列
SHALL 被忽略）；对忽略必填表头缺失的整份文件（模板中必填表头被删除或改名）SHALL 拒绝整个
请求并提示缺失的必填表头名称；表头齐全时，对每一数据行独立处理：按已勾选 `isPrimaryKey`
的列组成的复合键查询是否已存在匹配记录，命中零条则按新增流程调用对应业务模块（组织/人员/
任职/应用）既有的创建校验与写入逻辑，命中一条则按更新流程调用既有的更新校验与写入逻辑，
命中多于一条判定该行失败；必填列为空值的行判定失败；单行处理异常（业务校验不通过等）
SHALL 不影响其余行的处理，最终返回本次导入的成功条数与失败明细（行号、原因）。

#### Scenario: 上传的模板缺少必填表头被整体拒绝
- **WHEN** 客户端上传的 Excel 文件表头中不包含某个 `bizType` 下配置为必填的导入列对应的
  表头文字
- **THEN** 系统拒绝整个导入请求，返回业务错误，提示缺失的必填表头名称，不处理文件中的任何
  数据行

#### Scenario: 主键匹配到已有记录时执行更新
- **WHEN** 导入 `bizType=ORG` 的一行数据，其主键列（组织编码）取值与某条未删除组织记录的
  `code` 相同
- **THEN** 系统对该行调用组织模块既有的更新逻辑，该组织记录被更新而非新增一条重复记录

#### Scenario: 主键未匹配到已有记录时执行新增
- **WHEN** 导入 `bizType=APP` 的一行数据，其主键列（应用编码）取值在当前未删除的应用记录
  中不存在
- **THEN** 系统对该行调用应用模块既有的创建逻辑，新增一条应用记录

#### Scenario: 必填列为空的行导入失败但不影响其他行
- **WHEN** 上传的 Excel 中某一数据行的某个必填列为空，其余行数据完整
- **THEN** 该行被计入失败明细（含行号与"必填列缺失"类原因），其余数据行正常按新增/更新
  流程处理并计入成功条数

#### Scenario: 复合主键命中多条已有记录判定为失败
- **WHEN** 导入 `bizType=POSITION` 的一行数据，其人员编号+组织编码+任职类型组合的复合键
  查询到一条以上未删除的任职记录
- **THEN** 该行被计入失败明细，原因说明匹配到多条已存在记录，系统不做隐式选择或更新

### Requirement: 任职导入的人员与组织标识映射
系统 SHALL 将 POSITION 导入行中"人员编号"列的取值按未删除范围内的 `tab_user.code` 匹配
得到 `userId`，"组织编码"列的取值按未删除范围内的 `tab_org.code` 匹配得到 `orgId`；
任一标识匹配不到有效记录时，该行 SHALL 判定为失败，失败原因 SHALL 分别指出是人员编号还是
组织编码无法匹配。

#### Scenario: 人员编号无法匹配到任何用户
- **WHEN** 导入 POSITION 一行数据，"人员编号"列取值在当前未删除的用户记录中不存在
- **THEN** 该行被计入失败明细，原因说明人员编号无法匹配到已有人员记录

### Requirement: 应用导入的负责人与组织标识映射
系统 SHALL 将 APP 导入行中"负责人编号"列的取值按未删除范围内的 `tab_user.code` 匹配得到
`ownerId`，"组织编码"列的取值按未删除范围内的 `tab_org.code` 匹配得到 `orgId`；任一标识
匹配不到有效记录时，该行 SHALL 判定为失败，失败原因 SHALL 分别指出是负责人编号还是组织
编码无法匹配；解析成功后仍按应用编码（`code`）作为主键匹配已有记录（零条新增、一条更新、
多条判定失败）。

#### Scenario: 负责人编号无法匹配到任何用户
- **WHEN** 导入 APP 一行数据，"负责人编号"列取值在当前未删除的用户记录中不存在
- **THEN** 该行被计入失败明细，原因说明负责人编号无法匹配到已有人员记录

### Requirement: 管理页面的导入模板下载与批量导入入口
系统 SHALL 在组织、人员、任职、应用四个管理页面的工具栏新增"下载导入模板"按钮（触发对应
`bizType` 的模板下载接口）与"批量导入"按钮（打开上传弹窗，选择 Excel 文件后调用对应
`bizType` 的批量导入接口，导入完成后在弹窗内展示成功条数与失败明细列表）。

#### Scenario: 组织管理页面批量导入后展示结果
- **WHEN** 用户在组织管理页面点击"批量导入"，上传一份包含 3 条有效数据与 1 条缺失必填列
  数据的 Excel 文件
- **THEN** 导入完成后弹窗展示"成功 3 条"，并列出 1 条失败明细（行号 + 原因）
