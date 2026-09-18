# Activiti/Flowable 复杂任务类型与生产实现方案

## 第一部分：Activiti/Flowable 审批流程中的复杂任务类型

### 一、会签类任务——多实例（Multi-Instance）

最常见的"复杂任务"是"多人审批"，用Multi-Instance机制实现：

- **并行会签（Parallel MI）**：所有审批人同时收到任务，互不依赖。`<multiInstanceLoopCharacteristics isSequential="false">`，配合内置变量 `nrOfInstances`/`nrOfCompletedInstances`。
- **串行会签（Sequential MI）**：审批人依次审批，前一个完成才生成下一个，`isSequential="true"`。
- **完成条件（completionCondition）**：不需要全部批完，比如"3人会签，2人同意即通过"：

```xml
<multiInstanceLoopCharacteristics isSequential="false">
  <loopCardinality>3</loopCardinality>
  <completionCondition>${nrOfCompletedInstances/nrOfInstances >= 0.66}</completionCondition>
</multiInstanceLoopCharacteristics>
```

- **动态审批人列表**：审批人集合运行时从流程变量取（如按部门查出的leader列表），`loopCardinality` 换成 `collection` 属性指向List类型流程变量。

这类任务在数据库层面对应 `ACT_RU_EXECUTION`——每个并行分支/会签实例生成独立子Execution，挂在同一个 `PROC_INST_ID_` 下，这也是删除时必须先删子Execution再删父Execution的原因。

### 二、分支类——网关（Gateway）

- **并行网关（Parallel Gateway）**：无条件fork，多条分支同时激活（如"财务+HR同时审批"），Join节点等所有分支到达才继续。
- **排他网关（Exclusive Gateway）**：按条件走一条分支，典型如"金额>10万走总经理审批，否则走部门经理"。
- **包容网关（Inclusive Gateway）**：按条件同时激活多条满足条件的分支，适合"根据审批类型同时触发0~N条附加流程"，Join节点要等所有被激活分支完成，实现复杂，能用组合的排他网关替代则尽量别用。

### 三、跨流程复杂任务

- **调用活动（Call Activity）**：把子流程作为独立流程定义调用，如"合同审批"里嵌独立维护的"用印申请"子流程。子流程有自己独立的 `PROC_INST_ID_`，通过 `SUPER_EXEC_` 字段关联回父流程Execution——删父流程如果不管子流程实例会留下孤儿数据。
- **信号/消息事件（Signal/Message Event）**：跨流程实例通信，用 `IntermediateCatchEvent` 监听信号，另一流程用 `ThrowEvent` 广播。能解耦强依赖的多个流程，但容易出现"信号发出但没有监听者接收"的静默失败，建议配套超时兜底（BoundaryEvent + Timer）。

### 四、异步与长时间任务

- **异步延续（Async Continuation）**：耗时节点设为异步执行，变成一条Job（`ACT_RU_JOB`），由独立JobExecutor线程池异步跑，具备失败重试能力（`ACT_RU_TIMER_JOB`/`ACT_RU_SUSPENDED_JOB`/`ACT_RU_DEADLETTER_JOB` 分别对应待触发/挂起/重试耗尽）。
- **人工任务的超时升级**：用边界定时事件（Boundary Timer Event）挂在UserTask上，超时自动触发（自动提醒、自动转交上级、自动通过/驳回）。

### 五、分布式场景下的额外考虑

若审批引擎是多节点集群部署（多实例共享同一数据库）：

- **JobExecutor的分布式锁**：多节点同时抢占到期Job时，Activiti通过数据库乐观锁（`REV_`字段）保证只有一个节点抢到执行权，不需要业务额外开发，但排查"任务偶发不执行/重复执行"要理解这个机制。
- **跨节点的流程实例路由一致性**：异步Job不受会话粘性约束，可能被集群里任意节点的JobExecutor执行，业务代码不能假设"流程从头到尾都在同一台机器上跑"。

---

## 第二部分：SpringBoot3.x 对接 Flowable 自定义画审批流程 —— 生产实现方案

整体架构：前端用bpmn-js流程设计器拖拽画图生成BPMN XML，提交给流程定义管理服务做校验、版本化和存储，再由基于SpringBoot3的Flowable引擎动态部署并驱动运行时执行，运行时分别对接动态审批人解析（关联4A机构人员数据）和动态表单引擎完成完整审批闭环。

