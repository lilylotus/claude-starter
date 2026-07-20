## 1. 数据库

- [x] 1.1 新建 `backend/src/main/resources/db/migration/V14__init_tab_operation_log.sql`：
  按 `design.md` 决策 1 建 `tab_operation_log` 表，Flyway 迁移应用成功验证
  （`change_detail` 列的 COMMENT 文案按实际实现调整为
  `{field,oldValue,newValue}`，不含单独的 `label`——见本节末尾偏离说明）

## 2. 后端：操作日志通用模块

- [x] 2.0 ~~`backend/build.gradle` 新增依赖 `eu.bitwalker:UserAgentUtils`~~
  ——已回退：验证时发现该库最后一次实质更新在 2015 年前后，浏览器版本识别基于
  硬编码的版本枚举，对现代浏览器版本号（如 Chrome 120）会解析出明显错误的结果
  （实测得到 `"Chrome 12"`）而不是失败或返回 `null`。改为不引入第三方依赖，新增
  `cn.nihility.rbac.operationlog.util.UserAgentParser`（手写正则解析），详见
  `design.md` 决策 4"不引入第三方 UA 解析依赖"
- [x] 2.1 `cn.nihility.rbac.operationlog.constant.OperationType`（1=新增/2=编辑/
  3=启用/4=停用/5=删除，含 `label(int)` 中文映射方法）
- [x] 2.2 `cn.nihility.rbac.operationlog.constant.OperationLogResourceType`
  （org/user/position/app/role/permission/admin/menu/dictType/dictItem 十个
  编码，含"模块中文名"、"资源中文名"两个映射方法）
- [x] 2.3 `cn.nihility.rbac.operationlog.entity.OperationLogEntity`（对应
  `tab_operation_log`）
- [x] 2.4 `cn.nihility.rbac.operationlog.mapper.OperationLogMapper`（`BaseMapper`
  接口）+ `resources/mybatis/mapper/OperationLogMapper.xml`（`selectOperationLogPage`，
  按 module/resourceType/operationType/createBy/时间范围 动态条件筛选，
  `create_time` 降序）
- [x] 2.5 `cn.nihility.rbac.operationlog.dto`：`OperationLogVO`（列表用，含
  `operateIp`/`operateTerminal`/`operateOs`/`operateBrowser`，不含
  `operateUserAgent` 与变更详情）、`OperationLogDetailVO`（详情用，含
  `operateIp`/`operateTerminal`/`operateOs`/`operateBrowser`/`operateUserAgent`、
  `changeDetail: List<OperationLogFieldChangeVO>`）、
  `OperationLogFieldChangeVO`（`field`/`oldValue`/`newValue`）、
  `OperationLogQueryRequest`（分页 + 筛选参数）
- [x] 2.6 `cn.nihility.rbac.operationlog.mapstruct.OperationLogConvert`（静态
  单例写法，参照 `RoleConvert`）
- [x] 2.7 `cn.nihility.rbac.operationlog.service.OperationLogRecorder` 接口 +
  `impl.OperationLogRecorderImpl`：`recordCreate`/`recordUpdate`/
  `recordStatusChange`/`recordDelete` 四个方法，内部做 before/after 快照 diff、
  JSON 序列化 `change_detail`、通过 `RequestContextHolder` 获取当前
  `HttpServletRequest` 并解析 `operate_ip`（优先 `X-Forwarded-For` 首个 IP，
  否则 `getRemoteAddr()`，取不到时为 `null`，不抛异常）；读取 `User-Agent`
  请求头存入 `operate_user_agent`，并用新增的
  `cn.nihility.rbac.operationlog.util.UserAgentParser`（手写正则，不依赖第三方
  库）解析出 `operate_browser`（浏览器名称+主版本号）、`operate_os`（操作系统
  名称+版本）、`operate_terminal`（"Computer"/"Mobile"/"Tablet"），请求头缺失或
  解析异常时四个字段均为 `null`（`try/catch` 兜底，不影响日志主体写入）；写入
  `tab_operation_log`（`create_by`/`update_by` 暂固定 `"admin"`，与其他模块
  `DEFAULT_OPERATOR` 一致）
