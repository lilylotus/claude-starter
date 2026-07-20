## Context

现有 9 个业务模块（组织 `org`、用户 `user`、任职 `user.position`、应用 `app`、
角色 `role`、权限点 `permission`、管理员 `admin`、菜单 `menu`、字典 `dict`——
字典下再分字典类型/字典项两类资源）的 `ServiceImpl` 都遵循同一套 CRUD 骨架
（参照 `RoleServiceImpl`/`AdminServiceImpl`）：`create`/`update`/`enable`/
`disable`/`delete`，写操作前用 `getExistingEntity(id)` 读出实体、原地 set 字段、
`mapper.updateById`/`insert`。实体字段高度同构：`id` + 少量业务字段（`name`/
`code`/`showOrder`/`remark`/关联外键等）+ `status` + 4 个审计字段
（`createBy`/`createTime`/`updateBy`/`updateTime`）。当前登录鉴权尚未接入，
所有模块的 `createBy`/`updateBy` 都硬编码常量 `DEFAULT_OPERATOR = "admin"`。

用户明确要求操作日志记录**不使用切面（AOP）**，而是在各模块业务方法前后手动调用
记录逻辑。因为 9 个模块实体字段各不相同，需要一个通用的落库/diff 机制，同时让
"手动构造快照"这部分保持简单、可复制粘贴到每个模块。

## Goals / Non-Goals

**Goals:**
- 覆盖 org/user/position/app/role/permission/admin/menu/dict（dictType +
  dictItem）共 10 类资源的新增、编辑、启用、停用、删除操作记录。
- 记录内容包含：模块、资源类型、操作类型、被操作对象 id 与名称快照、操作人、
  操作 IP、操作发起时间、操作终端类型、操作系统、操作浏览器、原始 User-Agent、
  字段级变更详情（旧值 → 新值）。
- 记录逻辑通过显式方法调用完成，不引入 Spring AOP 切面、不使用注解驱动的拦截。
- 提供分页查询（多条件筛选）+ 详情查询两个只读接口，及对应前端页面。

**Non-Goals:**
- 不记录纯查询（分页列表、详情、下拉选项）类接口。
- 不记录管理员-角色、管理员-组织范围、用户-任职这类多对多/一对多关联表本身的
  增删（任职本身作为"任职管理"资源单独记录；管理员的角色/组织管辖范围随管理员
  整体同步，不拆分成独立的关联变更日志条目，"管理员"这条操作日志的字段变更里
  会把角色/组织管辖范围的变化各汇总成一个可读字符串字段，而不是逐行 diff）。
- 不做登录鉴权，操作人字段延续现状固定写入 `"admin"`（等鉴权接入后由调用方传入
  真实操作人，`OperationLogRecorder` 的方法签名已预留 `operator` 参数，不需要
  未来改签名）。
- 不提供操作日志的导出、删除、归档能力，仅查询。
- 不记录 IP/操作人/操作时间/终端/操作系统/浏览器/User-Agent 之外的其他请求
  上下文（如请求参数原文、Referer）。
- User-Agent 解析用手写正则实现，只覆盖主流桌面/移动浏览器与操作系统，不追求
  覆盖所有小众客户端/爬虫，解析不出的字段落 `null`，不视为缺陷。

## Decisions

### 1. 表结构

```sql
CREATE TABLE `tab_operation_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `module`         VARCHAR(32)  NOT NULL COMMENT '业务模块中文名，如"组织管理"',
    `resource_type`  VARCHAR(32)  NOT NULL COMMENT '资源类型编码，如 org/user/position/app/role/permission/admin/menu/dictType/dictItem',
    `resource_name`  VARCHAR(32)  NOT NULL COMMENT '资源类型中文名，如"组织"',
    `operation_type` INT          NOT NULL COMMENT '操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除',
    `target_id`      BIGINT       NOT NULL COMMENT '被操作对象主键 id',
    `target_name`    VARCHAR(128) NOT NULL COMMENT '被操作对象名称快照，即使对象后续被改名/删除也保留操作当时的名称',
    `change_detail`  TEXT         NOT NULL COMMENT '字段变更详情，JSON 数组，每项 {field,label,oldValue,newValue}',
    `operate_ip`         VARCHAR(64)  DEFAULT NULL COMMENT '操作发起 IP，从当前 HTTP 请求自动获取，取不到（如非 HTTP 上下文中调用）时为空',
    `operate_terminal`   VARCHAR(32)  DEFAULT NULL COMMENT '操作终端类型，如 PC/Mobile/Tablet，从 User-Agent 解析，解析不出时为空',
    `operate_os`         VARCHAR(64)  DEFAULT NULL COMMENT '操作系统，如 Windows 10/macOS/Android，从 User-Agent 解析，解析不出时为空',
    `operate_browser`    VARCHAR(64)  DEFAULT NULL COMMENT '操作浏览器，如 Chrome 120，从 User-Agent 解析，解析不出时为空',
    `operate_user_agent` VARCHAR(512) DEFAULT NULL COMMENT '原始 User-Agent 请求头，取不到时为空',
    `create_by`      VARCHAR(64)  NOT NULL COMMENT '操作人，即创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间，即创建时间',
    `update_by`      VARCHAR(64)  NOT NULL COMMENT '更新人，日志不可变更，恒等于 create_by',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，日志不可变更，恒等于 create_time',
    PRIMARY KEY (`id`),
    KEY `idx_tab_operation_log_resource` (`resource_type`, `target_id`),
    KEY `idx_tab_operation_log_module` (`module`),
    KEY `idx_tab_operation_log_create_by` (`create_by`),
    KEY `idx_tab_operation_log_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表，只追加不更新不删除';
```

