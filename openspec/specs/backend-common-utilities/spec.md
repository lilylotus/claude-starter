# backend-common-utilities Specification

## Purpose
后端跨模块复用的公共基础设施能力，与 CLAUDE.md 里 `common/` 目录下已有的 `Result`、`GlobalResponseAdvice`、`BusinessException`、`GlobalExceptionHandler` 同属一类定位：不属于任何具体业务模块，供各业务模块直接复用，避免重复实现。目前包含统一的 JSON 序列化/反序列化/类型转换工具类 `JacksonUtils`、统一解析当前登录操作人账号编码的 `CurrentOperatorService`、统一发起对外 HTTP 请求的 `HttpClientUtils`、统一的全局固定大小线程池工具类 `ThreadPoolUtils`、统一的 Redis 操作工具类 `RedisUtils`/`RedisObjectUtils`，以及可复用的转换脚本执行工具类 `ScriptTransformExecutor`。

## Requirements
### Requirement: 统一的 JSON 序列化/反序列化工具类
系统 SHALL 提供一个位于 `cn.nihility.rbac.common.util` 包下的静态工具类 `JacksonUtils`，封装 JSON 序列化、反序列化与对象间类型转换能力，供后端各模块复用，避免各处重复注入 `ObjectMapper` 并各自处理初始化配置与异常。`JacksonUtils` 内部维护的 `ObjectMapper` 实例 SHALL 是独立于 Spring 自动装配、用于 HTTP 响应实际序列化的 `ObjectMapper` Bean 的另一份实例，两者互不影响；调用 `JacksonUtils` 不 SHALL 改变任何 `@RestController` 接口对外返回的 JSON 输出格式。

`JacksonUtils` 内部的 `ObjectMapper` SHALL 应用以下初始化配置：
- 序列化时排除值为 `null` 的字段。
- 反序列化时，若 JSON 内容包含目标类未定义的字段，SHALL 忽略该字段而不是抛出异常。
- `LocalDateTime`、`LocalDate`、`LocalTime`、`java.util.Date` 这四种日期类型 SHALL 使用统一的自定义格式序列化与反序列化（分别为 `yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、`HH:mm:ss`、`yyyy-MM-dd HH:mm:ss`），且不以时间戳形式序列化日期。

`JacksonUtils` SHALL 提供以下静态方法：
- `toJson(Object obj)`：把任意对象序列化为 JSON 字符串。
- `toObj(...)`：把 JSON 内容反序列化为对象，来源支持 `String`、`byte[]`、`InputStream` 三种，目标类型描述支持 `Class<T>`、`com.fasterxml.jackson.core.type.TypeReference<T>`、`java.lang.reflect.Type` 三种，共 9 个重载。
- `convert(...)`：把一个已有对象转换为另一种类型的对象，目标类型描述支持 `Class<T>`、`TypeReference<T>`、`com.fasterxml.jackson.databind.JavaType` 三种，共 3 个重载。

`toJson`/`toObj`/`convert` 在序列化或反序列化失败时 SHALL 抛出运行时异常（不吞异常、不静默返回 `null` 或空值），由调用方自行决定是否捕获并降级处理。

#### Scenario: 序列化对象时排除 null 字段
- **WHEN** 调用 `JacksonUtils.toJson(obj)`，其中 `obj` 的某个字段值为 `null`
- **THEN** 返回的 JSON 字符串中不包含该字段

#### Scenario: 反序列化时忽略目标类不存在的字段
- **WHEN** 调用 `JacksonUtils.toObj(json, SomeClass.class)`，其中 `json` 包含 `SomeClass` 未定义的字段
- **THEN** 反序列化正常完成，多余字段被忽略，不抛出异常

#### Scenario: 反序列化 String 为指定 Class
- **WHEN** 调用 `JacksonUtils.toObj(jsonString, SomeClass.class)`
- **THEN** 返回一个 `SomeClass` 实例，字段值与 `jsonString` 内容对应

#### Scenario: 反序列化 String 为带泛型的集合类型
- **WHEN** 调用 `JacksonUtils.toObj(jsonString, new TypeReference<List<SomeClass>>() {})`
- **THEN** 返回一个 `List<SomeClass>`，元素与 `jsonString` 中的 JSON 数组内容对应

#### Scenario: 反序列化 byte[] 与 InputStream
- **WHEN** 调用 `JacksonUtils.toObj(bytes, SomeClass.class)` 或 `JacksonUtils.toObj(inputStream, SomeClass.class)`，其中 `bytes`/`inputStream` 内容为合法 JSON
- **THEN** 均返回一个内容对应的 `SomeClass` 实例，行为与传入等价 JSON 字符串一致

#### Scenario: 对象间类型转换
- **WHEN** 调用 `JacksonUtils.convert(sourceObj, TargetClass.class)`，其中 `sourceObj` 的字段与 `TargetClass` 字段可对应
- **THEN** 返回一个 `TargetClass` 实例，字段值来自 `sourceObj`（等价于先 `toJson` 再 `toObj`，但不经过 JSON 字符串中转）

#### Scenario: 日期字段按统一格式序列化
- **WHEN** 调用 `JacksonUtils.toJson(obj)`，其中 `obj` 包含一个 `LocalDateTime` 字段
- **THEN** 该字段在输出 JSON 中被格式化为 `yyyy-MM-dd HH:mm:ss` 形式的字符串，而不是时间戳或默认的 ISO-8601 格式

#### Scenario: 序列化或反序列化失败时抛出异常
- **WHEN** 调用 `JacksonUtils.toObj(json, SomeClass.class)`，其中 `json` 不是合法 JSON 或与 `SomeClass` 结构不兼容
- **THEN** 方法抛出运行时异常，不返回 `null`，调用方可自行捕获并决定降级策略

### Requirement: 统一的 HTTP 客户端工具类
系统 SHALL 提供一个位于 `cn.nihility.rbac.common.util` 包下的 `HttpClientUtils` 工具类，封装对外发起 HTTP 请求的能力，供需要主动调用外部接口的模块（如应用数据同步通知）复用，避免各处重复处理连接池、超时、证书校验等细节。

`HttpClientUtils` SHALL 支持 `GET`/`POST`/`PUT`/`PATCH` 四种请求方法，SHALL 支持以下四种请求体格式：`application/json`（对象与 JSON 互转复用 `JacksonUtils`）、`multipart/form-data`（含文本字段与二进制文件字段）、`application/x-www-form-urlencoded`、任意二进制内容（自定义 `Content-Type`）。

`HttpClientUtils` SHALL 支持按次请求单独指定响应超时时间；未指定时使用全局默认响应超时（5 秒）；连接超时统一使用全局默认（5 秒），不支持按次覆盖。`HttpClientUtils` 内部 SHALL 使用连接池化的 HTTP 客户端（最大连接数、单路由最大连接数可配置），不 SHALL 为每次请求创建新的连接管理器。`HttpClientUtils` SHALL 支持通过全局配置开启"跳过 HTTPS 证书校验"（用于自签名证书场景），开启后对 `https://` 地址的请求 SHALL NOT 因证书不受信任而失败。

