## Why

RBAC 权限管理系统目前对组织、用户、任职、应用、角色、权限、管理员、菜单、字典这
9 个业务模块的新增/编辑/启用/停用/删除操作没有留痕，出现数据被谁改过、什么时候
改的、改了哪些字段这类问题时无法追溯。"系统管理"分组下的"操作日志"菜单
（`/system/logs`，权限点 `system:log:view`）目前是占位页面（见 `权限资源.txt`
"尚未实现"小节），需要补上真实的查询与详情能力。

## What Changes

- 新增 `tab_operation_log` 表：记录每一次写操作所属模块、资源类型、操作类型
  （新增/编辑/启用/停用/删除）、被操作对象 id 与名称快照、操作人、操作 IP、
  操作终端类型、操作系统、操作浏览器、原始 User-Agent、操作发起时间、字段
  变更详情（JSON）；操作终端/系统/浏览器由后端从 `User-Agent` 请求头手写正则
  解析得出（不引入第三方 UA 解析依赖——评估过的 `eu.bitwalker:UserAgentUtils`
  因年久失修对现代浏览器版本号解析错误而放弃，详见 `design.md`）。
- 新增通用的 `OperationLogRecorder`（手动调用，不用 AOP/切面）：提供
  `recordCreate`/`recordUpdate`/`recordStatusChange`/`recordDelete` 方法，
  入参为业务模块传入的"变更前/变更后字段快照"（`Map<String, Object>`，key 为
  中文字段名），内部做逐字段 diff 后落库；创建只有"after"快照、删除只有
  "before"快照、编辑/启停用两者都有且只记录发生变化的字段。
- 在组织、用户、任职、应用、角色、权限、管理员、菜单、字典类型、字典项
  共 10 类资源（对应 9 个业务模块，字典管理下字典类型/字典项分别算一类资源）
  的创建/更新/启用/停用/删除方法中，各自手动构造前后字段快照并调用
  `OperationLogRecorder`——不引入切面，改动只发生在各模块 `ServiceImpl` 内部，
  不改变这些模块已有接口的请求/响应结构。
- 新增操作日志查询后端接口：分页查询（支持按模块、资源类型、操作类型、操作人、
  时间范围筛选）、详情（返回结构化的字段变更列表）。只读，没有新增/编辑/删除
  接口。
- 实现"操作日志管理"前端页面（路径 `/system/logs`，替换现有占位页），列表支持
  上述筛选条件 + 分页，详情弹窗以"字段名：旧值 → 新值"的形式展示变更。

## Capabilities

### New Capabilities
- `operation-log-management`：跨全部 9 个业务模块的操作日志记录（手动埋点，
  非切面）与查询，包含字段级变更详情的存储与展示。

### Modified Capabilities
（无——组织、用户、任职、应用、角色、权限、管理员、菜单、字典管理这些模块自身
对外的接口请求/响应结构不变，写操作内部新增的日志记录属于新能力
`operation-log-management` 的行为契约，不视为这些既有 capability 的需求变更。）

## Impact

- 数据库：新增 `tab_operation_log` 表（Flyway `V14__init_tab_operation_log.sql`）。
- 后端：新增 `cn.nihility.rbac.operationlog` 包（entity/mapper/mybatis xml/dto/
  mapstruct/service/controller/constant/util，含通用的 `OperationLogRecorder`
  与手写正则解析的 `UserAgentParser`，无新增第三方依赖）；
  `org`/`user`（含任职）/`app`/`role`/`permission`/`admin`/`menu`/`dict` 共 9 个
  模块的 `ServiceImpl` 在创建/更新/启用/停用/删除方法内新增对
  `OperationLogRecorder` 的调用。
- 前端：新增 `views/system/log/OperationLogManagementView.vue`、`api/operationLog.ts`、
  `types/operationLog.ts`；`router/index.ts` 里 `/system/logs` 从占位组件换成
  真实页面组件（`router/menu.ts` 无需改动，菜单项已存在）。
- 完成后需要同步更新仓库根目录 `权限资源.txt`：把"尚未实现"小节里的"操作日志"
  条目移除，补充 `OperationLogManagement:log:view`（模块名取自前端页面组件名
  `OperationLogManagementView.vue` 去掉 `View` 后缀，命名规则与既有
  `MenuManagement`/`DictManagement` 等模块名对齐）编码，并追加一份
  `tab_menu` 种子数据迁移（若该编码尚未写入 `tab_menu`）。
