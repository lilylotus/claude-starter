## Context

流程设计器"条件"节点分支（`EdgeConditionDsl{field, operator, value}`）编译为 Flowable UEL 网关表达式 `${field 比较符 value}`，`field` 引用的是 Flowable **流程启动变量**。`WorkflowService.start`/`FlowableWorkflowService.start` 早已支持通过 `StartProcessCommand.variables` 传入启动变量并透传给 `runtimeService.startProcessInstanceById(definitionId, variables)`（`FlowableWorkflowService.java:168-172`），管道本身是通的。但唯一的生产调用方 `ApprovalProcessServiceImpl.start()` 始终把 `variables` 传 `null`，且提交审批的入口 `ApprovalRequestServiceImpl.submit()` 也从未把已校验的 `typedPayload` 传给下游——条件分支因此从未在真实审批场景里生效过。前端"字段"输入框是自由文本，也没有校验字段是否真实存在。

组织/用户/任职/应用四类业务对象的动态自定义字段由 `FormFieldDefinitionService` 统一管理，`GET /api/form-fields/render-schema?bizType=X`（`FormFieldDefinitionController.renderSchema`）已经是审批详情页复用的、豁免资源权限校验的字段元数据接口，返回 `FormFieldRenderItemVO`（`fieldCode`/`fieldName`/`controlType`/`dictOptions` 等），且**包含承重字段**（组织/用户/应用的 name/code、任职的 position_type），比 `listActiveByBizType`（排除承重字段）更完整，适合作为条件字段选择的数据源。

## Goals / Non-Goals

**Goals:**

1. 条件分支的"字段"从表单字段定义里选，不能手打；发布时校验字段真实存在于对应业务类型的启用字段定义中。
2. 审批提交发起流程实例时，把该流程定义实际引用到的字段从 `request_payload` 取值同步为 Flowable 流程变量，条件分支运行时真正按提交内容路由。
3. 同一流程模型被多个业务类型共用（如默认的 `MASTER_DATA_APPROVAL`）时，条件里引用了"这次提交的业务类型根本没有"的字段，视为条件不满足、走默认分支，不抛异常中断流程。
4. 按字段控件类型收窄可用比较符与比较值输入方式（数字/日期支持全部六种比较符，文本/字典下拉仅等于/不等于；字典下拉字段的比较值也从该字典选项里选）。

**Non-Goals:**

- 不涉及 `dslv2` 包下的 `ConditionAstDsl`/`ProcessModelDslV2Validator`（DSL v2 至今没有任何前端页面能编辑，属于另一条尚未接入 UI 的能力线，其类似的字段白名单校验缺口留给该能力线自己的后续 change）。
- 不支持多选字典（`MULTI_DICT`）字段作为条件字段——无法做单值比较，下拉里直接不出现这类字段，不做"选中多个值中任一个"这类扩展语义。
- 不自动迁移历史已发布的草稿/版本：旧版本条件分支的 `field` 是自由文本，无法确定性地映射到某个真实字段定义；历史行为保持"变量不存在时的现有报错/异常"或本 change 里"取不到值按 null 处理走默认分支"的新规则自然覆盖（因为旧字段名对应的变量本来就永远是 null），但不强行改写已发布版本的 `model_json_snapshot`。管理员需要在设计器里重新编辑草稿、用新的字段下拉重新配置条件、重新发布，旧版本不受影响（`workflow-process-designer` 既有"启停模型与编辑草稿 SHALL 不影响已有实例"约束保持不变）。
- 不新增独立的字段选择/条件配置可复用组件跨模块共享——本次改动范围限定在 `NodePropertyPanel.vue` 内部，暂不评估其他模块是否需要类似能力。

## Decisions

### 1. 路由字段清单：发布时从条件分支提取，落库到流程定义

新增迁移为 `tab_wf_process_definition` 加一列 `route_field_codes`（`TEXT`，JSON 数组，元素形如 `{"bizType":"ORG","fieldCode":"riskLevel"}`），允许为空。`WorkflowModelCompilerImpl` 编译阶段遍历所有条件边收集去重后的 `{bizType, fieldCode}` 组合，随发布产物一起写入该列（与 `xml_snapshot`/`model_digest` 同一事务落库，沿用现有发布事务边界，不新增额外写入点）。运行时启动流程只需读这一列，不需要重新解析整个 `model_json_snapshot`，避免每次提交审批都做一次 JSON 图遍历。

