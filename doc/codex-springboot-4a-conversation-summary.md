# 对话结果汇总

> 主题：Codex、Spring Boot、FastAPI、Actuator、4A 数据同步设计  
> 导出日期：2026-08-28

---

## 1. Codex 初始化项目

对于已有项目，进入项目根目录后启动 Codex：

```bash
cd your-project
codex
```

然后在 Codex 交互界面中执行：

```text
/init
```

通常会生成项目级：

```text
AGENTS.md
```

用于定义项目构建、测试、编码规范、工作流程等约束。

推荐流程：

```text
项目目录
   ↓
git init / git status
   ↓
codex
   ↓
/init
   ↓
检查并完善 AGENTS.md
   ↓
让 Codex 分析项目
   ↓
再执行代码修改
```

---

## 2. Spring Boot Validation 使用

### 2.1 引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### 2.2 DTO 参数校验

```java
import jakarta.validation.constraints.*;

public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在2到20之间")
    private String username;

    @NotNull(message = "年龄不能为空")
    @Min(value = 18, message = "年龄不能小于18岁")
    @Max(value = 120, message = "年龄不能超过120岁")
    private Integer age;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

### 2.3 Controller 中触发校验

```java
@PostMapping("/api/v1/users")
public String create(@Valid @RequestBody UserCreateRequest request) {
    return "success";
}
```

### 2.4 `@Valid` 与 `@Validated`

- `@Valid`：Jakarta Validation 标准注解，适合普通 DTO、嵌套校验。
- `@Validated`：Spring 提供，支持分组校验和方法级参数校验。

Spring Boot 3.x 使用：

```java
import jakarta.validation.*;
```

Spring Boot 2.x 通常使用：

```java
import javax.validation.*;
```

---

## 3. Codex 用户全局 Spring Boot 开发规范

建议用户全局规范放在：

```text
~/.codex/AGENTS.md
```

Windows 通常是：

```text
C:\Users\用户名\.codex\AGENTS.md
```

### 3.1 Controller 规范

```md
## Controller 规范

- Controller 必须保持轻量、简洁。
- Controller 只负责：
  - 定义 HTTP 接口；
  - 接收和绑定请求参数；
  - 使用 `@Valid` 或 `@Validated` 触发参数校验；
  - 调用 Service / Application 层；
  - 返回接口响应结果。

- 禁止在 Controller 中编写业务逻辑。
- 禁止在 Controller 中直接访问数据库。
- 禁止 Controller 直接调用 Mapper、Repository、DAO。
- 禁止 Controller 直接操作 Redis、MQ、ES 等基础设施组件。
- 禁止在 Controller 中编写事务逻辑。
- 所有业务逻辑必须放在 Service / Application 层。
```

### 3.2 URL 映射规范

要求每个方法直接声明完整 URL。

推荐：

```java
@RestController
public class UserController {