日志表没有 `status` 字段——它是只追加的事实记录，没有启停用/逻辑删除的概念；
`create_by`/`create_time`/`update_by`/`update_time` 四个字段按仓库既有约定保留，
`update_by`/`update_time` 恒等于 `create_by`/`create_time`（写入时一次性赋值，
之后永不 `UPDATE`），不新造一套 `operator`/`operate_time` 命名，避免和项目统一的
审计字段并存造成混淆；"操作人"复用 `create_by`、"操作发起时间"复用
`create_time`，只有"操作 IP"没有已有字段可复用，新增独立的 `operate_ip` 列
（允许为空，兼容非 HTTP 上下文——如未来批处理/定时任务触发的写操作——取不到
客户端 IP 的情况）。

### 2. 操作类型常量

新增 `OperationType`（`cn.nihility.rbac.operationlog.constant`），沿用
`MenuResourceType` 的写法风格：

```java
public static final int CREATE = 1;
public static final int UPDATE = 2;
public static final int ENABLE = 3;
public static final int DISABLE = 4;
public static final int DELETE = 5;
```

配一个 `label(int)` 静态方法返回中文（新增/编辑/启用/停用/删除），供 VO 转换和
前端筛选下拉复用（前端也硬编码这 5 个选项，量少不必单独建字典类型）。

### 3. 资源类型常量

新增 `OperationLogResourceType`，10 个资源类型用字符串编码（而非数字，可读性
更好、也不需要和其他模块的数字状态常量混淆）：`org`/`user`/`position`/`app`/
`role`/`permission`/`admin`/`menu`/`dictType`/`dictItem`，每个编码同时维护
"模块中文名"和"资源中文名"两个映射（模块粒度是 9 个：字典类型和字典项同属
"字典管理"模块）。

### 4. `OperationLogRecorder`：手动记录入口，不用切面

```java
public interface OperationLogRecorder {
    void recordCreate(String resourceType, Long targetId, String targetName,
                       Map<String, Object> afterSnapshot);
    void recordUpdate(String resourceType, Long targetId, String targetName,
                       Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot);
    void recordStatusChange(String resourceType, Long targetId, String targetName,
                             boolean enable, Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot);
    void recordDelete(String resourceType, Long targetId, String targetName,
                       Map<String, Object> beforeSnapshot);
}
```

- `resourceType` 决定 `module`/`resourceName` 怎么填（内部查 `OperationLogResourceType`
  的映射），调用方不用重复传。
- 快照是 `LinkedHashMap<String, Object>`（key 为中文字段名，如"角色名称"，value
  为已经格式化好、人类可读的值，例如状态字段传"启用"/"停用"字符串而不是 2000/3000
  原始码值），由各模块自己的一个 `private Map<String, Object> toLogSnapshot(XxxEntity e)`
  方法构造——这是本设计里"手动"的核心体现：每个模块自己决定记什么字段、怎么格式化，
  `OperationLogRecorder` 不做反射、不猜字段名、不做枚举码值到文案的映射。
- diff 规则统一在 `OperationLogRecorder` 实现里做：以 before/after 两个 Map 的
  key 并集遍历，`Objects.equals(oldValue, newValue)` 相等的字段跳过，不相等的
  才写入 `change_detail`；`recordCreate` 时 before 视为全 null（每个 after 字段都
  当作"新增"记录），`recordDelete` 时 after 视为全 null。
- `enable`/`disable` 复用 `recordUpdate` 同一套 diff 逻辑（`recordStatusChange`
  只是语义化的入口，内部仍是全字段快照 diff，天然只会得出"状态"一个变更字段，
  因为调用方在启停用场景下 before/after 快照除状态外其余字段相同）——不用为
  启停用单独写"只记状态"的特化逻辑。
