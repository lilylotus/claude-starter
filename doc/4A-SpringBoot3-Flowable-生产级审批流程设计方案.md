# 4A + Spring Boot 3.x + Flowable 生产级审批流程设计方案

> 适用场景：4A / IAM 平台中的组织、人员、岗位、角色、应用、权限等数据变更审批。  
> 推荐技术栈：Spring Boot 3.x + Java 17/21 + Flowable 7.2.x + Vue3 + 自定义流程设计器。

---

## 1. 设计目标

在 4A 系统中接入 Flowable 时，建议把 Flowable 定位为：

> **流程执行引擎，而不是完整审批业务平台。**

核心设计原则：

- 业务数据归 4A 管理。
- 审批实例、待办、审批记录使用自己的 `wf_*` 表维护。
- Flowable 只负责流程状态机、节点流转、条件路由、会签、并行、定时器、运行态和历史态。
- 不让业务代码直接依赖 `ACT_*` 表。
- 不让 Flowable IDM 成为 4A 用户、组织、岗位、角色的权威数据源。
- 前端不直接依赖 BPMN 细节，而是使用自己的流程 JSON DSL。
- 后端把 JSON DSL 编译成 BPMN 后部署给 Flowable。

---

# 2. 总体架构

```text
┌──────────────────────────── 4A 管理平台 ────────────────────────────┐
│                                                                     │
│   组织管理       人员管理       岗位管理       应用管理       权限管理 │
│      │             │             │             │             │      │
│      └─────────────┴─────────────┴─────────────┴─────────────┘      │
│                              │                                      │
│                       4A 业务服务层                                 │
│                              │                                      │
│             ┌────────────────┴─────────────────┐                    │
│             │                                  │                    │
│       审批业务中心                        流程设计中心                │
│             │                                  │                    │
│      biz_approval_*                      wf_model_*                  │
│             │                                  │                    │
│             └──────────────┬───────────────────┘                    │
│                            │                                        │
│                  WorkflowFacade / Adapter                           │
│                            │                                        │
│                    Flowable 7.x Engine                              │
│                            │                                        │
│        RuntimeService / TaskService / HistoryService                │
│                            │                                        │
│                       ACT_* 系列表                                  │
└─────────────────────────────────────────────────────────────────────┘
```

推荐模块：

```text
4A
├── workflow-model        流程设计
├── workflow-engine       Flowable 封装
├── workflow-approval     审批业务
├── workflow-rule         审批人解析 / 条件解析
└── workflow-event        事件、通知、回调
```

---

# 3. Flowable 必须通过自己的抽象层访问

不要让业务 Controller / Service 直接注入：

```java
RuntimeService
TaskService
RepositoryService
HistoryService
```

错误示例：

```java
@RestController
public class UserController {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;
}
```

推荐定义自己的流程接口：

```java
public interface WorkflowService {

    String start(StartProcessCommand command);

    void approve(ApproveCommand command);

    void reject(RejectCommand command);

    void withdraw(WithdrawCommand command);

    void terminate(TerminateCommand command);

    void transfer(TransferCommand command);

    void delegate(DelegateCommand command);

    void addSign(AddSignCommand command);

    List<ApprovalTask> findTasks(String userId);

    ProcessDetail getProcessDetail(String instanceId);
}
```

业务层只认识：

```text
start
approve
reject
return
withdraw
terminate
transfer
delegate
addSign
```

底层 Flowable 通过适配器实现：

```text
业务系统
      ↓
ApprovalService
      ↓
WorkflowService
      ↓
FlowableWorkflowService
      ↓
RuntimeService / TaskService / RepositoryService / HistoryService
```

这样未来 Flowable 升级或替换时，业务层改动最小。

---

# 4. 流程设计器建议自己实现

不建议直接把 Flowable 原生 Modeler 暴露给普通业务管理员。

普通业务用户更容易理解：

