## Why

流程设计器"条件"节点的分支配置（字段/比较符/比较值）目前实际引用的是 Flowable **流程启动变量**，而不是提交审批时表单里的业务数据。但 `ApprovalProcessServiceImpl.start()` 启动流程实例时传给 Flowable 的 `variables` 参数恒为 `null`——整个代码库里没有任何地方把提交的 `request_payload` 同步成流程变量。结果是：管理员在条件分支里配置的字段名无论怎么填，运行时都取不到值，条件表达式在真实审批场景里从未真正按提交内容路由过。同时前端"字段"是一个自由文本框，容易手误输错字段名。本 change 把这两个问题一起解决，让"按提交的审批内容字段走不同分支"这个诉求真正生效。

## What Changes

- 流程模型发布时，从条件分支的 DSL 中提取被引用的字段（`{bizType, fieldCode}` 组合），计算出该流程定义的"路由字段清单"并随发布产物一起落库。
- 审批提交发起流程实例时，按发起的 `bizType` 与流程定义的路由字段清单，从已提交的 `request_payload` 中取出对应字段值，转换为 Flowable 流程变量（数字字段转 `BigDecimal`，日期字段转为可比较的数值表示，字符串/字典字段保持字符串），变量名按 `bizType_fieldCode` 命名空间化，避免不同业务类型同名字段互相干扰；不属于本次提交业务类型的路由字段一律置为变量值 `null`。
- 条件分支编译为 Flowable 网关表达式时统一做 null 安全包裹：引用的字段变量为 `null`（含"这次提交的业务类型根本没有这个字段"的情况）时，该条件一律判定为不满足、走默认分支，不抛异常中断流程。
- 前端"条件"节点属性面板的"字段"输入从自由文本改为下拉选择：下拉数据来源为组织/用户/任职/应用四类业务对象的表单字段定义（`GET /api/form-fields/render-schema` 或既有等效接口）合并去重后的列表，选项标注所属业务类型（如"组织·风险等级"）；选中字段后，可用的比较符按字段控件类型收窄（文本/字典下拉仅"等于/不等于"，数字/日期支持全部六种）；比较值输入控件按字段类型联动（字典下拉字段的值也改为从该字典的选项里选，不再手打）。
- **不支持**：多选字典（`MULTI_DICT`）类型字段作为条件字段（无法做单值比较，下拉里不出现这类字段）；本 change 只覆盖前端可达的 v1 设计器/编译器（`WorkflowModelCompilerImpl`/`ProcessModelDslValidator`），不涉及从未被前端使用的 DSL v2（`dslv2` 包下的 `ConditionAstDsl`/`ProcessModelDslV2Validator`，那是另一套尚未接入 UI 的实现，其类似的字段白名单校验缺口留给该能力线后续自己的 change 处理）。

## Capabilities

### Modified Capabilities
- `workflow-process-designer`: "条件节点分支必须有兜底"需求扩展为"条件字段须从对应业务对象的表单字段定义中选择，且运行时按实际提交的审批内容取值路由"，不再是名不副实的"流程启动变量"。

## Impact

- 后端：`tab_wf_process_definition` 新增字段（路由字段清单，JSON 数组，如 `route_field_codes`）；`WorkflowModelCompilerImpl`/`ProcessModelDslValidator`（条件校验改为要求 `field` 是 `{bizType, fieldCode}` 结构且必须存在于对应业务类型的启用字段定义中，非自由文本）；`ApprovalProcessService.start(...)`/`ApprovalProcessServiceImpl` 新增从 `request_payload` 构建 Flowable 变量的逻辑；`ApprovalRequestServiceImpl.submit()` 需要把已经转换好的 `typedPayload` 传给 `approvalProcessService.start(...)`（当前签名不携带 payload，需要扩展）。
- 前端：`frontend/src/views/workflow/designer/panels/NodePropertyPanel.vue` 条件分支编辑区改为字段下拉+联动的比较符/比较值控件；`frontend/src/types/workflow.ts` 的 `EdgeConditionDsl` 结构调整（`field` 从字符串改为 `{bizType, fieldCode}` 或等效结构）。
- 数据库：新增一条 Flyway 迁移为 `tab_wf_process_definition` 加列，遵循 MySQL 5.7 兼容语法，允许为空、不回填历史数据（历史已发布的 v1 流程若配置了条件分支，字段是自由文本、无法自动映射到真实表单字段，历史行为保持"取不到值→按 null 处理→走默认分支"，不强行修复历史草稿，需管理员在设计器里重新编辑并重新发布）。