#### Scenario: 发送 JSON 请求
- **WHEN** 调用方使用 `HttpClientUtils` 以 `POST` 方式、`application/json` 格式向某地址发送一个对象作为请求体
- **THEN** 请求体是该对象序列化后的 JSON 字符串，`Content-Type` 为 `application/json`

#### Scenario: 发送 multipart/form-data 请求
- **WHEN** 调用方使用 `HttpClientUtils` 以 `POST` 方式发送包含文本字段与一个二进制文件字段的表单
- **THEN** 请求以 `multipart/form-data` 格式发出，文本字段与文件字段均正确携带

#### Scenario: 未指定响应超时时使用全局默认值
- **WHEN** 调用方调用 `HttpClientUtils` 发起请求且未指定响应超时时间
- **THEN** 该次请求使用全局默认响应超时（5 秒）

#### Scenario: 指定响应超时覆盖全局默认值
- **WHEN** 调用方调用 `HttpClientUtils` 发起请求并显式指定响应超时时间为 3 秒
- **THEN** 该次请求的响应超时按 3 秒生效，不使用全局默认的 5 秒

#### Scenario: 跳过 HTTPS 证书校验
- **WHEN** 全局配置开启"跳过 HTTPS 证书校验"，调用方请求一个使用自签名证书的 `https://` 地址
- **THEN** 请求正常发出并能获取响应，不因证书不受信任而抛出异常

#### Scenario: 连接池复用
- **WHEN** 调用方连续多次调用 `HttpClientUtils` 向同一地址发起请求
- **THEN** 各次请求复用同一个连接池管理的 HTTP 客户端，不为每次请求重新建立连接管理器