```text
开始
审批
抄送
条件
并行
自动任务
结束
```

而不是：

```text
exclusiveGateway
parallelGateway
sequenceFlow
multiInstanceLoopCharacteristics
completionCondition
candidateGroups
boundaryEvent
```

因此推荐：

```text
前端流程设计器
      ↓
Workflow JSON DSL
      ↓
WorkflowModelCompiler
      ↓
BpmnModel
      ↓
BPMN XML
      ↓
RepositoryService.deploy()
```

前端可以使用：

- LogicFlow
- AntV X6
- Vue Flow

---

# 5. 自定义 Workflow JSON DSL

例如：新增人员审批。

```json
{
  "code": "USER_CREATE",
  "name": "新增人员审批",
  "version": 3,
  "nodes": [
    {
      "id": "start",
      "type": "START"
    },
    {
      "id": "deptLeader",
      "type": "APPROVAL",
      "name": "部门负责人审批",
      "assignee": {
        "type": "DEPT_LEADER",
        "deptSource": "APPLICANT"
      }
    },
    {
      "id": "securityAdmin",
      "type": "APPROVAL",
      "name": "安全管理员审批",
      "assignee": {
        "type": "ROLE",
        "value": "SECURITY_ADMIN"
      }
    },
    {
      "id": "end",
      "type": "END"
    }
  ],
  "edges": [
    {
      "from": "start",
      "to": "deptLeader"
    },
    {
      "from": "deptLeader",
      "to": "securityAdmin"
    },
    {
      "from": "securityAdmin",
      "to": "end"
    }
  ]
}
```

审批节点可以扩展：

```json
{
  "id": "node_1001",
  "type": "APPROVAL",
  "name": "部门负责人审批",
  "assigneeType": "DEPT_LEADER",
  "approvalMode": "OR",
  "emptyAssigneeStrategy": "TO_ADMIN",
  "multiInstance": false,
  "allowTransfer": true,
  "allowAddSign": true,
  "allowReturn": true,
  "timeout": {
    "hours": 48
  }
}
```

条件节点示例：

```json
{
  "type": "CONDITION",
  "conditions": [
    {
      "field": "changeType",
      "operator": "EQ",
      "value": "DELETE"
    }
  ]
}
```

---

# 6. BPMN 由后端编译生成

例如 DSL：

```json
{
  "assignee": {
    "type": "DEPT_LEADER",
    "deptSource": "APPLICANT"
  }
}
```

后端生成 BPMN：

```xml
<userTask
    id="deptLeader"
    name="部门负责人审批"
    flowable:assignee="${approvalAssigneeResolver.resolve(execution)}">
</userTask>
```

建议不要在 BPMN 表达式中堆业务逻辑。

不要这样：

```text
${orgService.findParentDept(
    userService.getUser(startUserId).getDeptId()
).getManagerId()}
```

推荐：

```text
BPMN
  ↓
简单变量 / 标识
  ↓
AssigneeResolver
  ↓
4A OrganizationService / UserService / PositionService / RoleService
```

---

# 7. 4A 作为唯一身份权威

不建议 Flowable IDM 再维护：

```text
ACT_ID_USER
ACT_ID_GROUP
```

4A 应当是唯一身份源：

```text
4A
├── 用户
├── 组织
├── 岗位
├── 任职
├── 角色
├── 应用管理员
├── 安全管理员
└── 审计管理员
```

Flowable 中只保存：

```text
assignee = userId
candidateUser = userId
candidateGroup = roleCode / deptId
```

审批人解析接口：

```java
public interface AssigneeResolver {

    Set<String> resolve(
        AssigneeRule rule,
        ApprovalContext context
    );
}
```

实现：

```text
UserAssigneeResolver
RoleAssigneeResolver
PositionAssigneeResolver
DeptLeaderAssigneeResolver
ParentDeptLeaderAssigneeResolver
ApplicantLeaderAssigneeResolver
AppAdminAssigneeResolver
```

