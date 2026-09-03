## Why

仓库已有 `workflow-approval-engine` 在建实现，但从可视化建模到生产审批仍缺少统一的发布绑定、表单版本、复杂节点操作边界、可靠业务执行与运维闭环。两份参考文档存在设计器路线和运行语义差异，需要形成基于本仓库实际代码、可验证并能分阶段交付的完整方案。

## What Changes

- 延续 Vue Flow + 结构化 JSON DSL + 后端 BPMN 编译路线，补齐模型列表、完整画布、属性配置、校验定位、试运行、版本比较、发布审批与业务绑定。
- 分离草稿状态、不可变定义版本和业务生效指针；精确按 definitionId 启动，显式切换旧版本回滚，禁止依赖“挂起最新版本自动回退”的假设。
- 增加表单与身份快照、节点字段权限、真实岗位/应用管理员解析、结构化条件、并行分支、串行/并行会签、抄送、受控自动任务及超时提醒。
- 统一任务认领、同意、投反对票、终止拒绝、退回、转办、委派归还、加签和撤回语义，约束并行跨域退回，补齐幂等、并发和接口权限验证。
- 新增审批结束后的 Outbox + 业务执行器、执行状态、通知投递、异常恢复、对账和归档；第一阶段使用数据库可靠轮询，不强制引入 MQ。
- 为现有 ORG/USER/POSITION/APP 申请增加按绑定版本选择的 `RELIABLE_ASYNC` 执行模式，保留历史 `LEGACY_SYNC` 行为。**BREAKING（仅显式启用新模式的申请）**：最终同意表示审批已通过，正式业务可能仍在执行；失败展示执行失败并重试，不再把已提交的最终审批回滚为待审批。前后端必须同时适配后才能启用。
- 建立从设计器绘制到业务真正生效的真实引擎验收矩阵与生产上线门禁。

## Capabilities

### New Capabilities

- `approval-design-release`: 建模体验、DSL v2、表单版本、校验与试运行、双人发布、业务版本绑定。
- `approval-runtime-safety`: 审批身份、动作语义、会签与并行、权限、幂等和事务一致性。
- `approval-reliable-operations`: Outbox、通知、超时、补偿、对账、运维与留存。

### Modified Capabilities

- `master-data-approval-workflow`: 增加可选择的可靠异步执行契约与双状态展示；保留旧模式的同步执行要求。

## Impact

- 复用 `cn.nihility.rbac.workflow`、`cn.nihility.rbac.approval`、现有动态字段、身份与审计模块；扩展 `frontend/src/views/workflow` 和审批中心。
- 复用 Java 21 / Spring Boot 3.5.16 / Flowable 7.2.0；前端 package.json 已声明 Vue Flow。本次只写文档，不修改依赖或代码。
- 后续增量迁移扩展 `tab_wf_*` 与 `tab_approval_request`；不改已应用迁移、不直接修改引擎私有表；兼容 MySQL 5.7 的业务 SQL。
- 实现时新增页面/按钮须同步 `权限资源.txt`；服务端绑定操作权限，不能只信任客户端 `menu` 请求头。
- 本 change 是生产化增量方案，不取代或勾选 `workflow-approval-engine` 中尚未完成的任务。实施前以当时工作树重新核对两者重叠范围，先完成前置基础能力，再实施本方案；本轮未宣称既有测试通过。
- 不纳入首轮：任意 BPMN/XML 导入执行、用户脚本、包容网关、跨流程调用、跨租户审批、运行中实例自动迁移及跨并行域任意跳转。发布校验必须拒绝这些暂不支持的配置。