### Requirement: Flyway 迁移目录保持单一基线
`backend/src/main/resources/db/migration/` 目录 SHALL 以一份反映当前最终数据库状态（全部
建表语句 + 全部种子数据）的单一基线迁移文件（`V1__init_schema.sql`）作为起点，不 SHALL
无限堆积仅用于记录“某张表历史上是怎么一步步改过来的”的中间过程 `ALTER`/`UPDATE` 文件；
基线文件 SHALL 直接体现字段的最终形态（如已完成的字典编码转换），不保留转换前的中间列
定义或转换步骤。后续新的结构变更 SHALL 继续以递增版本号的增量迁移文件形式添加在基线之后，
不 SHALL 修改已发布的基线文件本身；当增量迁移文件积累到影响可维护性的程度时，SHALL 允许
仿照本次操作重新合并出一份新的基线。

#### Scenario: 新环境执行迁移只需应用基线文件
- **WHEN** 在一个全新、空的数据库上执行 `flyway migrate`
- **THEN** 系统只需应用 `V1__init_schema.sql` 一个文件即可得到包含全部 40 张业务表结构与
  种子数据的完整基线，无需再应用任何历史中间迁移文件

#### Scenario: 已执行过旧版本历史迁移的库需要清库重建
- **WHEN** 某个数据库此前已经执行过被合并、删除的旧版本迁移文件（如原 `V5__init_tab_user.sql`）
- **THEN** 该库需要先清空（或删除 `flyway_schema_history` 表）后重新执行新的
  `V1__init_schema.sql`，否则 Flyway 会因找不到对应版本号的历史文件而报错

### Requirement: 统一解析当前登录操作人账号编码
系统 SHALL 提供一个位于 `cn.nihility.rbac.auth.service` 包下的服务 `CurrentOperatorService`，基于 `CurrentUserContext.getUserId()`（`IdentityAuthFilter` 校验通过的已登录会话中已设置）解析出当前请求发起者的用户 id（`tab_user.id`），供各业务模块的新增/编辑/启停用/删除等写操作复用，填充其 `create_by`/`update_by` 审计字段，以及操作日志的 `create_by` 字段。各业务模块 SHALL NOT 再使用与登录会话无关的固定字符串/固定 id 常量填充这些字段。

`CurrentUserContext.getUserId()` 取不到值（当前线程不处于已认证的 HTTP 请求上下文中）时，SHALL 视为调用方用法错误而不是静默降级为某个固定占位符——业务写操作的正常调用路径均发生在 `IdentityAuthFilter` 校验通过之后的同一线程内，取不到值意味着调用方脱离了预期的调用上下文（如遗漏在测试中设置登录态）。

#### Scenario: 已登录会话下解析出真实操作人用户 id
- **WHEN** 某个已登录账号（`tab_user.id` 为某个具体值，如 `1001`）发起一次新增/编辑等写操作
- **THEN** 该次写操作落库的 `create_by`/`update_by`（或产生的操作日志 `create_by`）等于该账号的 `id`，而不是账号编码或固定字符串

#### Scenario: 不同账号发起的操作各自归属到本人
- **WHEN** 账号 A 和账号 B 先后各自发起一次写操作
- **THEN** A 产生的记录 `create_by` 为 A 的用户 id，B 产生的记录 `create_by` 为 B 的用户 id，两者不同且都不是同一个固定值

#### Scenario: 用户改名/改账号编码后历史审计字段仍能关联回本人
- **WHEN** 某用户此前发起过写操作留下 `create_by` 记录，之后该用户修改了自己的姓名或账号编码
- **THEN** 该历史记录的 `create_by`（用户 id）不受影响，仍能通过该 id 查到该用户当前最新的姓名/账号编码

#### Scenario: 脱离已登录上下文调用时不静默使用固定占位符
- **WHEN** 调用方在没有先设置 `CurrentUserContext` 当前用户 id 的情况下调用 `CurrentOperatorService` 解析操作人
- **THEN** 系统抛出运行时异常，不返回任何固定值（如账号编码 "admin"/"system" 或固定 id）作为兜底操作人标识

### Requirement: 请求参数缺失/类型错误的明确提示
系统的全局异常处理器 SHALL 识别 Spring MVC 在请求参数绑定阶段抛出的"必填参数缺失"与"参数类型不匹配"异常，返回业务错误码（而不是笼统的服务器内部错误），错误信息中 SHALL 明确指出具体是哪一个参数出了问题；不属于这两类的未预期异常，系统 SHALL 继续按现有的兜底逻辑处理（记录服务端日志、对外只返回笼统的服务器内部错误提示，不暴露异常堆栈）。