---

# 8. 4A 推荐支持的审批人规则

至少支持：

```text
指定人员
指定角色
指定岗位
指定组织负责人
发起人部门负责人
发起人部门上级负责人
指定部门负责人
应用管理员
应用安全管理员
应用审计管理员
表单字段指定人员
表单字段所属部门负责人
上一节点审批人
流程发起人
```

例如：

```json
{
  "type": "ORG_LEADER",
  "org": {
    "source": "FORM",
    "field": "orgId"
  },
  "level": 1
}
```

---

# 9. 建立自己的审批业务表

不要只依赖：

```text
ACT_RU_TASK
ACT_HI_TASKINST
ACT_HI_PROCINST
```

建议至少包含以下表。

## 9.1 wf_process_definition

```text
id
process_code
process_name
business_type
flowable_definition_id
flowable_definition_key
version
status
tenant_id
created_by
created_time
```

## 9.2 wf_process_instance

```text
id
process_definition_id
process_code
flowable_instance_id
business_type
business_id
title
applicant_id
applicant_dept_id
status
current_node
started_time
finished_time
tenant_id
```

## 9.3 wf_approval_task

```text
id
process_instance_id
flowable_task_id
node_id
node_name
assignee_id
status
created_time
finished_time
```

## 9.4 wf_approval_record

```text
id
process_instance_id
task_id
node_id
node_name
approver_id
action
comment
from_user_id
created_time
```

审批动作：

```text
SUBMIT
APPROVE
REJECT
RETURN
TRANSFER
DELEGATE
ADD_SIGN
WITHDRAW
TERMINATE
```

---

# 10. 为什么需要自己的 wf_approval_task

Flowable 查询：

```java
taskService
    .createTaskQuery()
    .taskAssignee(userId)
```

可以用于引擎内部查询。

但 4A 实际待办通常需要：

```text
我的待办
我的已办
部门待办
应用待办
审批类型
业务名称
申请人
机构
时间
流程状态
关键字
分页
```

推荐：

```text
Flowable
    ↓
负责流程状态机

wf_approval_task
    ↓
负责业务查询和列表展示
```

例如：

```sql
select
    t.*,
    u.username,
    o.org_name,
    i.title
from wf_approval_task t
join wf_process_instance i
    on i.id = t.process_instance_id
join sys_user u
    on ...
join sys_org o
    on ...
where
    t.assignee_id = ?
and t.status = 'PENDING';
```

禁止业务 SQL 直接 join Flowable 私有表。

---

# 11. ACT_* 表的边界

推荐数据库逻辑划分：

```text
4a

├── sys_org
├── sys_user
├── sys_position
├── sys_user_position
├── sys_role
├── sys_permission
│
├── wf_model
├── wf_definition
├── wf_instance
├── wf_task
├── wf_record
├── wf_business_request
├── wf_operation_request
├── wf_event
│
└── ACT_RE_*
    ACT_RU_*
    ACT_HI_*
    ACT_GE_*
```

边界：

```text
ACT_* = Flowable 私有表
wf_*  = 审批平台业务表
sys_* = 4A 主数据
```

禁止：

```sql
update ACT_RU_TASK ...
```

也不要：

```sql
select *
from ACT_RU_TASK
join sys_user ...
```

---

# 12. 审批业务与正式业务数据分离

例如：

```text
新增人员
编辑人员
删除人员
部门变更
岗位变更
角色授权
权限申请
应用接入
```

不要在发起审批时直接修改正式主数据。

推荐：

```text
提交业务申请
     ↓
保存变更快照
     ↓
启动流程
     ↓
审批通过
     ↓
执行业务变更
```

建议建立：

```text
iam_change_request
```

字段：

```text
id
business_type
target_type
target_id
before_data JSON
after_data JSON
status
process_instance_id
created_by
created_time
```

例如原数据：

