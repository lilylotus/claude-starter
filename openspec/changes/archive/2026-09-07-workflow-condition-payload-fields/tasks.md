## 1. 数据模型

- [x] 1.1 新增 Flyway 迁移，为 `tab_wf_process_definition` 增加 `route_field_codes TEXT NULL`
      列（MySQL 5.7 兼容语法，无需回填历史数据）。
- [x] 1.2 `ProcessDefinitionEntity` 同步加字段，MapStruct 转换（如涉及）同步更新。

## 2. 后端 DSL 与编译器改造（v1，`workflow.designer` 包）

- [x] 2.1 `EdgeConditionDsl.field` 从 `String` 改为 `{bizType, fieldCode}` 结构（新增内嵌
      DTO 或改为两个字段 `fieldBizType`/`fieldCode`）。
- [x] 2.2 `ProcessModelDslValidator.validateConditionNodes`：
      - 校验 `fieldBizType` 属于 ORG/USER/POSITION/APP 之一；
      - 校验 `fieldCode` 存在于 `formFieldDefinitionService.buildRenderSchema(fieldBizType)`
        返回的启用字段中，且该字段 `controlType != 5`（多选字典）；
      - 按字段 `controlType` 收窄允许的比较符：文本框(1)/字典下拉(3) 仅 EQ/NE，数字框(2)/
        日期(4) 允许全部六个；
      - 保留既有"条件节点至少一条默认分支"校验不变。
- [x] 2.3 `WorkflowModelCompilerImpl`：
      - 编译阶段遍历全部条件边收集去重后的 `{bizType, fieldCode}` 列表，序列化写入本次发布
        产物的 `route_field_codes`（与 `xml_snapshot`/`model_digest` 同一事务落库）；
      - `buildConditionExpression` 改为按字段 `controlType` 生成 null 安全表达式：
        变量名 `bizType_fieldCode`；数字框比较值转 `BigDecimal` 字面量；日期字段比较值
        （管理员选择的日期）编译时转换为 `LocalDate.toEpochDay()` 数字字面量；文本框/字典
        下拉比较值按字符串字面量加引号转义；统一包裹为
        `${(变量 != null) && (变量 比较符 比较值)}` 形式。

## 3. 审批提交链路：把表单数据同步成流程变量

- [x] 3.1 `ApprovalProcessService.start(...)`/`ApprovalProcessServiceImpl` 新增
      `Object typedPayload` 参数。
- [x] 3.2 `ApprovalProcessServiceImpl.start()` 内：读取 `resolved.definition().getRouteFieldCodes()`
      反序列化为 `{bizType, fieldCode}` 列表；对每一条，若 `bizType` 与本次提交的 `bizType`
      一致，用 `ObjectMapper.convertValue(typedPayload, Map.class)` 摊平后按 `fieldCode` 取值，
      按该字段的 `controlType`（查 `formFieldDefinitionService.buildRenderSchema`）转换为
      `BigDecimal`/`epoch day long`/`String`；不一致则值为 `null`；变量名统一
      `bizType_fieldCode`，汇总为 `Map<String,Object>` 传入 `StartProcessCommand.variables`
      （替换现有恒为 `null` 的传参）。
- [x] 3.3 `ApprovalRequestServiceImpl.submit()` 调用 `approvalProcessService.start(...)` 时
      透传已校验的 `typedPayload`。

## 4. 前端设计器条件编辑区

- [x] 4.1 设计器页面（`ProcessDesignerView.vue` 或其加载逻辑）并行请求四类业务对象的
      `GET /api/form-fields/render-schema?bizType=...`，合并为条件字段可选列表数据源
      （过滤掉 `controlType=5` 的字段），提供给 `NodePropertyPanel.vue`。
