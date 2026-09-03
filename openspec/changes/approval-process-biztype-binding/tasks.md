## 1. 数据库迁移

- [ ] 1.1 新增 `backend/src/main/resources/db/migration/V11__add_approval_switch_process_code.sql`：
      `tab_approval_switch` 新增可空列 `process_code VARCHAR(64)`；`UPDATE` 现有 ORG/USER/
      POSITION/APP 四条记录，把 `process_code` 回填为预置流程编码字面量
      `'MASTER_DATA_APPROVAL'`（design.md Migration Plan / Decision 4），保证迁移后行为
      与迁移前一致。不使用 MySQL 5.7 不兼容写法。

## 2. 后端：审批开关绑定流程

- [ ] 2.1 `ApprovalSwitchEntity` 新增 `processCode` 字段。
- [ ] 2.2 `ApprovalSwitchVO`/`ApprovalSwitchUpdateRequest`（及对应 MapStruct 转换）新增
      `processCode` 字段；`ApprovalSwitchUpdateRequest.processCode` 在 `enabled=true` 时
      通过 `@NotBlank`（或等价的自定义校验逻辑）要求非空（design.md Decision 2）。
- [ ] 2.3 `ApprovalSwitchService`/`ApprovalSwitchServiceImpl.update()` 方法签名扩展为接收
      `processCode`：`enabled=true` 时校验 `processCode` 对应的 `tab_wf_process_model`
      必须存在且 `status=PUBLISHED`（复用 `ProcessModelMapper`，不满足时抛
      `BusinessException`，开关与绑定值均不落库）；`enabled=false` 时不做该项校验直接
      保存（design.md Decision 2）。操作日志快照（`snapshot()`）里追加"绑定流程"字段。
- [ ] 2.4 `ApprovalSwitchController` 对应接口的 springdoc-openapi 注解按新字段更新描述。

## 3. 后端：提交审批时按绑定流程路由

- [ ] 3.1 `ApprovalSwitchService` 新增查询方法（如
      `resolveProcessCode(bizType)`），返回该 `bizType` 当前绑定的 `processCode`；
      `bizType` 不合法或记录不存在时的异常行为与既有 `getExisting()` 保持一致
      （design.md Decision 5）。
- [ ] 3.2 `ApprovalProcessServiceImpl` 移除写死的 `PROCESS_CODE` 常量，注入
      `ApprovalSwitchService`，`start()` 改为调用 3.1 新增的方法按 `bizType` 动态解析
      `processCode` 后再传给 `WorkflowService.start()`；流程未发布等失败场景直接透传
      `WorkflowService.start()` 已有的 `BusinessException`，不做静默兜底（design.md
      Decision 3、5）。

## 4. 前端：审批设置页面绑定流程模型

- [ ] 4.1 `frontend/src/types/approvalSwitch.ts` 的 `ApprovalSwitchRow`/
      `ApprovalSwitchUpdateRequest` 新增 `processCode` 字段，字段命名和后端
      `ApprovalSwitchVO`/`ApprovalSwitchUpdateRequest` 对齐。
- [ ] 4.2 `ApprovalSettingsView.vue` 每行新增流程模型下拉（`el-select`），数据源为
      `workflowApi.listProcessModels()` 按 `status === 'PUBLISHED'` 客户端过滤后的选项；
      当前绑定的 `processCode` 若不在过滤后的列表里（已下线/从未发布），下拉仍需展示该
      值本身（禁用态选项 + 告警样式），提示管理员"当前绑定流程已下线，请重新绑定"
      （design.md Decision 6 / Risks）。
- [ ] 4.3 调整 `handleToggle`/开关交互：开启审批开关前必须已选定一个 `processCode`，
      未选定时禁止开启并给出提示（与 2.2/2.3 后端校验的语义保持一致，前端做提前拦截，
      避免无意义的失败请求）；更换绑定流程（不改变开关状态）走独立的保存动作。

## 5. 测试

- [ ] 5.1 `ApprovalSwitchServiceImplTest` 补充：开启开关时绑定流程未发布/未指定
      `processCode` 均被拒绝；关闭开关时不校验绑定流程可用性；成功路径落库
      `processCode`。
- [ ] 5.2 `ApprovalProcessServiceImplTest` 补充：`start()` 按 `bizType` 使用其绑定的
      `processCode` 调用 `WorkflowService.start()`（不同 `bizType` 绑定不同 `processCode`
      时分别验证）；绑定流程未发布时透传 `WorkflowService` 的异常。
- [ ] 5.3 集成测试：完整走一遍"新建并发布一个非预置流程模型 → 审批设置里为某
      `bizType` 绑定该流程并开启开关 → 提交该 `bizType` 的申请 → 审批通过 → 业务记录
      生效"全链路（对照 `openspec/changes/workflow-approval-engine/specs/
      master-data-approval-workflow/spec.md` 既有"审批通过后执行既有业务逻辑"需求的
      验证方式）。
- [ ] 5.4 回归测试：确认迁移后（`process_code` 回填为预置流程）四个业务模块的既有审批
      流程行为不受影响，`./gradlew build` 全量通过。

## 6. 文档同步

- [ ] 6.1 编码完成后运行 `openspec-doc-sync` 对齐本 change 的
      `tasks.md`/`design.md`/`proposal.md` 与实际实现结果。