```json
{
  "name": "张三",
  "deptId": "D001"
}
```

申请数据：

```json
{
  "name": "张三",
  "deptId": "D002"
}
```

审批中：

```text
sys_user 仍然保持 D001
```

审批通过后：

```text
ChangeExecutor
     ↓
sys_user.dept_id = D002
```

这样可以避免：

```text
先修改正式数据
↓
审批拒绝
↓
再回滚正式数据
```

---

# 13. ApprovalBusinessExecutor

不同审批类型最终执行不同业务。

```java
public interface ApprovalBusinessExecutor {

    String businessType();

    void onApproved(
        ApprovalInstance instance
    );

    void onRejected(
        ApprovalInstance instance
    );
}
```

例如：

```java
@Component
public class UserCreateExecutor
        implements ApprovalBusinessExecutor {

    @Override
    public String businessType() {
        return "USER_CREATE";
    }

    @Override
    public void onApproved(
            ApprovalInstance instance) {

        // 创建正式用户
    }
}
```

可以扩展：

```text
UserCreateExecutor
UserUpdateExecutor
UserDeleteExecutor
OrgCreateExecutor
OrgUpdateExecutor
PositionChangeExecutor
PermissionGrantExecutor
ApplicationAccessExecutor
```

审批引擎只关心：

```text
businessType
businessId
```

---

# 14. 启动审批完整流程

接口：

```http
POST /api/approval/process/start
```

请求：

```json
{
  "processCode": "USER_CREATE",
  "businessType": "USER_CREATE",
  "businessId": "REQ10001",
  "title": "新增人员-李四",
  "variables": {
    "deptId": "D100",
    "securityLevel": 2
  }
}
```

处理流程：

```text
① 校验流程
② 获取当前发布版本
③ 创建 wf_process_instance
④ 创建 Flowable ProcessInstance
⑤ 写入流程变量
⑥ Flowable 创建 UserTask
⑦ TaskListener 捕获任务
⑧ AssigneeResolver 计算审批人
⑨ 写 wf_approval_task
⑩ 产生审批通知事件
```

示例：

```java
ProcessInstance instance =
        runtimeService.startProcessInstanceByKey(
            processDefinitionKey,
            businessKey,
            variables
        );
```

---

# 15. 审批接口必须自己封装

不要让前端直接调用 Flowable REST：

```http
POST /flowable/task/complete
```

推荐：

```http
POST /api/approval/tasks/{id}/approve
```

请求：

```json
{
  "comment": "同意"
}
```

示例：

```java
@Transactional
public void approve(ApproveCommand cmd) {

    ApprovalTask task =
        approvalTaskRepository
            .findForUpdate(cmd.taskId());

    permissionService.checkApprover(task);

    taskService.complete(
        task.getFlowableTaskId(),
        variables
    );

    approvalRecordService.recordApprove(...);

    task.finish();
}
```

生产必须考虑：

```text
权限校验
任务状态校验
重复提交校验
乐观锁 / 悲观锁
幂等
事务
审计
```

---

# 16. 审批操作幂等

典型问题：

```text
用户连续点击两次“同意”

或者：

前端超时
↓
自动重试
↓
第一次请求实际上已经执行成功
```

推荐请求头：

```http
X-Request-Id: 8e33...
```

建立：

```text
wf_operation_request
```

字段：

```text
request_id unique
task_id
operator_id
operation
result
created_time
```

逻辑：

```text
requestId 已存在
      ↓
直接返回之前执行结果
```

---

# 17. 流程版本必须不可覆盖

例如 V1：

```text
部门负责人
    ↓
管理员
```

V2：

```text
部门负责人
    ↓
安全管理员
    ↓
管理员
```

正确方式：

```text
旧实例继续运行 V1
新实例使用 V2
```

禁止修改 XML 后覆盖旧版本。

自己的：

```text
wf_process_definition.version
```

要和 Flowable ProcessDefinition version 建立映射。