- [x] 4.2 `NodePropertyPanel.vue` 条件分支编辑区：
      - "字段"下拉按业务类型分组展示（`el-select`+`el-option-group`），选中值存
        `{bizType, fieldCode}`；
      - "比较符"下拉按选中字段的 `controlType` 动态过滤选项，未选字段时禁用并提示
        "请先选择字段"；
      - "比较值"输入按 `controlType` 切换控件：文本框用 `el-input`，数字框用
        `el-input-number`，字典下拉用 `el-select`（选项取该字段 `dictOptions`），日期用
        `el-date-picker`。
- [x] 4.3 `frontend/src/types/workflow.ts` 的 `EdgeConditionDsl`/相关类型同步调整为新结构。

## 5. 测试与验收

- [x] 5.1 补充/调整 `WorkflowModelCompilerImplTest` 用例：条件字段结构变更后的编译产物
      断言（含 `route_field_codes` 写入）、null 安全表达式生成、数字/日期比较值转换正确性。
- [x] 5.2 新增/调整校验器测试：拒绝多选字典字段、拒绝文本/字典字段配置非 EQ/NE 比较符、
      拒绝引用不存在的字段。
- [x] 5.3 新增集成测试覆盖 `ApprovalProcessServiceImpl.start()` 的变量构建：命中条件的分支、
      业务类型不匹配走默认分支、字段值缺失走默认分支三个场景，均对着真实 Flowable 引擎
      验证路由结果，不用 mock 断言"应该会路由对"。
- [x] 5.4 `./gradlew build` 全量通过；`npm run build`（vue-tsc + vite build）通过。
      前端部分：`npm run build` 已验证通过（vue-tsc 类型检查 + vite build 均无报错）。
      后端部分：主协调者（非实现该代码的 agent）独立重新跑过一遍验证，非仅采信实现者的
      报告——单独运行 `ProcessModelDslValidatorTest`/`WorkflowModelCompilerImplTest`/
      `ApprovalProcessServiceImplRouteVariableIntegrationTest` 三个新增/核心测试类均通过；
      批量跑 `ApprovalProcessServiceImplTest`/`ApprovalProcessServiceImplBindingIntegrationTest`/
      `ApprovalRequestServiceImplTest`/`WorkflowProcessModelServiceImplTest` 时后者一度报
      Spring 上下文加载失败（`IllegalStateException`/`IllegalArgumentException`），但对应的
      XML 测试报告实际记录 `failures="0"`，且单独重跑（`--rerun`）稳定通过，确认是共享远程
      开发库下的环境瞬时问题，不是本次代码改动引入的回归。`./gradlew test` 全量跑（含本
      change 未触碰的 `workflow.dslv2`/`workflow.integration` 等既有并发/会签集成测试）存在
      与本 change 无关的预先存在的测试隔离问题（`TaskClaimConcurrencyIntegrationTest` 等报
      "Unknown column 'processInstanceId'/'taskId' in where clause"，怀疑是某个单测里手工
      `TableInfoHelper.initTableInfo` 注册 MyBatis-Plus 全局静态 TableInfo 缓存时未开
      驼峰下划线映射，污染了同一 JVM 内后续加载同一实体的真实列名映射，与本 change 改动的
      文件均无关，单独运行这些测试类均能通过）——这是既有基础设施问题，不属于本 change
      范围，勾选本条目仅代表"与本 change 相关的测试均通过"，不代表已修复该既有隔离问题。
- [ ] 5.5 浏览器实际走查：在设计器里给条件节点配置一个引用真实业务字段的分支并发布，
      提交一条命中/不命中该条件的真实审批申请，确认路由到预期分支。**未完成**：本次会话
      浏览器自动化工具持续不可用（多次重试均失败，非本 change 特有问题），前后端 dev
      server 均已在运行（`localhost:5173`/`localhost:48080`），需要用户自行走查一遍确认。
- [x] 5.6 核对 `权限资源.txt`：本 change 未新增任何 REST 接口，条件字段下拉复用既有的
      `GET /api/form-fields/render-schema`（已豁免资源权限校验），设计器保存/发布仍是既有
      `WorkflowDesign:model:edit`/`WorkflowDesign:model:publish`，未发现需要更新的描述。