#### Scenario: 缺少必填的请求参数
- **WHEN** 某接口的必填 `@RequestParam` 在实际请求中缺失
- **THEN** 系统返回业务错误码，错误信息中包含该参数的名称，而不是"服务器内部错误"

#### Scenario: 请求参数类型不匹配
- **WHEN** 某接口的 `@RequestParam` 收到的值无法转换为其声明的类型（如声明为 `Long` 但收到非数字字符串）
- **THEN** 系统返回业务错误码，错误信息中包含该参数的名称，而不是"服务器内部错误"

#### Scenario: 未预期异常仍按兜底逻辑处理
- **WHEN** 发生一个既不是参数缺失/类型不匹配、也不是已知业务异常的未预期异常
- **THEN** 系统记录服务端日志，对外仍只返回笼统的服务器内部错误提示，不改变现有行为

### Requirement: 未匹配路由/静态资源返回真实的 404
系统的全局异常处理器 SHALL 识别 Spring MVC 因请求未命中任何已注册的 Controller 路由、也
不是已注册的静态资源而抛出的 `NoResourceFoundException`，返回真实的 HTTP 404 状态码（而
不是像其余异常类型那样统一返回 HTTP 200、仅在响应体 `code` 字段里携带错误码）；响应体仍
遵循 `{ code, message, data }` 结构，`code` 为 `404`，`message` 中 SHALL 明确指出具体是
哪一个请求路径未能匹配。该异常 SHALL NOT 被当作未预期的系统异常记录 ERROR 级别日志。

#### Scenario: 请求一个不存在的路由
- **WHEN** 客户端请求一个未注册任何 Controller 处理方法、也不是静态资源的路径
- **THEN** 系统返回 HTTP 404 状态码，响应体 `code` 为 `404`，`message` 中包含具体的请求路径

#### Scenario: 未匹配路由不记录为系统异常日志
- **WHEN** 触发一次 `NoResourceFoundException`
- **THEN** 系统不记录 ERROR 级别日志，与兜底处理未预期异常的行为区分开

### Requirement: 全局固定大小线程池
系统 SHALL 提供一个全局唯一的固定大小线程池，核心线程数与最大线程数均为 4，
供业务代码提交异步任务，无需自行创建或管理 `ExecutorService`。

#### Scenario: 获取全局线程池执行任务
- **WHEN** 业务代码调用线程池工具类提交一个 `Runnable`/`Callable` 任务
- **THEN** 任务被提交到进程内唯一的全局线程池执行，核心/最大线程数为 4

### Requirement: 有界任务队列
线程池 SHALL 使用容量为 2048 的有界队列缓存待执行任务，避免任务无限堆积。

#### Scenario: 任务队列缓存待执行任务
- **WHEN** 当前 4 个线程都在执行任务，且有新任务提交
- **THEN** 新任务进入容量为 2048 的有界队列等待执行，而不是被立即拒绝或无限扩容

### Requirement: 队列满时拒绝新任务并记录日志
当线程数已达到最大值（4）且任务队列已满（2048）时，线程池 SHALL 拒绝新提交的任务，
并向调用方抛出异常，而不是静默丢弃任务或无限阻塞等待；拒绝发生时 SHALL 记录一条
包含拒绝原因（线程池当前状态，如活跃线程数、队列积压数）的日志，便于运维排查过载原因。

#### Scenario: 线程与队列均已饱和时提交新任务
- **WHEN** 4 个线程均在执行任务，且队列中已有 2048 个待执行任务，此时业务代码再提交
  一个新任务
- **THEN** 线程池立即拒绝该任务并抛出 `RejectedExecutionException`，调用方可据此感知
  过载并做降级处理

#### Scenario: 拒绝任务时记录日志
- **WHEN** 线程池因线程与队列均已饱和而拒绝一个新提交的任务
- **THEN** 系统记录一条 WARN 级别日志，日志内容包含拒绝原因及线程池当前状态
  （如活跃线程数、队列积压数），而不是只抛异常不留任何日志痕迹

### Requirement: 全局字符串 Redis 工具类
系统 SHALL 提供一个全局静态工具类（`RedisUtils`），封装基于 `StringRedisTemplate` 的
字符串 key 读写、Hash 字段读写、key 存在性判断、过期时间设置与删除操作，供业务代码
直接调用，无需各自注入 `StringRedisTemplate`。