---

# 18. 流程生命周期

建议：

```text
DRAFT
  ↓
PUBLISHED
  ↓
DISABLED
```

流程发布：

```text
编辑
 ↓
保存 JSON
 ↓
流程校验
 ↓
预览
 ↓
发布
 ↓
编译 BPMN
 ↓
Flowable Deploy
 ↓
version + 1
```

流程模型表：

```text
wf_process_model

id
code
name
model_json
version
status
flowable_definition_id
created_by
updated_by
```

---

# 19. 会签与多实例

会签不要自己重新实现状态机。

Flowable BPMN 本身支持 Multi-Instance。

例如：

```text
王五
李四
赵六
```

BPMN：

```xml
<multiInstanceLoopCharacteristics
    isSequential="false"
    flowable:collection="approverList"
    flowable:elementVariable="approver">
</multiInstanceLoopCharacteristics>
```

UserTask：

```text
assignee = ${approver}
```

DSL 只需要表达：

```json
{
  "approvalMode": "AND"
}
```

编译器负责翻译成 Flowable BPMN。

可以定义：

```text
AND       全部通过
OR        任一通过
PERCENT   达到一定比例
SEQUENCE  顺序审批
```

---

# 20. Reject 与 Return 必须区分

## Reject

通常表示终止流程：

```text
A → B → C

B Reject
   ↓
END
```

## Return

退回历史节点：

```text
A → B → C
    ↑     │
    └─────┘
```

推荐业务接口：

```java
workflowService.returnTask(
    taskId,
    targetNodeId
);
```

不要让业务层直接使用 Flowable ChangeActivityStateBuilder。

---

# 21. 撤回规则

撤回不是 Flowable 的简单“删除实例”。

例如：

```text
申请人刚提交
↓
第一个审批人还未操作
↓
允许撤回
```

但如果：

```text
已经经过多个审批节点
```

是否允许撤回应由 4A 业务规则决定。

接口：

```java
public interface WithdrawPolicy {

    boolean canWithdraw(
        ApprovalInstance instance,
        User operator
    );
}
```

DSL：

```json
{
  "allowWithdraw": true,
  "withdrawPolicy": "BEFORE_FIRST_APPROVAL"
}
```

---

# 22. 审批人为空等边界场景

第一版就需要明确：

```text
1. 审批人为空怎么办？
   - 自动通过
   - 报错
   - 转流程管理员
   - 向上找负责人

2. 审批人就是申请人怎么办？
   - 允许自审
   - 自动跳过
   - 向上级查找

3. 用户有多个岗位怎么办？

4. 部门没有负责人怎么办？

5. 审批期间人员离职怎么办？

6. 组织关系发生变化怎么办？

7. 流程版本升级怎么办？

8. 连续两个节点审批人相同怎么办？
```

推荐在流程启动时保存身份上下文快照：

```json
{
  "applicantId": "U001",
  "applicantOrgId": "D001",
  "applicantPositionId": "P001"
}
```

避免审批过程中人员调岗后流程路线突然改变。

---

# 23. 通知必须事件化

不要在：

```java
approve()
```

里直接：

```java
sendSms();
sendEmail();
sendWebSocket();
```

推荐：

```text
Flowable Task Created
        ↓
WorkflowEvent
        ↓
Outbox
        ↓
MQ
        ↓
Notification Service
        ↓
站内信 / WebSocket / 邮件
```

事件：

```json
{
  "eventType": "TASK_CREATED",
  "processInstanceId": "P100",
  "taskId": "T100",
  "assigneeId": "U100"
}
```

---

# 24. 和 4A 主数据事件统一

4A 本身可能需要向接入应用发布：

```text
USER_CREATED
USER_UPDATED
ORG_UPDATED
POSITION_UPDATED
```

审批事件可以统一：