```
前端流程设计器(bpmn-js) — 拖拽画图生成BPMN XML
        ↓
流程定义管理服务 — 校验+版本化+存储
        ↓
Flowable引擎(SpringBoot3) — 动态部署+运行时执行
        ↓                              ↓
动态审批人解析                    动态表单引擎
（对接4A机构人员数据）             （字段绑定+条件显示）
```

### 一、版本选型——SpringBoot3必须用Flowable 7.x

SpringBoot3基于Jakarta EE（`javax.*`全部迁移到`jakarta.*`），**Flowable 6.x不兼容**，必须用**Flowable 7.x**：

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.x.x</version>
</dependency>
```

生产必调配置：

```yaml
flowable:
  database-schema-update: false   # 生产环境禁止自动建表/改表，用flowable-db-schema工具单独执行DDL
  async-executor-activate: true   # 开启异步执行器
  history-level: audit            # 按合规需求选full/audit/activity，full会写入过多历史数据影响性能
```

### 二、前端设计器——bpmn-js 定制而不是自己造轮子

- 引入 `bpmn-js` + `bpmn-js-properties-panel`，用户拖拽节点连线，导出标准BPMN2.0 XML，Flowable引擎可直接解析部署。
- **自定义属性面板是核心工作量**：需自己开发扩展Provider，给UserTask加业务配置项——审批人来源（固定人员/角色/部门leader/发起人上级/流程变量动态指定）、是否会签、会签比例、超时时间、是否可加签/转办、抄送人等，落到`flowable:`命名空间扩展属性或`extensionElements`：

```xml
<userTask id="approve1" name="部门审批" flowable:candidateGroups="${deptLeaders}">
  <extensionElements>
    <flowable:formProperty id="reason" type="string" required="true"/>
  </extensionElements>
</userTask>
```

- 建议维护两份数据：一份"设计器专用流程配置JSON"（回显编辑用），一份"转换生成的标准BPMN XML"（引擎用），避免每次编辑都反解析XML。

### 三、动态部署与版本管理——生产环境的关键坑

```java
Deployment deployment = repositoryService.createDeployment()
    .name(processName)
    .addString(processKey + ".bpmn20.xml", bpmnXml)
    .deploy();