**备选方案**：运行时直接解析 `model_json_snapshot` 现算路由字段——否决，审批提交是相对高频的写路径，没必要在热路径上重复做发布时就能算好的事。

### 2. 条件字段引用结构：`{bizType, fieldCode}`，Flowable 变量按 `bizType_fieldCode` 命名空间化

`EdgeConditionDsl.field` 从裸字符串改为 `{bizType, fieldCode}` 结构（前端选择时从下拉里选，值即为这个组合）。运行时启动流程实例时，`ApprovalProcessServiceImpl.start()` 读取 `resolved.definition()` 的 `route_field_codes`：
- 若某条目的 `bizType` 与本次提交的 `bizType` 一致：从 `typedPayload`（`ApprovalRequestServiceImpl.submit()` 已经校验过的类型化请求对象，经 `ObjectMapper.convertValue(typedPayload, Map.class)` 摊平后按 `fieldCode` 取值）取出原始值，按字段的 `controlType`（查 `formFieldDefinitionService.buildRenderSchema(bizType)`，与发布时/前端下拉同一数据源）转换后放入变量；
- 若不一致：变量值为 `null`。

Flowable 变量名统一为 `bizType + "_" + fieldCode`（如 `ORG_riskLevel`），避免不同业务类型恰好用了相同 `fieldCode`（如两个 bizType 都有一个叫 `remark` 的自定义字段）时互相覆盖或语义混淆——命名空间化后，条件编译出的 UEL 表达式引用的是这个确定的变量名，不会有歧义。

**备选方案**：不加命名空间、直接用 `fieldCode` 作变量名——否决，同一流程模型被多个业务类型共用是系统现状（默认绑定就是 4 个业务类型共用同一个模型），字段码收窄空间不大，命名冲突是真实风险，命名空间化的额外复杂度可以忽略不计。

### 3. 字段值类型转换：数字用 `BigDecimal`，日期转 epoch day（`long`），字符串/字典保持字符串

- 控件类型=数字框（`controlType=2`）：转换为 `java.math.BigDecimal`（避免浮点比较误差，与已有 `dslv2.ConditionAstEvaluator` 的既定做法一致）。
- 控件类型=日期（`controlType=4`）：提交值（ISO 日期字符串）解析为 `java.time.LocalDate` 后取 `toEpochDay()`（`long`）。UEL/JUEL 没有日期字面量语法，直接把 `${dateVar > '2026-01-01'}` 这样写会变成 `LocalDate`（或字符串）跟字符串比较，`>`/`<` 在 JUEL 里对非同类型操作数行为不可靠；统一转成可比较的整数消解这个问题。比较值同样在**编译时**（管理员在设计器里选的日期）转换成 epoch day 数字字面量嵌入表达式，两边始终是同类型数值比较。
- 控件类型=文本框（`controlType=1`）/字典下拉（`controlType=3`）：保持字符串，比较值在编译时按字符串字面量加单引号转义后嵌入表达式（沿用 v1 现有 `formatValue` 的字符串分支逻辑，只是来源从自由输入改为下拉选中的字典选项值）。
- 控件类型=多选字典下拉（`controlType=5`）：不允许选为条件字段（校验器直接拒绝），下拉里也不展示。

### 4. 比较符按字段控件类型收窄

延续现有 `ALLOWED_OPERATORS = {EQ,NE,GT,GTE,LT,LTE}` 六个不变，但按选中字段的 `controlType` 进一步限制哪些可用：
- 文本框(1)/字典下拉(3)：仅 `EQ`/`NE`。
- 数字框(2)/日期(4)：全部六个。

前端下拉在字段选中后动态过滤比较符选项；后端 `ProcessModelDslValidator.validateConditionNodes` 同步做这层校验（不能只依赖前端过滤，绕过画布直传 DSL 的场景需要后端兜底拒绝，这是既有"系统 SHALL NOT 允许自由表达式"精神的延伸）。

### 5. 条件表达式统一 null 安全包裹，缺失字段一律判定为不满足

