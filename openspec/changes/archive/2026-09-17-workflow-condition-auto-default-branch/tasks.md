## 1. 后端：编译期自动补全默认分支

- [x] 1.1 在 `backend/src/main/java/cn/nihility/rbac/workflow/designer/compiler/WorkflowModelCompilerImpl.java` 新增私有方法（如 `augmentWithAutoDefaultBranches(ProcessModelDsl dsl)`），在 `compile()` 方法体最前面（`processModelDslValidator.validate(dsl)` 之前）调用：
  - 按现有出边分组出每个节点的出边列表；
  - 遍历 `ConditionNodeDsl` 类型节点，找出出边中不存在 `condition == null` 的节点；
  - 惰性创建一个共享的自动结束节点（`EndNodeDsl`，id 固定前缀 + 与现有节点 id 冲突时追加后缀直到唯一），只在确实存在需要补全的条件节点时才创建；
  - 为每个需要补全的条件节点追加一条 `EdgeDsl(from=该节点id, to=自动结束节点id, condition=null)`；
  - 需要补全时将 `dsl.getNodes()`/`dsl.getEdges()` 复制为 `ArrayList`，追加节点/边后回填，兼容不可变输入列表。
- [x] 1.2 `backend/src/main/java/cn/nihility/rbac/workflow/designer/compiler/ProcessModelDslValidator.java` 的 `validateConditionNodes()`：移除"条件节点缺少默认分支"报错分支，只保留对已存在的带条件出边（字段/比较符/比较值）的校验。
- [x] 1.3 `backend/src/main/java/cn/nihility/rbac/workflow/designer/service/impl/WorkflowProcessModelServiceImpl.java` 的 `publishV1()`：`.modelJsonSnapshot(model.getModelJson())` 改为 `.modelJsonSnapshot(JacksonUtils.toJson(dsl))`（使用调用 `workflowModelCompiler.compile(dsl)` 之后、已被原地补全的同一个 `dsl` 对象），确保发布快照与实际部署的 Flowable BPMN 结构 1:1 一致。

## 2. 后端：测试

- [x] 2.1 核对 `backend/src/test/java/cn/nihility/rbac/workflow/designer/compiler/ProcessModelDslValidatorTest.java`：该类没有缺少默认分支时报错的用例，更新过时注释；对应拒绝编译用例在 `WorkflowModelCompilerImplTest.java` 中替换（见 2.2）。
- [x] 2.2 更新 `backend/src/test/java/cn/nihility/rbac/workflow/designer/compiler/WorkflowModelCompilerImplTest.java`：
  - 删除/替换原先断言"缺少默认分支时 compile 抛异常"的用例；
  - 新增用例：条件节点未配置默认分支时，`compile()` 后 `dsl` 被自动补入结束节点与兜底边，生成的 `BpmnModel` 中对应 `ExclusiveGateway` 存在 `defaultFlow`；
  - 新增用例：多个条件节点都缺默认分支时，共享同一个自动生成的结束节点，不重复创建；
  - 新增用例：使用者已手动配置默认分支（无论指向哪里）时，不触发自动补全，`dsl` 节点/边数量不变。
  - 补充验证自动节点 id 冲突时递增后缀、不可变列表输入，以及同一 DSL 重复编译不重复补全。
- [x] 2.3 更新 `backend/src/test/java/cn/nihility/rbac/workflow/designer/service/impl/WorkflowProcessModelServiceImplTest.java`：
  - 删除/替换原先依赖"缺少默认分支拒绝发布"的用例；
  - 新增/更新用例：发布成功后 `modelJsonSnapshot` 反序列化出来包含自动补全的结束节点与兜底边（而不再是与草稿 `model_json` 完全一致的原始内容）。