    @PostMapping("/api/v1/users")
    public UserResponse create(
            @Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @GetMapping("/api/v1/users/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }
}
```

禁止：

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @PostMapping
    public UserResponse create(...) {
        ...
    }

    @GetMapping("/{id}")
    public UserResponse get(...) {
        ...
    }
}
```

核心约束：

```md
- 禁止使用类级别 `@RequestMapping` 定义公共路径前缀。
- `@GetMapping`、`@PostMapping`、`@PutMapping`、`@PatchMapping`、`@DeleteMapping`
  必须直接写完整接口路径。
```

---

## 4. OpenSpec 工作流约束

在任何编码、新增功能、修改代码、重构或修复缺陷前，需要优先检查：

- `proposal.md`
- `design.md`
- `tasks.md`

### 4.1 编码前检查

必须检查：

1. 当前需求是否存在对应 OpenSpec 文档。
2. `proposal.md` 是否描述目标、范围和变更内容。
3. `design.md` 是否与实际架构、代码结构、技术选型一致。
4. `tasks.md` 是否与准备执行的任务一致。
5. 三份文档之间是否一致。
6. 文档是否与当前代码状态同步。

### 4.2 文档修改后必须停止

如果 OpenSpec 文档缺失、冲突或不同步：

```text
禁止直接编码
    ↓
先修改 OpenSpec
    ↓
完成 proposal.md / design.md / tasks.md
    ↓
立即停止
    ↓
等待人工确认
```

核心规则：

> OpenSpec 文档完成不等于允许编码。

> `tasks.md` 是任务计划，不是执行授权。

> Codex 禁止自行批准 OpenSpec，禁止自动从文档阶段进入编码阶段。

### 4.3 允许进入编码阶段的人工确认

例如：

```text
确认
确认执行
可以开始编码
按 tasks.md 执行
开始实现
```

没有明确人工批准时，禁止自动执行 `tasks.md`。

---

## 5. Codex AGENTS.md 按功能拆分

推荐目录：

```text
~/.codex/
├── AGENTS.md
└── rules/
    ├── 00-general.md
    ├── 10-openspec-workflow.md
    ├── 20-springboot.md
    ├── 30-database.md
    ├── 40-testing.md
    ├── 50-git.md
    └── 60-security.md
```

`AGENTS.md` 作为总入口和规则路由：

```md
# Codex 用户全局开发规范

## 规则加载

执行任务时，根据任务类型读取以下规则：

- 通用规则：`rules/00-general.md`
- OpenSpec 工作流：`rules/10-openspec-workflow.md`
- Java / Spring Boot：`rules/20-springboot.md`
- 数据库相关：`rules/30-database.md`
- 测试相关：`rules/40-testing.md`
- Git 相关：`rules/50-git.md`
- 安全相关：`rules/60-security.md`

## 强制工作流

任何涉及代码新增、修改、重构或修复的任务：

1. 必须首先读取 `rules/10-openspec-workflow.md`。
2. 根据项目技术栈读取对应开发规范。
3. 完成规范检查后才能决定是否允许进入编码阶段。
4. OpenSpec 文档发生新增或修改后必须停止，等待用户人工确认。
5. 禁止自动执行 `tasks.md`。
```

注意：拆分后的普通 Markdown 文件不会天然自动变成独立 Agent，应在主 `AGENTS.md` 中明确要求“什么时候必须读取”。

---

## 6. Springdoc / OpenAPI Controller 接口规范

如果项目存在 Springdoc / OpenAPI：

- 新增或修改接口时必须同步维护接口文档。
- 必须维护接口描述、路径参数、Query 参数、DTO 字段说明。
- 不允许接口实现变化但文档不更新。

### 6.1 接口描述

```java
@Operation(
    summary = "创建用户",
    description = "创建新的用户信息"
)
@PostMapping("/api/v1/users")
public UserResponse create(
        @Valid @RequestBody UserCreateRequest request) {
    return userService.create(request);
}
```

### 6.2 路径参数

```java
@Operation(summary = "查询用户详情")
@GetMapping("/api/v1/users/{id}")
public UserResponse get(
        @Parameter(description = "用户ID", required = true)
        @PathVariable Long id) {
    return userService.get(id);
}
```

### 6.3 DTO 字段

```java
public class UserCreateRequest {

    @Schema(description = "用户名", example = "zhangsan")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "手机号", example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    private String mobile;
}
```

原则：

```md
- 项目未使用 Springdoc 时，不主动引入 Springdoc。
- 项目已使用 Springdoc 时，新增接口必须补充接口说明。
- 新增或修改参数时必须同步维护参数说明。
- OpenAPI 文档必须与实际行为一致。
```

---

## 7. Codex 重新加载 AGENTS.md

修改 `AGENTS.md` 后，最稳妥的方法是开启新会话。

CLI：

```text
/new
```

或者退出后重新启动：

```bash
exit
codex
```

可以使用检查提示验证规则是否已加载：

```text
请先读取并总结当前生效的 AGENTS.md 规则，不要修改任何代码。
```

---

## 8. Spring Boot `@ModelAttribute`

`@ModelAttribute` 的主要作用：

> 将 Query/Form 请求参数绑定到一个 Java 对象，并将对象加入 MVC Model。

例如：

```java
public class UserQuery {
    private String name;
    private Integer age;
}
```

Controller：

```java
@GetMapping("/api/v1/users")
public List<User> list(@ModelAttribute UserQuery query) {
    return userService.list(query);
}
```

请求：

```text
GET /api/v1/users?name=zhangsan&age=20
```

Spring 会自动绑定：

```text
name = zhangsan
age = 20
```

常见对比：

| 注解 | 主要用途 |
|---|---|
| `@RequestParam` | 单个 Query/Form 参数 |
| `@ModelAttribute` | 多个 Query/Form 参数绑定成对象 |
| `@RequestBody` | HTTP Body，通常是 JSON |
| `@PathVariable` | URL 路径参数 |

REST API 中可以简单理解：

```text
GET Query 参数对象
        ↓
@ModelAttribute

POST JSON Body
        ↓
@RequestBody
```

---

## 9. Python FastAPI 基础示例

安装：

```bash
pip install fastapi uvicorn
```

示例：

```python
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field, EmailStr

app = FastAPI(
    title="FastAPI 示例",
    version="1.0.0",
)


class UserCreateRequest(BaseModel):
    username: str = Field(..., min_length=2, max_length=20)
    age: int = Field(..., ge=18, le=120)
    email: EmailStr


class UserResponse(BaseModel):
    id: int
    username: str
    age: int
    email: str


users: dict[int, UserResponse] = {}


@app.post(
    "/api/v1/users",
    response_model=UserResponse,
    status_code=201,
)
def create_user(request: UserCreateRequest):
    user_id = len(users) + 1

    user = UserResponse(
        id=user_id,
        username=request.username,
        age=request.age,
        email=request.email,
    )

    users[user_id] = user
    return user
```

启动：

```bash
uvicorn main:app --reload
```

默认文档：

```text
http://127.0.0.1:8000/docs
http://127.0.0.1:8000/redoc
```

Spring Boot 与 FastAPI 对应：

| Spring Boot | FastAPI |
|---|---|
| `@RestController` | `FastAPI()` / Router |
| `@GetMapping` | `@app.get()` |
| `@PostMapping` | `@app.post()` |
| `@RequestBody` | Pydantic Model |
| `@RequestParam` | `Query()` |
| `@PathVariable` | 路由参数 |
| `@Valid` | Pydantic 自动校验 |
| Springdoc | FastAPI 内置 OpenAPI |

---

## 10. Spring Boot 彻底关闭 Actuator

目标：

```text
/actuator
/actuator/**
```

全部不可访问。

### 10.1 Spring Boot 3.4+ 新配置

`management.endpoints.enabled-by-default` 已弃用，新方式：

```yaml
management:
  endpoints:
    access:
      default: none
```

### 10.2 保留管理端口，但所有 Actuator HTTP 接口关闭

```yaml
management:
  server:
    port: 8081

  endpoints:
    access:
      default: none

    web:
      exposure:
        exclude: "*"

      discovery:
        enabled: false
```

其中：

```yaml
management:
  endpoints:
    web:
      discovery:
        enabled: false
```

用于关闭 `/actuator` discovery page。

### 10.3 完全禁用 HTTP management server

如果 8081 只为 Actuator 使用，推荐：

```yaml
management:
  server:
    port: -1
```

这是最彻底的方式。

---

## 11. Codex 会话上下文管理

CLI 中常用命令：

| 目的 | 命令 |
|---|---|
| 查看当前会话状态 / token 使用 | `/status` |
| 压缩上下文 | `/compact` |
| 开启新上下文 | `/new` |
| 退出 | `/quit` / `/exit` |

桌面端如果要彻底清空上下文，最可靠的方法是：

```text
New chat
```

也就是新建一个 Codex 会话。

---

## 12. Codex Agent 设计

需要区分：

```text
AGENTS.md
```

和真正的多 Agent / Subagent。

`AGENTS.md` 是规则与上下文，不是创建独立 Agent。

推荐将规则、工作流和角色职责拆开：

```text
~/.codex/
├── AGENTS.md
├── workflows/
│   └── openspec-workflow.md
├── preferences/
│   └── springboot-preferences.md
└── agents/
    ├── openspec-agent.md
    ├── developer-agent.md
    ├── test-agent.md
    └── review-agent.md
```

逻辑上可以形成：

```text
主 Agent
  │
  ├── OpenSpec Agent
  │     proposal / design / tasks
  │
  ├── Spring Boot Agent
  │     Controller / Service / Validation / Springdoc
  │
  ├── Test Agent
  │     单元测试 / 集成测试
  │
  └── Review Agent
        最终代码审查
```

---

# 13. 4A 按应用推送 / 拉取机构、人员、任职数据设计

这是本次对话中最重要的系统设计部分。

核心建议：

> 使用“统一变更日志 + 应用数据范围 + Cursor 增量拉取 + 可靠事件通知/推送 + 定期对账”。

整体架构：

```text
                 4A 主数据
                    │
          ┌─────────┴─────────┐
          │                   │
      业务数据表          ChangeLog
      机构/人员/任职      统一变更流水
                              │
                    ┌─────────┴─────────┐
                    │                   │
                 增量拉取             事件通知
                 /changes              Webhook/MQ
                    │                   │
                    └─────────┬─────────┘
                              │
                           业务应用
```

核心原则：

> 4A 数据库是事实源；ChangeLog 是同步事实源；通知只是“有变化了”的提示，而不是唯一的数据一致性保障。

---

## 13.1 不使用 `update_time` 作为唯一增量依据

不推荐：

```http
GET /api/users?updateTimeGt=2026-08-28T10:00:00
```

原因：

- 相同更新时间容易产生边界问题。
- 事务提交顺序与 update_time 顺序不一定一致。
- 分页期间数据持续变化。
- 删除数据无法可靠查询。
- 应用宕机恢复容易重复或遗漏。

推荐使用全局单调递增：

```text
change_seq
```

例如：

```text
100001 ORG        CREATE
100002 PERSON     UPDATE
100003 ASSIGNMENT DELETE
```

应用保存：

```text
last_change_seq = 100003
```

下一次继续：

```text
> 100003
```

对外最好使用 opaque cursor：

```text
cursor=xxxxx
```

---

## 13.2 核心实体

建议主数据独立：

```text
iam_org
iam_person
iam_assignment
```

任职表示：

```text
person + org + position + primary/status
```

每条实体增加：

```text
version
```

例如：

```text
person_id = P10001
version = 8
```

每次修改：

```text
8 → 9
```

版本号用于解决重复、乱序、重试覆盖等问题。

---

## 13.3 统一 ChangeLog

建议：

```text
iam_change_log
```

示例结构：

```sql
CREATE TABLE iam_change_log (
    change_seq      BIGINT PRIMARY KEY,
    entity_type     VARCHAR(32) NOT NULL,
    entity_id       VARCHAR(64) NOT NULL,
    operation       VARCHAR(16) NOT NULL,
    entity_version  BIGINT NOT NULL,
    occurred_at     TIMESTAMP NOT NULL,
    trace_id        VARCHAR(64),
    payload         JSON
);
```

实体类型：

```text
ORG
PERSON
ASSIGNMENT
```

对外同步操作建议简化为：

```text
UPSERT
DELETE
```

这样下游更容易实现幂等。

---

## 13.4 删除必须 Tombstone

删除人员不能只执行：

```sql
DELETE FROM iam_person WHERE id = 'P10001';
```

必须写入 ChangeLog：

```json
{
  "seq": 10237,
  "type": "PERSON",
  "id": "P10001",
  "operation": "DELETE",
  "version": 13
}
```

这样应用才能知道该数据已删除。

---

## 13.5 按应用控制数据范围

建议表：

```text
iam_application
iam_application_scope
```

应用范围示例：

```text
APP_A：
    总部 + 北京公司
    包含子机构
    正式在职员工

APP_B：
    上海公司
    全部在职人员
```

关键原则：

> 数据范围由 4A 服务端根据 app_id / access token 控制，不能由应用通过 query 参数自行决定。

---

## 13.6 数据离开授权范围

这是按范围同步最容易出错的场景。

例如：

```text
张三：北京 → 上海
```

应用 A 只能看北京。

如果只是过滤 UPDATE，应用 A 将永远保留张三的旧数据。

因此对应用视角，需要处理：

```text
ENTER
UPDATE
LEAVE
DELETE
```

可对外映射成：

```text
ENTER  → UPSERT
UPDATE → UPSERT
LEAVE  → DELETE
DELETE → DELETE
```

例如：

```json
{
  "operation": "DELETE",
  "reason": "OUT_OF_SCOPE",
  "entityType": "PERSON",
  "entityId": "P10001"
}
```

这是数据范围同步的核心设计点之一。

---

## 13.7 全量同步：Snapshot + Cursor

不建议：

```text
?page=1&size=1000
```

因为同步过程中数据仍会新增或删除，容易造成重复或遗漏。

推荐：

```http
POST /api/v1/sync/snapshots
```

返回：

```json
{
  "snapshotId": "S202608280001",
  "watermark": "C102400"
}
```

然后分页读取：

```http
GET /api/v1/sync/snapshots/{snapshotId}/persons?cursor=xxx
```

整个全量基于同一个逻辑快照。

全量结束后：

```text
cursor = watermark
```

然后进入增量。

---

## 13.8 增量同步

接口：

```http
GET /api/v1/sync/changes?cursor=xxxx&limit=500
```

返回：

```json
{
  "items": [
    {
      "seq": 102401,
      "type": "ORG",
      "operation": "UPSERT",
      "id": "ORG1001",
      "version": 7
    },
    {
      "seq": 102402,
      "type": "PERSON",
      "operation": "UPSERT",
      "id": "P10001",
      "version": 13
    }
  ],
  "nextCursor": "xxxxx",
  "hasMore": true
}
```

应用侧正确处理：

```text
读取 cursor=A
       ↓
请求变化数据
       ↓
本地开启事务
       ↓
写入业务数据
       ↓
保存 nextCursor=B
       ↓
COMMIT
```

关键：

> 业务数据和 cursor 必须同事务提交。

---

## 13.9 同步语义：At-Least-Once + 幂等

不要追求跨系统 Exactly Once。

推荐：

```text
At Least Once
+
Idempotent
```

允许重复，但绝不能漏。

结合：

```text
eventId
entity version
UPSERT
```

可以解决：

- 重复
- 乱序
- 重试

例如本地版本已经为 14，又收到版本 13：

```text
忽略 version=13
```

---

## 13.10 通知与数据同步分离

最推荐：

```text
Webhook/MQ 只通知“有变化”
             ↓
应用根据自己的 cursor 拉取 changes
```

Webhook：

```json
{
  "eventId": "EV100023",
  "type": "DATA_CHANGED",
  "latestCursor": "xxxxx"
}
```

应用收到后：

```http
GET /api/v1/sync/changes?cursor=自己的cursor
```

即使中间某个通知丢失：

```text
100
101 ← 通知丢失
102
```

应用收到 102 时仍从自己的 cursor 拉：

```text
100
101
102
```

因此：

> 通知可以丢，数据不能丢。

---

## 13.11 Push 模式

如果某些旧应用要求 4A 主动推完整数据，可以支持：

```text
PULL
PUSH
NOTIFY_PULL
```

Push 建议维护：

```text
app_delivery
```

状态：

```text
PENDING
SENDING
SUCCESS
RETRY
DEAD
```

并支持：

- 指数退避重试
- Dead Letter
- 手动重发
- 从指定 seq 重放
- 重新全量同步

---

## 13.12 Transactional Outbox

禁止：

```java
@Transactional
public void updateUser() {
    userRepository.update(user);
    httpClient.pushToApp(user);
}
```

推荐：

```text
数据库事务
┌────────────────────┐
│ 更新 PERSON         │
│ INSERT ChangeLog   │
│ INSERT Outbox      │
└────────────────────┘
        ↓
      COMMIT
```

再由异步 Worker：

```text
Outbox
  ↓
发送
  ↓
成功 → SUCCESS
失败 → RETRY
```

---

## 13.13 推送内容

建议包含：

```json
{
  "eventId": "01K3NABC...",
  "sequence": 103566,
  "entityType": "PERSON",
  "operation": "UPSERT",
  "entityId": "P10001",
  "version": 15,
  "occurredAt": "2026-08-28T16:40:12.123+08:00",
  "data": {
    "id": "P10001",
    "name": "张三"
  }
}
```

下游通过：

```text
eventId
+
version
```

实现幂等和乱序保护。

---

## 13.14 机构、人员、任职独立同步

不要将所有关系打成一个大对象。

建议：

```text
ORG
PERSON
ASSIGNMENT
```

例如：

```text
PERSON
P10001 张三
```

```text
ORG
O100 北京研发中心
```

```text
ASSIGNMENT
A10001
person=P10001
org=O100
position=开发工程师
primary=true
```

变化关系：

```text
人员姓名变化
→ PERSON UPSERT

组织名称变化
→ ORG UPSERT

人员调岗
→ ASSIGNMENT UPSERT

离职
→ ASSIGNMENT DELETE / 状态变化
→ PERSON 状态变化
```

---

## 13.15 默认推荐：通知 + 拉取 + 定时兜底

推荐：

```text
Webhook：实时触发
+
定时 Pull：例如每 5 分钟兜底
+
每日 Reconciliation
```

即使：

```text
Webhook 丢失
MQ 故障
应用停机
网络中断
```

恢复后都可以：

```text
cursor
  ↓
持续拉取
  ↓
追平最新数据
```

---

## 13.16 对账 Reconciliation

建议提供：

```http
GET /api/v1/sync/checksum
```

例如：

```text
ORG:
count = 1024
hash = xxx

PERSON:
count = 183422
hash = xxx

ASSIGNMENT:
count = 201533
hash = xxx
```

应用定期对账。

发现差异后可以：

```text
重新校验范围
或
重新全量同步
```

---

## 13.17 每个应用保存同步状态

建议：

```text
app_sync_state
```

字段示例：

| app_id | data_type | last_ack_seq | last_pull_at | status |
|---|---|---:|---|---|
| OA | PERSON | 105201 | 16:55 | NORMAL |
| HR | PERSON | 104013 | 13:20 | LAGGING |

监控指标：

```text
sync_lag_events
sync_lag_seconds
push_retry_count
push_dead_count
pull_request_count
cursor_invalid_count
```

可接入 Prometheus。

---

## 13.18 推荐 API

```text
# 初始化全量同步
POST /api/v1/sync/snapshots

# 全量机构
GET /api/v1/sync/snapshots/{snapshotId}/organizations

# 全量人员
GET /api/v1/sync/snapshots/{snapshotId}/persons

# 全量任职
GET /api/v1/sync/snapshots/{snapshotId}/assignments

# 增量
GET /api/v1/sync/changes?cursor=xxx&limit=500

# 单条补偿查询
GET /api/v1/sync/persons/{id}
GET /api/v1/sync/organizations/{id}
GET /api/v1/sync/assignments/{id}

# 同步状态
GET /api/v1/sync/status
```

不建议把：

```text
GET /users?updatedAt=xxx
```

作为正式同步协议。

---

# 14. 4A 同步设计最终硬规范

建议将以下规则作为系统级约束：

1. 每个应用的数据范围由 4A 服务端控制，应用不能自己决定范围。
2. 所有主数据变化进入统一 ChangeLog，并有全局单调 `change_seq`。
3. 删除和离开授权范围必须产生 DELETE / Tombstone。
4. 全量使用 Snapshot + Cursor。
5. 增量使用 Cursor。
6. 禁止依赖 `update_time + page/size` 作为唯一同步机制。
7. 同步语义使用 At-Least-Once + 幂等。
8. 实体携带 version，解决重复和乱序。
9. 默认采用 Webhook/MQ 通知 + Cursor Pull，而不是把 Push 当唯一可靠来源。
10. Push 使用 Transactional Outbox + Retry + DLQ。
11. 应用业务数据和自己的 Cursor 必须同事务提交。
12. 必须提供 Reconciliation / 全量重建能力。
13. 应用停机恢复后，能够从最后 cursor 继续追平，不能要求人工补数据。

最终效果：

```text
应用停机前 cursor = C100000
4A 当前          = C180000

应用恢复
   ↓
从 C100000 继续拉
   ↓
C120000
   ↓
C150000
   ↓
C180000
```

中间即使通知全部丢失，也不会丢数据。

---

# 15. 总结

本次对话主要形成了两套可持续落地的规范体系：

## Codex / Spring Boot 开发规范

```text
AGENTS.md
   ↓
OpenSpec 工作流
   ↓
人工确认
   ↓
Spring Boot 编码规范
   ↓
Validation / Springdoc / Controller
   ↓
测试和 Review
```

## 4A 数据同步规范

```text
4A 主数据
   ↓
ChangeLog
   ↓
Snapshot + Cursor
   ↓
Webhook/MQ 通知
   ↓
应用 Pull
   ↓
本地事务 + 幂等 + Version
   ↓
定时对账
```

这两套规范都强调：

- 明确流程
- 可恢复
- 可重放
- 不依赖人工补偿
- 不依赖“刚好不失败”
- 通过版本、游标、事务和审计保证最终一致性