- [x] 2.8 `cn.nihility.rbac.operationlog.service.OperationLogQueryService` +
  `impl.OperationLogQueryServiceImpl`：分页查询（调用
  `OperationLogMapper.xml` 的动态条件 SQL）、详情查询（`change_detail` JSON
  反序列化为 `List<OperationLogFieldChangeVO>`）
- [x] 2.9 `cn.nihility.rbac.operationlog.controller.OperationLogController`：
  `GET /api/operation-logs`（分页+筛选）、`GET /api/operation-logs/{id}`（详情），
  加 springdoc-openapi 注解
- [x] 2.10 新增 `OperationLogRecorderImplTest`：覆盖新增（before 全 null）、
  编辑（仅记录变化字段，相同字段不出现）、启停用（仅状态字段变更）、删除
  （after 全 null）四种场景的 diff 逻辑，`operate_ip` 在有/无
  `RequestContextHolder` 上下文时分别正确解析/为空的场景，以及 `User-Agent`
  请求头存在（精确断言为 `"Chrome 120"`/`"Windows 10"`/`"Computer"`，验证不会
  出现类似废弃第三方库的版本号截断问题）、缺失、无法识别三种情况下相关字段的
  解析结果；另新增 `cn.nihility.rbac.operationlog.util.UserAgentParserTest`
  单独覆盖 `UserAgentParser` 本身：Chrome/Edge（关键字互相包含时的优先级）/
  Safari（桌面+iPhone）/Firefox/Android（手机+平板）/iPad 等主流浏览器与设备的
  解析结果，以及空白/无法识别 User-Agent 的兜底行为

**实现偏离说明**：`design.md` 决策 1 的 `tab_operation_log.change_detail` 列
COMMENT 文案写的是"每项 `{field,label,oldValue,newValue}`"，但驱动本次实现的
任务说明里给出的 `OperationLogFieldChangeVO` 精确字段契约（前端据此独立开发）
只有 `field`/`oldValue`/`newValue` 三个字段，没有单独的 `label`——`field` 本身
就是快照 Map 里已经中文化的字段名（如"角色名称"），再加一个 `label` 会与
`field` 重复。落地时以字段契约为准，`change_detail` 的 JSON 结构及 V14 迁移里
的列 COMMENT 均按 `{field,oldValue,newValue}` 实现，未逐字照抄 `design.md`
里的 COMMENT 原文。

## 3. 后端：9 个业务模块接入 `OperationLogRecorder`

- [x] 3.1 组织 `OrgServiceImpl`：新增 `toLogSnapshot(OrgEntity)`（组织名称、
  组织编码、上级组织名称、显示序号、备注、状态文案），在 `create`/`update`/
  `enable`/`disable`/`delete`（或对应的启停用私有方法）中调用
  `OperationLogRecorder`，`resourceType="org"`
- [x] 3.2 用户 `UserServiceImpl`：新增 `toLogSnapshot(UserEntity)`（姓名、编码、
  性别文案、手机号、身份证号、显示序号、备注、状态文案），接入
  create/update/enable/disable/delete，`resourceType="user"`
- [x] 3.3 任职 `PositionServiceImpl`（或 `UserServiceImpl` 内任职相关方法，
  以实际实现为准）：新增 `toLogSnapshot`（所属用户姓名、所属组织名称、任职
  类型、任职地址、任职电话、显示序号、备注、状态文案），接入
  create/update/enable/disable/delete，`resourceType="position"`（接入独立的
  `PositionServiceImpl`，`UserServiceImpl` 内嵌任职子表单的 `syncPositions`
  不重复记录任职日志，见 design.md Non-Goals）