- [x] 2.4 检索 `backend/src/test` 下其余提及"默认分支"的 v1 相关集成测试（`ApprovalProcessServiceImplRouteVariableIntegrationTest`、`ApprovalRequestServiceImplZeroTaskSubmitFailureIntegrationTest`、`ApprovalRequestServiceImplZeroTaskSubmitIntegrationTest`、`WorkflowTaskServiceImplIntegrationTest`），确认它们原本手动配置默认分支的测试数据仍然按"使用者手动配置优先"语义正常工作，不需要改动；如断言的报错行为已被本次改动移除，同步更新。
- [x] 2.5 新增一条端到端集成测试：条件节点只配置了"不满足"的条件分支、未配置默认分支，提交一条不满足该条件的申请后，流程自动路由到自动生成的默认分支、绕过后续审批节点，最终 `tab_approval_request.status` 直接变为已通过。
- [x] 2.6 `dslv2` 相关测试（`WorkflowSimulationServiceTest`、`TaskReturnScopeIntegrationTest`、`ZeroTaskProcessCompletionIntegrationTest`）不属于本次改动范围，确认保持不变、不受影响。

## 3. 前端：设计器交互

- [x] 3.1 `frontend/src/utils/workflowValidation.ts` 的 `validateConditionNodes()`：删除"条件节点缺少默认分支"报错分支；顶部注释（第 5-6 行"条件节点存在兜底默认边"描述）同步更新。
- [x] 3.2 `frontend/src/views/workflow/designer/panels/NodePropertyPanel.vue`：
  - 移除 `hasDefaultBranch` 驱动的 `el-alert` 警告框；
  - 移除每条出边行的"作为默认兜底分支"勾选框（`toggleBranchDefault`/`hasDefaultBranch` 相关逻辑随之精简或整体移除，视是否还有其他用途保留必要部分）；
  - 在原警告框位置换成一句非阻塞说明文案：未手动配置默认分支时，发布时系统会自动补一条"不满足任何条件即自动通过审批"的兜底分支；如需自定义默认分支去向，可手动添加一条不设置条件的出边；
  - 顶部文件注释（3-4 行 CONDITION 节点说明）同步更新，去掉"发布前必须至少保留一条"的表述。
  - 每条出边始终显示可清空字段选择器，选择字段创建条件，清空恢复 `condition=null`；仅有条件时显示比较符/值，无条件边显示“默认分支”标记。
  - 通过 `frontend/tests/conditionBranchEditor.test.mjs` 验证新边添加条件后清空恢复默认分支、切换字段重置值/不适用比较符，以及只读控件状态。
- [x] 3.3 `frontend/src/views/workflow/designer/ProcessDesignerView.vue`：去掉左侧节点面板中"按字段条件分流，需保留一条默认分支"一类的强制性提示文案，改为说明"未配置时系统自动补全默认分支"。
- [x] 3.4 `frontend/src/views/workflow/designer/nodes/ConditionNode.vue`：同步更新头部注释，不涉及功能代码改动。

## 4. OpenSpec 文档同步

- [x] 4.1 实现完成后，调用 `openspec-doc-sync` 按真实 diff/测试结果核对并更新本 change 的 `proposal.md`/`design.md`/`tasks.md`（若实现细节与规划有出入）。
- [x] 4.2 已执行 `openspec-sync-specs` 把本 change 的 delta spec 应用到 `openspec/specs/workflow-process-designer/spec.md`（43 个 spec 全部校验通过）；已按用户指示归档。

## 验证记录（2026-09-17）

- 核对依据：相对 `HEAD` 的已暂存及未暂存代码 diff、新增集成测试与前端测试文件，以及 `backend/build/test-results/test/TEST-*.xml`。
- 后端本次定向回归共 11 个测试类、75 个测试，失败、错误、跳过均为 0；覆盖 v1 校验/编译/发布、自动兜底申请持久化与引擎历史、既有手动默认分支，以及 3 个未修改的 v2 测试类。这是定向回归结果，不代表运行了全仓库测试。
- `ApprovalRequestAutoDefaultIntegrationTest` 验证真实发布快照包含自动节点/边、原始草稿不变、BPMN 默认分支与快照一致；提交不满足条件的申请后，申请和流程实例均为已通过，历史人工任务为 0，且执行过自动结束节点。
- 实现执行者报告：前端 `node --test tests/conditionBranchEditor.test.mjs` 的 3 个交互回归测试通过，`npm run build`（`vue-tsc` + Vite）通过；文档核对已读取对应测试源码确认覆盖范围。
- 权威 spec 同步与归档尚未执行，4.2 保持待用户确认。