```

- **必须先做流程定义合法性校验**再部署：检查孤立节点、网关分支是否闭合、表达式语法，Flowable自带 `ProcessValidator` 可在部署前静态检查。
- **版本兼容问题是最容易踩的坑**：同一`processKey`重新部署自动生成新版本，**运行中的流程实例默认绑定原版本继续走完**，不受新版本影响，是Flowable默认行为，符合"改流程不影响存量审批"的预期，但要明确告知用户避免误解。
- 需要"作废某版本不让再发起"时，用 `repositoryService.suspendProcessDefinitionById()` 挂起而不是删除（删除会级联报约束错误）。
- 建议设计器做"草稿→测试环境验证→正式发布"三态管理，草稿阶段可反复编辑，发布后生成不可变版本，改动只能发布新版本，便于追溯"某张审批单当时走的是哪个版本"。

### 四、动态审批人——对接4A组织人事数据

- **简单场景**：直接用表达式取流程变量，如 `flowable:assignee="${initiator}"` 或 `flowable:candidateGroups="${deptId}"`（`deptId`在流程发起时从4A数据查出塞进流程变量）。
- **复杂场景（按角色查动态人员，如"部门leader"、"上两级领导"）**：不要把复杂查询逻辑写进表达式字符串，用 `TaskListener` 在任务创建时调4A组织人事接口动态设置候选人：

```java
public class DeptLeaderTaskListener implements TaskListener {
    public void notify(DelegateTask task) {
        String deptId = (String) task.getVariable("deptId");
        List<String> leaders = orgService.getDeptLeaders(deptId); // 调用4A组织人事接口
        task.addCandidateUsers(leaders);
    }
}
```

- 组织架构变更（离职、调岗）不会自动影响已生成任务，需后台定时任务扫描"候选人已离职/调岗"的未完成任务自动转交继任者，可复用4A数据同步的变更通知机制（4A推送人员变更事件，审批系统订阅后触发任务重新分配）。

### 五、安全：表达式注入是最容易被忽视的风险

Flowable大量使用UEL表达式（`${xxx}`），若用户能在设计器里自由输入表达式字符串，存在代码执行注入风险。生产环境务必：

- **限制表达式来源**：设计器里"审批人""条件"配置做成下拉选择+参数填空的结构化UI，后端根据结构化配置拼装表达式，用户接触不到原始表达式语法。
- 如需开放高级模式的表达式自定义能力，配置**表达式沙箱**，用自定义 `FlowableExpressionManager`/`FunctionDelegate` 限制可调用方法白名单，避免暴露反射、文件、网络等危险API。
- 部署接口做**发布权限控制**，普通业务人员只该有"编辑草稿+提交测试"权限，正式发布走审核。

### 六、动态表单——和流程节点绑定

- 表单设计器产出表单schema（字段类型、校验规则、条件显示逻辑），存表单库，通过`formKey`关联UserTask节点。
- 每个节点可绑定不同表单（发起表单 vs 各级审批表单），运行时根据当前TaskDefinitionKey查出对应schema渲染。
- 完整表单内容不建议直接存 `ACT_RU_VARIABLE`（该表适合存流程流转需要的关键字段，如金额、审批人ID），应单独建业务表存储，通过`PROC_INST_ID_`关联，避免变量表塞入大量JSON影响性能。

### 七、生产环境配置要点

- **异步执行器线程池**：`AsyncExecutor`核心线程数、队列大小按并发量压测调整，避免定时任务/超时升级堆积。
- **集群部署**：多实例共享同一数据库时，JobExecutor靠数据库乐观锁做分布式协调，不需额外加分布式锁组件，但要确保所有节点连同一库、时钟同步。
- **历史数据清理**：`history-level=audit`情况下历史表仍会快速增长，建议定时归档任务把超期（如1年）的`ACT_HI_*`数据转存归档库或清理。
- **监控告警**：监控僵尸任务（超期未处理）、`ACT_RU_DEADLETTER_JOB`（重试耗尽需人工介入）、流程实例平均耗时，直接反映审批系统健康度。

**总结**：bpmn-js负责画图产出标准BPMN + 结构化业务配置存自己的库，Flowable 7.x只管标准的部署与运行时执行，审批人和表单这类业务定制通过TaskListener/表单引擎在应用层实现而不是硬塞进BPMN表达式，既保留引擎稳定性和标准兼容性，又能灵活支撑业务自定义需求。

---

## 第三部分：业务系统与Flowable运行时对接

整体链路：业务模块发起审批时生成businessKey并提交表单，调用流程引擎启动流程实例并与该businessKey绑定，进入任务处理层提供待办查询与审批、驳回、转办、加签等操作接口，任务状态变化通过流程监听器同步，最终触发业务状态回写更新业务表并发送通知，形成从发起到结束的完整闭环。

```
业务模块发起审批 — 生成businessKey+提交表单
        ↓
启动流程实例 — ProcessInstance绑定businessKey
        ↓
任务处理层 — 待办查询+审批/驳回/转办/加签
        ↓                              ↓