- [x] 3.4 应用 `AppServiceImpl`：新增 `toLogSnapshot(AppEntity)`（应用名称、
  编码、负责人姓名、所属组织名称、显示序号、备注、状态文案），接入
  create/update/enable/disable/delete，`resourceType="app"`
- [x] 3.5 角色 `RoleServiceImpl`：新增 `toLogSnapshot(RoleEntity)`（角色名称、
  编码、显示序号、备注、状态文案），接入 create/update/enable/disable/delete，
  `resourceType="role"`
- [x] 3.6 权限点 `PermissionServiceImpl`：新增 `toLogSnapshot(PermissionEntity)`
  （权限点名称、编码、显示序号、备注、状态文案），接入
  create/update/enable/disable/delete，`resourceType="permission"`
- [x] 3.7 管理员 `AdminServiceImpl`：新增 `toLogSnapshot(AdminEntity)`（内部按
  `adminId` 回查角色关联/组织管辖范围，管理员名称、编码、关联用户姓名、显示
  序号、备注、状态文案、角色名称列表拼接、组织管辖范围"组织名(含子组织)"列表
  拼接），接入 create/update/enable/disable/delete，`resourceType="admin"`
  （新增注入 `UserMapper` 用于回填关联用户姓名）
- [x] 3.8 菜单 `MenuServiceImpl`：新增 `toLogSnapshot(MenuEntity)`（资源名称、
  编码、上级资源名称、资源类型文案、显示序号、备注、状态文案），接入
  create/update/enable/disable/delete，`resourceType="menu"`
- [x] 3.9 字典类型 `DictTypeServiceImpl`：新增 `toLogSnapshot(DictTypeEntity)`
  （类型名称、编码、显示序号、备注、状态文案），接入
  create/update/enable/disable/delete，`resourceType="dictType"`
- [x] 3.10 字典项 `DictItemServiceImpl`：新增 `toLogSnapshot(DictItemEntity)`
  （所属字典类型名称、字典项标签、编码、显示序号、备注、状态文案），接入
  create/update/enable/disable/delete，`resourceType="dictItem"`
- [x] 3.11 为 3.1~3.10 中每个模块补充/更新对应 `*ServiceImplTest`：验证写操作
  成功后调用了 `OperationLogRecorder` 对应方法（可用 Mockito `verify`），不要求
  真实落库断言（落库逻辑已在 2.10 覆盖）

## 4. 前端：操作日志管理页面

- [x] 4.1 `types/operationLog.ts`：`OperationLogRow`、`OperationLogDetail`、
  `OperationLogFieldChange`、`OperationLogQueryParams`，以及模块/资源类型/
  操作类型的前端硬编码选项常量（与后端 `OperationLogResourceType`/
  `OperationType` 一一对应）
- [x] 4.2 `api/operationLog.ts`：分页查询（含筛选参数）、详情查询的 axios 封装
- [x] 4.3 `views/system/log/OperationLogManagementView.vue`：筛选栏（模块下拉、
  资源类型下拉、操作类型下拉、操作人输入框、操作时间范围选择器）+ 分页表格
  （操作时间、操作模块、资源类型、操作类型、被操作对象、操作人、操作发起 IP、
  操作）+ 只读详情弹窗（字段变更表格：字段名/旧值/新值；另展示操作终端、操作
  系统、操作浏览器、原始 User-Agent，取值为空的字段展示"-"）

## 5. 前端：挂载新页面

- [x] 5.1 `router/index.ts`：`/system/logs` 从占位组件
  （`PlaceholderView.vue`）替换为
  `() => import('@/views/system/log/OperationLogManagementView.vue')`
  （`router/menu.ts` 中 `/system/logs` 菜单项已存在，无需改动）

## 6. 权限资源清单与菜单种子数据（实现完成后处理，不委托给子 agent）