```text
4A Domain Event
         │
         ├── USER_CREATED
         ├── USER_UPDATED
         ├── ORG_UPDATED
         ├── POSITION_UPDATED
         │
         ├── APPROVAL_STARTED
         ├── TASK_CREATED
         ├── TASK_APPROVED
         ├── APPROVAL_APPROVED
         └── APPROVAL_FINISHED
```

推荐：

```text
Outbox
   ↓
Kafka / RocketMQ
```

这样后续可以统一解决：

- 应用推送
- 应用拉取
- 数据变更通知
- 审批通知
- 数据同步失败重试
- 审计追踪

---

# 25. 审批完成后的事务模型

典型场景：

```text
Flowable 最后一个任务完成
        ↓
用户正式创建
```

不能只认为：

```text
Flowable Process Completed
=
业务完成
```

建议业务状态：

```text
APPROVING
APPROVED
EXECUTING
COMPLETED
EXECUTE_FAILED
```

推荐流程：

```text
Flowable 完成
     ↓
写 PROCESS_APPROVED 事件
     ↓
BusinessExecutor
     ↓
执行业务变更
     ↓
成功 → COMPLETED
失败 → EXECUTE_FAILED
```

失败后：

```text
自动重试
人工补偿
审计告警
```

如果 Flowable 和业务数据位于同一个 Spring Boot / 同一个数据库，可以在简单场景使用本地事务，但仍建议保留业务执行状态。

---

# 26. 审计日志

4A 涉及：

```text
人员新增
人员删除
组织变更
管理员授权
角色授权
权限变更
应用接入
```

建议建立自己的完整审批轨迹：

```text
张三
2026-09-03 10:00
提交“新增应用管理员”

↓

李四
2026-09-03 10:20
审批通过
意见：同意

↓

王五
2026-09-03 11:00
审批通过

↓

系统
2026-09-03 11:00:02
管理员权限授权成功
```

不要把 `ACT_HI_TASKINST` 直接作为最终业务审计页面。

---

# 27. Java 包结构建议

```text
com.xxx.iam.workflow

├── api
│   ├── WorkflowController
│   ├── ApprovalController
│   └── WorkflowModelController
│
├── application
│   ├── WorkflowApplicationService
│   ├── ApprovalApplicationService
│   └── WorkflowModelApplicationService
│
├── domain
│   ├── model
│   │   ├── WorkflowDefinition
│   │   ├── WorkflowInstance
│   │   ├── ApprovalTask
│   │   └── ApprovalRecord
│   │
│   ├── assignee
│   │   ├── AssigneeResolver
│   │   ├── DeptLeaderResolver
│   │   └── RoleResolver
│   │
│   ├── rule
│   │
│   └── executor
│       └── ApprovalBusinessExecutor
│
├── engine
│   ├── WorkflowEngine
│   └── flowable
│       ├── FlowableWorkflowEngine
│       ├── FlowableTaskListener
│       ├── FlowableExecutionListener
│       └── FlowableModelCompiler
│
├── infrastructure
│   ├── repository
│   ├── event
│   └── persistence
│
└── config
    └── FlowableConfiguration
```

---

# 28. Spring Boot 3.x + Flowable 版本建议

如果项目明确使用 Spring Boot 3.x：

```text
Spring Boot 3.x
Java 17 / 21
Flowable 7.2.x
```

Maven：

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter-process</artifactId>
    <version>7.2.0</version>
</dependency>
```

如果当前只做 BPMN 审批，不需要一开始全部引入：

```text
CMMN
DMN
IDM
Form
Content
Event Registry
```

尽量保持最小依赖。

---

# 29. Flowable 是嵌入 4A 还是独立微服务

## 第一阶段推荐

如果当前 4A 是单体或模块化单体：

```text
4A Spring Boot
    │
    └── Embedded Flowable