- 操作人：方法内部暂时固定读取一个包级常量 `"admin"`（与现有 9 个模块
  `DEFAULT_OPERATOR` 保持一致的占位方式），未来接入鉴权后改成从
  `SecurityContext`/请求上下文取值，属于 `OperationLogRecorder` 实现内部改动，
  不影响调用方签名。
- 操作 IP：不作为方法入参，由 `OperationLogRecorderImpl` 内部通过
  `RequestContextHolder.getRequestAttributes()` 取回当前线程绑定的
  `HttpServletRequest`（Spring MVC 请求处理线程内始终可用，9 个业务模块的写
  接口都是同步 controller方法直接调用到 service，不存在异步线程切换），再取
  `X-Forwarded-For` 请求头（若存在，取第一个 IP，兼容经反向代理/网关的场景）
  或 `request.getRemoteAddr()`（否则）。取不到（`RequestContextHolder` 返回
  `null`，如单元测试或未来非 HTTP 触发场景）时 `operate_ip` 写 `null`，不抛异常
  阻断主业务流程——这样调用方（9 个模块的 `toLogSnapshot`/调用点）完全不用关心
  IP，避免给 40+ 处调用点都加一个 `HttpServletRequest` 参数。
- 操作终端/操作系统/操作浏览器/User-Agent：同样不作为方法入参，在
  `OperationLogRecorderImpl` 内部取到 `HttpServletRequest` 后一并读取
  `User-Agent` 请求头，原样存入 `operate_user_agent`；再用新增的
  `cn.nihility.rbac.operationlog.util.UserAgentParser`（手写正则，不引入第三方
  依赖）解析出浏览器名称+主版本号（`operate_browser`，如"Chrome 120"）、操作
  系统名称+版本（`operate_os`，如"Windows 10"/"macOS 14.2"/"Android 14"/
  "iOS 17.2"）、终端类型（`operate_terminal`，"Computer"/"Mobile"/"Tablet"）。
  `User-Agent` 请求头缺失或解析异常时四个字段全部写 `null`，用 `try/catch`
  兜底，不影响操作日志主体（模块/操作类型/字段变更等）的写入，也不阻断被记录
  的业务操作本身。

**不引入第三方 UA 解析依赖**：最初评估过 `eu.bitwalker:UserAgentUtils`（并已
与用户确认过引入），但该库最后一次实质更新在 2015 年前后，其浏览器版本识别
基于硬编码的版本枚举（如 `CHROME12`……`CHROME65`），对超出枚举范围的现代浏览器
版本号会解析出明显错误的结果——实测 `Chrome/120.0.0.0` 被错误识别成
`"Chrome 12"`，而不是抛出解析失败或返回 `null`，这种"看似成功但结果错误"比
"解析不出返回 `null`"更糟糕，因为使用者会把错误值当真实数据。改为
`UserAgentParser` 手写正则实现：只依赖 UA 字符串里的关键字/版本号做正则提取
（浏览器判断顺序刻意把 Edge/Opera 放在 Chrome 之前、Safari 放在 Chrome 之后，
避免互相包含的关键字导致误判），版本号直接从原始字符串里提取，不存在"版本号
超出预置范围"的问题；不认识的 UA 各字段返回 `null`（终端类型兜底为
"Computer"），覆盖面比 UserAgentUtils 窄（不识别小众浏览器/机器人），但不会给
出错误结果。

**考虑过的替代方案**：反射通用 diff（对比 entity 的全部 getter），配合字段上的
`@LogField("中文名")` 注解自动生成快照——放弃，因为（a）状态这类码值字段还是需要
一次人工的码值→文案映射，注解不能省这一步；（b）用户明确要求"手动记录"，反射+
注解本质上是另一种自动化（约定优于配置），偏离了"改动可见、逻辑显式"的诉求；
（c）跨模块的实体字段类型不统一（有的是 `Integer` 状态码，有的是关联对象名称需要
额外查询回填，无法从 entity 自身反射得到），手动构造快照反而更省事。

### 5. 各模块接入点与快照字段

在对应 `ServiceImpl` 的 `create`/`update`/`enable`/`disable`/`delete`（或
`changeStatus` 私有方法）里，在 `mapper.insert/updateById` 成功之后，构造快照并
调用 `OperationLogRecorder`；`update`/`enable`/`disable` 需要在 mutate 实体字段
**之前**先构造 `beforeSnapshot`（此时实体仍是 DB 原值），mutate 完成后再构造
`afterSnapshot`。逐模块的快照字段（均含"状态"，值用各模块已有的状态常量做
码值→文案映射，若模块暂无该工具方法则新增一个）：