编译出的表达式统一形如 `${(ORG_riskLevel != null) && (ORG_riskLevel == 'HIGH')}`（字符串/字典）或 `${(ORG_amount != null) && (ORG_amount > 10000)}`（数字，`ORG_amount` 变量本身已是 `BigDecimal`，UEL 数值比较原生支持）或 `${(ORG_hireDate != null) && (ORG_hireDate > 20260101)}`（日期，两边都是 epoch day 整数）。无论算符是 `EQ` 还是 `NE`，缺失字段（变量为 `null`）都统一判定为条件不满足——这是用户已确认的行为（"视为条件不满足，走默认分支"），不因算符语义把 `NE` 在字段缺失时特殊处理成"满足"。

由于运行时对流程定义引用到的**全部**路由字段都会显式设置变量（值或 `null`，见决策2），UEL 表达式引用的变量名永远存在（不会触发 Flowable 对完全未声明变量抛 `PropertyNotFoundException` 之类的异常），只是值可能为 `null`，null 安全包裹能正确处理这种情况。

### 6. 前端字段下拉与联动控件

`NodePropertyPanel.vue` 条件分支编辑区改造：
- "字段"下拉：页面进入设计器时一次性并行请求 `GET /api/form-fields/render-schema?bizType=ORG/USER/POSITION/APP` 四次，合并成一个分组下拉（Element Plus `el-select` + `el-option-group`，按业务类型分组，组内选项标签为字段的 `fieldName`），过滤掉 `controlType=5`（多选字典）的字段。选中值存 `{bizType, fieldCode}`。
- "比较符"下拉：按选中字段的 `controlType` 动态过滤选项（决策4）。
- "比较值"输入：按选中字段的 `controlType` 切换控件——文本框(1)用 `el-input`，数字框(2)用 `el-input-number`，字典下拉(3)用 `el-select`（选项直接用该字段 `FormFieldRenderItemVO.dictOptions`，不必再单独请求字典接口），日期(4)用 `el-date-picker`。
- 未选字段前，比较符/比较值控件禁用，提示"请先选择字段"。

### 7. `ApprovalProcessService.start(...)` 签名扩展

`ApprovalProcessService`/`ApprovalProcessServiceImpl.start(...)` 新增 `Object typedPayload` 参数（`ApprovalRequestServiceImpl.submit()` 调用处已经持有这个已校验、已转换类型的对象，原样传入即可，不需要重新序列化/反序列化一次 `request_payload`）。

## Risks / Trade-offs

- [历史已发布版本条件分支字段名对应不上真实字段] → 保持现状语义（取不到值，按新规则视为不满足，走默认分支），不强行修复；管理员需重新编辑发布（见 Non-Goals）。
- [同一流程模型被多业务类型共用，条件字段清单会包含"这次提交用不上"的字段] → 命名空间化变量名 + 统一 null 安全包裹已经处理，不是缺陷，只是设计上允许的正常情况。
- [新增列/新的 DSL 结构可能影响既有 v1 集成测试对 `EdgeConditionDsl` 的断言] → 实施时需要跑一遍现有 `WorkflowModelCompilerImplTest`/`ProcessModelDslValidatorTest` 相关用例，按新结构调整测试数据，不属于运行时行为回归。
- [四次并行请求 `render-schema` 增加设计器页面初次加载的请求数] → 数据量小（字段定义清单通常几十条以内），且已有并发请求模式先例（本仓库其他页面已有类似的并行拉取模式），可接受。

## Migration Plan

1. 新增 Flyway 迁移：`tab_wf_process_definition` 加 `route_field_codes TEXT NULL`，MySQL 5.7 兼容语法，无需回填。
2. 后端按决策 1-5、7 实现：`ProcessModelDslValidator`/`WorkflowModelCompilerImpl` 改造 + `ApprovalProcessService`/`Impl` 签名扩展与变量构建逻辑 + `ApprovalRequestServiceImpl.submit()` 透传 `typedPayload`。
3. 前端按决策 6 实现设计器条件编辑区。
4. 历史已发布流程不受影响（新列为空时，运行时视为"该定义没有声明任何路由字段"，不设置任何条件相关变量，行为等同于此前的"变量不存在"，只是不再抛异常而是走 null 安全表达式判定不满足——需要确认这不会改变现有依赖"抛异常"的测试预期，若有此类测试按新的"优雅降级"语义调整）。
5. 上线后管理员如需真正按提交内容路由，需要在设计器里用新的字段下拉重新配置条件分支并重新发布一个新版本，通过业务绑定切换到新版本生效（不影响运行中的旧实例）。

## Open Questions

无阻塞性未决问题。