#### Scenario: 读写字符串 key
- **WHEN** 业务代码调用 `RedisUtils.set(key, value, timeout, unit)` 写入一个字符串值
  并设置过期时间
- **THEN** 之后调用 `RedisUtils.get(key)` 能读取到该值，超过过期时间后读取返回空

#### Scenario: 读写 Hash 字段
- **WHEN** 业务代码调用 `RedisUtils.putHash`/`putHashField` 写入一个 Hash 的多个/单个字段
- **THEN** 调用 `RedisUtils.hashEntries`/`getHashObject` 能读取到已写入的字段值

### Requirement: 基于 JSON 字符串中转的对象存取
`RedisUtils` SHALL 提供对象存取方法（`setObject`/`getObject`/`putHashObject`/
`getHashObject`），写入前把对象序列化为 JSON 字符串、读取后按目标类型反序列化，
供业务代码直接存取对象实例而不必手动调用 JSON 工具类。

#### Scenario: 存取对象实例
- **WHEN** 业务代码调用 `RedisUtils.setObject(key, payload, timeout, unit)` 写入一个
  对象实例
- **THEN** 调用 `RedisUtils.getObject(key, PayloadClass.class)` 能读取到反序列化后的
  同等对象

### Requirement: 基于专用对象 Redis 模板的对象存取
系统 SHALL 提供一个基于专用 `objectRedisTemplate`（`RedisTemplate<String, Object>`，
value/hashValue 用 `Jackson2JsonRedisSerializer` 包装）的对象存取工具类
（`RedisObjectUtils`），使 `opsForValue()`/`opsForHash()` 可以直接 put/get 对象实例，
序列化在 Redis 客户端层面完成，不需要调用方手动转 JSON 字符串。

#### Scenario: 直接存取对象实例
- **WHEN** 业务代码调用 `RedisObjectUtils.set(key, value)` 写入一个对象实例
- **THEN** 调用 `RedisObjectUtils.get(key, ValueClass.class)` 能读取到转换后的同等对象，
  底层未经过手动 JSON 字符串序列化

#### Scenario: 读取原始值并按需转换类型
- **WHEN** 业务代码调用 `RedisObjectUtils.get(key)` 读取一个已写入的复杂对象
- **THEN** 返回值为反序列化后的原始 `LinkedHashMap`（Jackson 处理未知目标类型的标准
  行为），业务代码可再调用 `RedisObjectUtils.get(key, ValueClass.class)` 转换为目标类型

### Requirement: Redis 工具类未初始化时明确报错
`RedisUtils`/`RedisObjectUtils` 依赖的底层 Redis 模板 SHALL 由 Spring 容器在启动阶段
注入；容器尚未完成注入时调用工具类方法 SHALL 抛出 `IllegalStateException`，而不是
返回 `null` 或抛出难以定位根因的 `NullPointerException`。

#### Scenario: Spring 容器未启动时调用工具类方法
- **WHEN** `RedisUtils`/`RedisObjectUtils` 尚未完成初始化（底层 Redis 模板未注入）时，
  调用其读写方法
- **THEN** 方法抛出 `IllegalStateException`，异常信息提示容器尚未启动完成初始化

### Requirement: 可复用的转换脚本执行工具类
系统 SHALL 提供一个位于 `cn.nihility.rbac.common.util` 包下的静态工具类
`ScriptTransformExecutor`，在最小权限的 GraalVM 沙箱内执行一段 JavaScript 转换脚本
（脚本以 `value` 全局变量读入源字段值，脚本最后一个表达式的值作为结果），供后端各处
需要"转换脚本"能力的字段映射功能共同复用，不各自重复实现沙箱执行细节。脚本执行 SHALL
有超时保护（200 毫秒），超时或执行异常时 SHALL 返回 `null` 并记录 WARN 级别日志，不
SHALL 向上抛出异常影响调用方后续处理。

#### Scenario: 脚本正常执行返回结果
- **WHEN** 调用方执行一段合法的转换脚本，源字段值为某个具体值
- **THEN** 返回该脚本最后一个表达式的求值结果

#### Scenario: 脚本执行超时返回 null
- **WHEN** 调用方执行的脚本运行超过 200 毫秒仍未结束
- **THEN** 方法返回 `null`，记录一条 WARN 日志，不向上抛出异常

#### Scenario: 脚本执行异常返回 null
- **WHEN** 调用方执行的脚本在运行期间抛出异常（如访问未定义变量）
- **THEN** 方法返回 `null`，记录一条 WARN 日志，不向上抛出异常