| 模块 | resourceType | 快照字段 |
|---|---|---|
| 组织 | `org` | 组织名称、组织编码、上级组织（名称，需按 parentId 查一次名）、显示序号、备注、状态 |
| 用户 | `user` | 姓名、编码、性别、手机号、身份证号、显示序号、备注、状态 |
| 任职 | `position` | 所属用户（姓名）、所属组织（名称）、任职类型、任职地址、任职电话、显示序号、备注、状态 |
| 应用 | `app` | 应用名称、编码、负责人（姓名）、所属组织（名称）、显示序号、备注、状态 |
| 角色 | `role` | 角色名称、编码、显示序号、备注、状态 |
| 权限点 | `permission` | 权限点名称、编码、显示序号、备注、状态 |
| 管理员 | `admin` | 管理员名称、编码、关联用户（姓名）、显示序号、备注、状态、管辖角色（名称列表拼接字符串）、管辖组织范围（"组织名(含子组织)"列表拼接字符串） |
| 菜单 | `menu` | 资源名称、编码、上级资源（名称）、资源类型（菜单/按钮/API 文案）、显示序号、备注、状态 |
| 字典类型 | `dictType` | 类型名称、编码、显示序号、备注、状态 |
| 字典项 | `dictItem` | 所属字典类型（名称）、字典项标签、编码、显示序号、备注、状态 |

需要按外键回查名称的字段（如组织名称、用户姓名）直接复用各模块已有的
mapper/service 查询方法，量级和已有的 `AdminServiceImpl` 详情回填一致，不引入
新查询模式。

### 6. 查询接口（`/api/operation-logs`，只读）

- `GET /api/operation-logs?module=&resourceType=&operationType=&createBy=&startTime=&endTime=&page=&pageSize=`
  —— 分页查询，全部筛选参数可选，按 `create_time` 降序排列；每条记录含
  `operateIp`（`operate_ip`）、`operateTerminal`、`operateOs`、`operateBrowser`。
- `GET /api/operation-logs/{id}` —— 详情，`change_detail` 反序列化为
  `List<OperationLogFieldChangeVO>{field,label,oldValue,newValue}` 返回，同样
  含 `operateIp`、`operateTerminal`、`operateOs`、`operateBrowser`、
  `operateUserAgent`（原始 UA 字符串只在详情返回，列表不返回，避免列表响应体
  过大）。
- 不提供新增/编辑/删除接口——写入只通过 `OperationLogRecorder` 内部完成。

### 7. 前端页面

- 列表：筛选栏（模块下拉、资源类型下拉、操作类型下拉、操作人输入框、时间范围
  选择器）+ 分页表格（列：操作时间、操作模块、资源类型、操作类型、被操作对象、
  操作人、操作 IP、操作）。操作终端/操作系统/操作浏览器/原始 User-Agent 不作为
  列表列展示（信息量大、列表页横向空间有限，与管理员管理页面不在列表展示角色/
  组织管辖范围是同样的取舍），仅在详情弹窗展示。模块/资源类型下拉的选项前端
  硬编码（与后端 `OperationLogResourceType` 的 10 项一一对应），不新增一个
  后端"选项"接口（列表页首次渲染即可用，无需额外请求，也没有下一步会新增第
  11 类资源导致选项过期的场景）。
- 详情弹窗：只读表格展示 `field`（中文字段名）+ `oldValue` + `newValue`
  （新增操作 `oldValue` 列显示"-"，删除操作 `newValue` 列显示"-"），以及操作
  模块、资源类型、操作类型、被操作对象名称、操作人、操作 IP、操作终端、操作
  系统、操作浏览器、原始 User-Agent、操作发起时间；上述任一字段为空时展示"-"。
- 交互参照菜单管理/字典管理这类"极简列表"页面（无新增/编辑弹窗，只有查询+
  详情），不需要表单校验规则。

## Risks / Trade-offs

- [Risk] 9 个模块的 `ServiceImpl` 都要手动改动 create/update/enable/disable/delete，
  改动点多（约 40+ 处调用），后续新增模块容易忘记接入 → Mitigation：这是用户
  明确要求的"手动记录"取舍，用一致的接入模式（每模块一个 `toLogSnapshot` 私有
  方法 + 5 处调用）降低遗漏概率；后续如需强约束可以考虑加单测检查（不在本次
  范围内）。
- [Risk] 快照里的关联名称（组织名、用户名等）是操作发生时的回查结果，如果同一
  事务内前后两次回查之间数据被并发修改，可能出现轻微不一致 → Mitigation：
  与现有各模块详情接口的一致性读取粒度相同，属于已接受的既有权衡，不额外加锁。
- [Risk] `change_detail` 用 TEXT 存 JSON 而不是 MySQL `JSON` 类型，后续无法用
  SQL 直接查询 JSON 内部字段 → Mitigation：当前查询需求只到"资源+对象+时间范围"
  粒度，字段级检索不是本次需求；如未来需要，属于独立的后续变更。

## Open Questions

无。