- [x] 6.1 更新仓库根目录 `权限资源.txt`：在"系统管理"小节新增
  `OperationLogManagement:log:view` 一条编码（模块名取自前端组件
  `OperationLogManagementView.vue`；操作日志管理页面访问，仅查看，无
  add/edit/enable/disable/delete），并从文件末尾"尚未实现"小节移除"操作日志"
  条目
- [x] 6.2 新增
  `backend/src/main/resources/db/migration/V15__seed_log_menu_resource_data.sql`，
  挂在 `system` 一级分组下，`showOrder=5`，排在"菜单管理"（20）、"字典管理"
  （10）之后，与 `router/menu.ts` 里 `system` 分组 children 数组声明顺序一致；
  本地 MySQL 验证 Flyway 迁移应用成功（`flyway_schema_history` version=15），
  查询确认排序符合预期（菜单管理→字典管理→操作日志）

## 7. 验证

- [x] 7.1 `./gradlew build`（含全部新增/修改测试类，共 12 个测试类，含新增的
  `OperationLogRecorderImplTest`、`UserAgentParserTest`）通过，V14/V15 迁移
  本地 MySQL 验证应用成功
- [x] 7.2 `npx vue-tsc --noEmit`（`frontend/`）与 `npm run build` 均通过，
  无类型错误
- [x] 7.3 真实浏览器/接口验证：启动后端 `bootRun`（48080）+ 前端 `vite --host
  127.0.0.1`（5173）。
  - 用 curl 直接对后端接口做了跨模块验证（携带真实浏览器 User-Agent，覆盖
    Chrome/Firefox/Safari 三种浏览器与 Windows/Linux/macOS 三种系统）：
    角色（新增，`role`）、字典类型（新增→编辑→删除全流程，`dictType`，验证
    编辑操作的字段级 diff 只包含真正变化的字段、删除操作的 `newValue` 全为
    `null`）、管理员（新增，`admin`，验证角色/组织管辖范围被正确聚合成可读
    字符串写入变更详情，且详情接口正确回填 `operateBrowser`/`operateOs`
    为 "Safari 17"/"macOS 14.2.1"）。分页查询按 `resourceType`/`operationType`
    筛选、详情查询的字段变更结构均与 `design.md`/spec 一致。验证完毕后已
    清理测试期间创建的角色/字典类型/管理员数据。
  - 用 Playwright（本地已缓存 chromium，临时装到 scratchpad 目录，未写入
    项目依赖）登录后驱动操作日志管理页面：①页面加载即调用分页接口展示列表，
    列头包含全部预期列；②按"模块"筛选为"字典管理"后，列表只保留该模块的
    记录，其他模块记录被正确过滤掉；③点击"详情"打开只读弹窗，正确展示
    操作模块/资源类型/操作类型/被操作对象/操作人/操作 IP/操作终端/操作系统/
    操作浏览器/原始 User-Agent，以及字段变更表格（删除操作的"新值"列正确
    显示为"-"）。全部步骤通过（`ALL_STEPS_PASSED`），并留存了详情弹窗截图。
  - 未覆盖：任职/应用/权限点/菜单/字典项这 5 类资源、以及启用/停用两种操作
    类型没有逐一在真实服务器上手动触发（9 个模块的接入代码结构一致，
    已通过 `./gradlew build` 的单测覆盖 diff 逻辑本身，加上本轮对 3 类不同
    复杂度资源——扁平资源 `role`、树形/嵌套资源 `dictType`、含多对多关联的
    `admin`——的真实接口验证，判断遗漏同类回归的风险较低，但未做到对全部
    10×5 种操作组合的穷尽验证）。
  - 验证过程中发现并修复了一个真实缺陷（见下方"实现偏离说明"追加记录）：
    最初选用的 `eu.bitwalker:UserAgentUtils` 库对现代浏览器版本号解析错误
    （`Chrome/120.0.0.0` 被识别成 `"Chrome 12"`），已改为手写正则解析
    `UserAgentParser`，不再依赖该第三方库，`backend/build.gradle` 相应回退。