流程监听器                        业务状态回写
（同步流程与任务状态）             （更新业务表+发通知）
```

### 一、businessKey——业务系统和流程实例的唯一纽带

流程引擎不应该知道业务细节，业务表也不应该直接依赖流程内部表结构，两者靠`businessKey`绑定：

```java
ProcessInstance pi = runtimeService.startProcessInstanceByKey(
    "contract_approval",           // 流程定义Key（对应设计器发布的流程）
    contractId,                    // businessKey，一般用业务单据ID，保证唯一
    variables                      // 启动变量：金额、部门ID、发起人等，供流程内条件判断/审批人解析用
);
contractMapper.updateProcessInstanceId(contractId, pi.getId());
```

- **业务表要存一个`process_instance_id`字段**，之后对流程状态的查询都通过这个ID查Flowable的`ACT_RU_TASK`/`ACT_HI_*`，而不是业务表冗余维护一份状态机，避免两边状态不一致。
- 用`businessKey`而不是完全依赖`processInstanceId`反查业务，很多场景（"这个合同当前有没有在跑的审批流程"）用`businessKey`反查更直接：`runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(contractId)`。
- 一个业务单据同一时间只应该有一个进行中的流程实例，发起前要检查是否已存在未结束实例；"审批中的单据修改后重新提交"是"作废旧实例开新实例"还是"驳回重提复用同一实例"，要在启动前就校验清楚，不要指望引擎兜底。

### 二、启动流程的事务边界

启动流程实例、写业务表要在同一个本地事务里：

```java
@Transactional
public void submitContract(Contract contract) {
    contract.setStatus("APPROVING");
    contractMapper.insert(contract);   // 先落业务数据

    ProcessInstance pi = runtimeService.startProcessInstanceByKey(
        "contract_approval", contract.getId(), buildVariables(contract));

    contractMapper.updateProcessInstanceId(contract.getId(), pi.getId());
}
```

`flowable-spring-boot-starter`下RuntimeService操作默认参与Spring事务管理，跟业务DB操作用同一个事务管理器，直接用普通`@Transactional`即可保证原子性——这也是不建议把Flowable库和业务库分开部署在不同数据库实例的原因，分开会导致事务一致性没法保证，除非引入分布式事务框架（成本明显更高，一般审批场景不值得）。

### 三、任务处理层——面向前端的API设计

```
GET  /api/tasks/todo              查询当前用户待办（candidateUser/candidateGroup/assignee三种维度都要覆盖）
GET  /api/tasks/{taskId}          任务详情（含表单schema+业务数据）
POST /api/tasks/{taskId}/claim    认领（候选任务变成个人任务）
POST /api/tasks/{taskId}/complete 审批通过/驳回（用一个变量区分approve/reject走不同网关分支）
POST /api/tasks/{taskId}/transfer 转办
POST /api/tasks/{taskId}/delegate 加签/委派
GET  /api/tasks/history           已办查询
```

- **待办查询要合并三种来源**：`taskCandidateUser`+`taskCandidateGroup`（角色列表从4A组织人事查）+`taskAssignee`。生产环境通常把查询结果做二级索引/宽表（定时或事件驱动同步一份"待办宽表"到业务库），因为`ACT_RU_TASK`原生表不适合承载复杂的业务排序/搜索。
- **complete接口要传业务批注/审批意见**，这些不属于流程变量（流程变量只放"影响流转逻辑"的字段），审批意见单独存业务表（如`approval_record`表：taskId、operator、opinion、operateTime），方便统计报表和复杂查询。
- **驳回不要用简单的"往回跳转"**，用排他网关+变量判断实现，避免用`moveExecutionsToSingleActivityId`这类硬跳转API破坏Token正常流转逻辑（容易导致网关计数错乱、后续状态异常）。

### 四、流程监听器——状态同步的核心

用`TaskListener`（任务级）和`ExecutionListener`（流程/网关级）把流程内部状态同步给业务系统：

```java
public class ApprovalCompleteListener implements ExecutionListener {
    public void notify(DelegateExecution execution) {
        String contractId = execution.getProcessInstanceBusinessKey();
        Boolean approved = (Boolean) execution.getVariable("approved");
        contractService.updateStatus(contractId, approved ? "APPROVED" : "REJECTED");
        notifyService.send(contractId, approved);
    }
}
```

挂在流程结束节点（EndEvent）的`flowable:executionListener`上，流程走到终点自动触发。

- **幂等性是最容易出问题的地方**：监听器执行失败重试、Job重复执行都可能导致业务方法被调用两次，业务方法本身要做成幂等的（如`UPDATE contract SET status='APPROVED' WHERE id=? AND status != 'APPROVED'`，用状态判断替代无条件更新）。
- **监听器里不要做重量级同步调用**（调外部系统、发短信等可能失败/耗时的操作），监听器在流程引擎事务里执行，出异常会导致流程流转本身回滚。推荐监听器只发一条领域事件（Spring事件/MQ消息），业务状态回写和通知发送放到事件消费端异步处理，和流程引擎事务解耦：

```java
public void notify(DelegateExecution execution) {
    applicationEventPublisher.publishEvent(
        new ApprovalCompletedEvent(execution.getProcessInstanceBusinessKey(), 
                                     execution.getVariable("approved")));
}
```

### 五、流程结束后的业务回写——防止事件丢失

- **不要只依赖事件驱动**：监听器同步写一条"待处理的状态变更记录"到业务库（同一个本地事务，保证不丢），事件消费端处理完成后标记已处理；同时跑定时兜底任务，扫描"流程已结束但业务状态还是待处理"的记录重新触发同步。
- 也可以直接用**流程结束事件的数据库轮询**：定时扫描`ACT_HI_PROCINST`里`END_TIME_`不为空但业务表状态还是"审批中"的记录，主动同步，适合对实时性要求不高的场景，实现更简单，缺点是有延迟。

### 六、权限校验——防止越权操作

任务API必须校验当前登录用户对该task确实有权限：

```java
public void completeTask(String taskId, String userId, Map<String, Object> vars) {
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
    if (task == null) throw new BizException("任务不存在或已处理");

    boolean isAssignee = userId.equals(task.getAssignee());
    boolean isCandidate = !taskService.createTaskQuery()
        .taskId(taskId).taskCandidateUser(userId).list().isEmpty();
    boolean isGroupCandidate = checkGroupCandidate(taskId, userId); // 结合4A角色数据判断

    if (!isAssignee && !isCandidate && !isGroupCandidate) {
        throw new BizException("无权操作该任务");
    }
    taskService.complete(taskId, vars);
}
```

- **候选组任务在完成前必须先claim**，避免多个候选人同时点"通过"导致`complete()`报错；complete接口内部可以封装成"未认领则先认领再完成"，减少前端复杂度。
- 管理员代为处理（离职人员任务需管理员强制转交）要走单独的高权限接口，不能复用普通用户的complete接口，且必须记录操作日志。

### 七、会签场景的进度展示与部分操作

多实例会签需要专门查询进度：

```java
List<Task> subTasks = taskService.createTaskQuery()
    .processInstanceId(processInstanceId)
    .taskDefinitionKey("approve1")  // 多实例节点的活动ID
    .list();