```

优点：

```text
开发简单
事务简单
部署简单
调用成本低
调试方便
```

## 后续再拆分

当：

```text
IAM
OA
ERP
采购
财务
工单
```

都需要统一审批平台时，再考虑：

```text
IAM ─┐
ERP ─┼─→ workflow-service → Flowable
OA  ─┘
```

不要仅为了“微服务化”在第一版就拆。

---

# 30. 推荐最终架构

```text
                     ┌────────────────┐
                     │ Flow Designer  │
                     └───────┬────────┘
                             │
                         JSON DSL
                             │
                     ┌───────▼────────┐
                     │ Model Compiler │
                     └───────┬────────┘
                             │
                           BPMN
                             │
                     ┌───────▼───────┐
                     │   Flowable    │
                     └───────┬───────┘
                             │
               ┌─────────────┼─────────────┐
               │             │             │
          Task Created   Task Complete  Process End
               │             │             │
               ▼             ▼             ▼
        ApprovalTask     ApprovalRecord   Event
               │                           │
               │                           ▼
               │                    BusinessExecutor
               │                           │
               │                           ▼
               │                       4A 主数据
               │
               ▼
          我的待办 / 已办
```

---

# 31. 推荐生产技术路线总结

```text
Spring Boot 3.x
Java 21
Flowable 7.2.x

Vue3
LogicFlow / AntV X6
       ↓
自定义审批设计器

前端保存：
Workflow JSON DSL

后端：
WorkflowModelCompiler
       ↓
BpmnModel
       ↓
Flowable Deploy

Flowable：
只做流程状态机

4A：
用户 / 组织 / 岗位 / 角色作为唯一身份源

wf_*：
流程定义、实例、待办、审批轨迹、幂等记录

ACT_*：
Flowable 私有运行数据

Outbox + MQ：
审批通知、业务执行、数据变更通知

businessId：
绑定具体 4A 变更申请

ApprovalBusinessExecutor：
审批完成后执行正式组织 / 人员 / 权限变更
```

核心目标：

> **前端不被 BPMN 绑死，业务不被 Flowable 绑死，4A 身份体系不被 Flowable IDM 绑死。**

Flowable 只负责：

```text
节点流转
条件路由
并行
会签
定时器
流程运行状态
流程历史
```

4A 负责：

```text
业务规则
身份关系
审批权限
待办查询
审批轨迹
正式数据变更
业务审计
消息事件
```

---

# 32. 后续实现建议

建议按以下顺序开发：

```text
Phase 1
├── Flowable 基础接入
├── WorkflowService 抽象
├── wf_process_definition
├── wf_process_instance
├── wf_approval_task
└── wf_approval_record

Phase 2
├── 流程 JSON DSL
├── WorkflowModelCompiler
├── 前端流程设计器
└── 流程版本发布

Phase 3
├── AssigneeResolver
├── 部门负责人
├── 角色 / 岗位
├── 应用管理员
└── 组织快照

Phase 4
├── Reject
├── Return
├── Withdraw
├── Transfer
├── Delegate
├── AddSign
└── Multi-Instance

Phase 5
├── ApprovalBusinessExecutor
├── Outbox
├── MQ
├── 业务执行重试
├── 通知
└── 审计

Phase 6
├── 超时审批
├── 催办
├── 流程监控
├── 运维管理
├── 异常补偿
└── 历史归档
```

---

## 后续可以继续补充

在此方案基础上，下一步可以继续设计：

1. `wf_*` 完整 MySQL DDL。
2. Workflow JSON DSL 完整 Schema。
3. DSL → Flowable `BpmnModel` 编译器实现。
4. Spring Boot 3.x + Flowable 7.2.x 完整 Maven / YAML 配置。
5. 人员新增审批完整 Demo。
6. 审批、驳回、退回、撤回、转办、委派、加签 Java 实现。
7. 会签 / 或签实现。
8. 4A `AssigneeResolver` 完整设计。
9. Outbox + RocketMQ / Kafka 可靠消息方案。
10. 流程设计器前后端接口设计。