Execution miExec = runtimeService.createExecutionQuery()
    .processInstanceId(processInstanceId)
    .activityId("approve1")
    .singleResult();
Integer total = (Integer) runtimeService.getVariable(miExec.getId(), "nrOfInstances");
Integer done = (Integer) runtimeService.getVariable(miExec.getId(), "nrOfCompletedInstances");
```

**加签**用专门API而不是手动改变量：

```java
taskService.addUserIdentityLink(taskId, newUserId, IdentityLinkType.CANDIDATE);
runtimeService.addMultiInstanceExecution(activityId, processInstanceId, variables);
```

**减签**同理用`deleteMultiInstanceExecution()`，不要直接操作`ACT_RU_EXECUTION`表——多实例的内部计数字段（`nrOfInstances`等）散落在多处维护，手工SQL改极容易把计数搞乱导致完成条件永远算不对。

### 八、灰度与测试策略

- **测试环境用完整数据链路跑通**：拿真实的业务数据结构、真实的4A组织人事测试账号，把发起到结束的完整链路走一遍，包括驳回、加签、超时升级这些分支路径都要覆盖，不能只测"一路通过"这条主线。
- **小范围灰度**：流程改动影响面大（如审批人规则变化）时，`processDefinitionKey`可以按业务线拆分成多个而不是全公司共用一个key，先小范围启用新版本验证，再推广，比"一key全量切换"风险小。
- **回滚预案**：运行中实例不受新版本影响，若需要临时"回滚到发起用老版本"，用`suspendProcessDefinitionById()`挂起新版本，Flowable会自动用挂起前的最新可用版本处理新发起请求（前提是老版本没被删除/挂起）。

### 九、监控指标补充

- **平均审批时长/超时率**：按流程定义、按节点统计，发现哪个审批环节经常卡壳（如某角色候选人经常没人及时处理）。
- **事件消费延迟**：若用"监听器发事件+异步消费"模式，监控消息队列堆积情况，堆积说明消费端处理能力跟不上审批发起量。
- **流程实例与业务表状态一致性巡检**：定时比对`ACT_HI_PROCINST`的结束状态和业务表状态，不一致则报警，是发现"事件丢失导致业务状态没回写"问题的最后一道保险，建议和第五节的兜底同步任务一起上。

**总结**：`businessKey`是业务与流程的唯一纽带，启动流程与业务落库要在同一事务保证原子性，监听器只做"发事件"而不做重量级同步操作，业务状态回写要有事件驱动+定时兜底双保险，任务操作接口必须做权限校验且不能绕过Service API直接碰`ACT_RU_*`表——这几条是运行时对接阶段最容易出问题、也最值得在设计阶段就定下规范的地方。
