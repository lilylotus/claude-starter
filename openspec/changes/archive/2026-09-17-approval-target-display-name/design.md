## Context

`ApprovalRequestRow`（`frontend/src/types/approval.ts`，与后端 `ApprovalRequestVO` 字段对齐）已经携带了推导"审批对象"名称所需的全部原始数据，只是分散在两个字段里，且并非每种操作类型都齐全：

- `requestPayload`：CREATE/UPDATE 提交的新值（ENABLE/DISABLE/DELETE 为空）。ORG/USER/APP 三类的创建请求体（`OrgCreateRequest`/`UserCreateRequest`/`AppCreateRequest`）与对应 VO 都统一用字段名 `name`。
- `targetSnapshot`：目标记录当前值，**目前后端 `ApprovalRequestServiceImpl.toVO()` 只在 `operationType == UPDATE` 时才填充**（`vo.setTargetSnapshot(masterDataOperationExecutor.getCurrentTarget(...))` 只出现在 UPDATE 分支），ENABLE/DISABLE/DELETE 完全没有填充，导致这三类操作在前端拿不到任何名称信息。
- `POSITION`（任职）没有独立的 `name` 字段，`PositionVO`/`PositionCreateRequest` 用 `userName`（所属用户姓名）+ `orgName`（所属组织名称）表达"这是谁在哪个组织的任职"。

三处消费方（`MyApprovalRequestView.vue`、`PendingApprovalRequestView.vue`、`ApprovalRequestDetailDialog.vue`）目前都是直接渲染 `row.targetId ?? '-'`，各自独立实现，没有共享工具函数。

同类问题还出现在 `ApprovalRequestDetailDialog.vue` 里"任职信息"卡片的嵌套渲染上：`positionFieldRows()`（193-202 行）取"所属组织"字段值时已经写好了 `(position.orgName as string) || (position.orgId != null ? String(position.orgId) : '-')` 这样的优先级——只要 `position.orgName` 存在就会用它，问题在于数据源侧：`UserPositionVO`（既有任职记录，来自 `targetSnapshot`）本来就带 `orgName`；但 `UserPositionRequest`（`requestPayload.positions` 里新增/提交的任职记录，`UserCreateRequest`/`UserUpdateRequest` 共用同一个请求 DTO）只有 `orgId`，没有 `orgName` 字段，序列化进 `requestPayload` 后自然也没有，导致前端退化展示组织 id 原始数字。

## Goals / Non-Goals

**Goals:**
- "目标记录ID"列/字段在三处 UI 统一改名为"审批对象"，展示人类可读名称：USER→姓名，ORG→组织名称，APP→应用名称，POSITION→"用户姓名 - 组织名称"。
- CREATE 类型从 `requestPayload` 取名称，UPDATE/ENABLE/DISABLE/DELETE 类型从 `targetSnapshot` 取名称；后端为此补齐 ENABLE/DISABLE/DELETE 下的 `targetSnapshot` 填充。
- 三处 UI 共用同一份"从行数据解析审批对象展示名称"的前端工具函数，不重复实现三份等价逻辑。
- USER 类型申请 `requestPayload.positions` 里每条任职记录都能展示组织名称，不再在没有 `orgName` 时退化展示 `orgId` 原始数字。

**Non-Goals:**
- 不改变 `targetId`/`resultTargetId` 字段本身的返回与语义，不影响撤回、创建结果关联等依赖 `targetId` 做业务判断的既有逻辑——本次只是前端换了一种展示方式，原始 id 仍然原样返回（供需要时排查用，只是不再作为主展示字段）。
- 不为 POSITION 新增独立的 "name" 字段或数据库改动；直接复用已有的 `userName`/`orgName` 组合展示。
- 不改变目标记录已被物理删除后的兜底行为：`MasterDataOperationExecutor.getCurrentTarget()` 已有的"目标不存在时返回 `null`"语义保持不变，此时前端展示 `-`。

## Decisions

### Decision 1：后端把 `targetSnapshot` 的填充条件从"仅 UPDATE"放宽到"UPDATE/ENABLE/DISABLE/DELETE"

`ApprovalRequestServiceImpl.toVO()` 里原本的：
```java
if (Objects.equals(entity.getOperationType(), ApprovalOperationType.UPDATE)) {
    vo.setTargetSnapshot(masterDataOperationExecutor.getCurrentTarget(entity.getBizType(), entity.getTargetId()));
}
```
改为对 UPDATE/ENABLE/DISABLE/DELETE 四种"`targetId` 非空"的操作类型都调用 `getCurrentTarget`（CREATE 因为 `targetId` 为空，本身就不适用，维持只从 `requestPayload` 取值）。`getCurrentTarget()` 已经具备"记录不存在时捕获异常返回 `null`"的兜底（`MasterDataOperationExecutor.getCurrentTarget()` 既有实现），不需要额外处理已删除记录的情况。

理由：这是获得 ENABLE/DISABLE/DELETE 三类操作"审批对象名称"数据来源的唯一方式——这三类操作提交时不携带 `requestPayload`，只能从目标记录当前值里取名称。

### Decision 2：前端新增一个共享工具函数，三处 UI 统一调用

新增 `frontend/src/utils/approvalTarget.ts`，导出 `resolveApprovalTargetLabel(row: Pick<ApprovalRequestRow, 'bizType' | 'targetId' | 'requestPayload' | 'targetSnapshot'>): string`：

- 数据来源取值优先级：`targetSnapshot`（UPDATE/ENABLE/DISABLE/DELETE 四类操作的目标记录当前值）非空时优先使用，为空时（此时必为 CREATE，因为其余四类操作都会填充 `targetSnapshot`）退化到 `requestPayload`；不再按 `bizType` 或 `operationType` 额外分支判断，一行 `row.targetSnapshot ?? row.requestPayload` 的空值合并即可覆盖全部五种操作类型。二者都取不到时返回 `'-'`。
- `USER`/`ORG`/`APP`：取来源对象的 `name` 字段。
- `POSITION`：取来源对象的 `userName`/`orgName` 字段，拼成 `"${userName} - ${orgName}"`；某一侧缺失时只展示存在的一侧，都缺失时返回 `'-'`。
- 三处 UI（`MyApprovalRequestView.vue` 表格列、`PendingApprovalRequestView.vue` 表格列、`ApprovalRequestDetailDialog.vue` 详情项）把原来的 `{{ row.targetId ?? '-' }}` 替换为 `{{ resolveApprovalTargetLabel(row) }}`，列/字段标签由"目标记录ID"改为"审批对象"。

理由：三处渲染逻辑完全一致，抽成一个纯函数避免重复；不放进 `types/approval.ts`（该文件当前只放类型定义与选项常量，不放渲染逻辑），单独建一个小工具文件更符合项目里"utils 放跨组件复用的纯函数"的既有组织方式。

### Decision 3：不改后端 DTO 结构，不新增专用的"审批对象名称"字段

考虑过在 `ApprovalRequestVO` 上直接加一个 `targetLabel: String` 字段、由后端算好名称再返回，前端就不用再解析 `requestPayload`/`targetSnapshot`。最终选择维持现状（前端解析），理由：
- `requestPayload`/`targetSnapshot` 已经是详情弹窗渲染新旧字段对照所必需的数据，前端本来就要拿到，不存在"专门为了取个名字多返回一份大对象"的浪费。
- 名称拼接规则（尤其 POSITION 的组合展示）属于纯展示层决策，放前端更容易在不改后端接口的情况下继续调整格式。

### Decision 4：`requestPayload.positions` 的组织名称在读取时批量查询注入，不改动存储的原始 JSON、不改 `UserPositionRequest` DTO

`ApprovalRequestServiceImpl.toVO()` 里，在 `payload = JacksonUtils.toObj(entity.getRequestPayload(), ...)` 反序列化成 `Map<String, Object>` 之后、`vo.setRequestPayload(payload)` 之前（紧邻既有的 `removeHiddenFields(payload, ...)` 调用），新增一步：当 `entity.getBizType() == USER` 且 `payload.get("positions")` 是非空数组时，收集其中每条记录的 `orgId`，去重后用新注入的 `OrgMapper`（沿用 `UserServiceImpl` 同款"批量 `selectList(in orgIds)` + 建 `Map<Long, String>`"模式，`backend/.../user/service/impl/UserServiceImpl.java:458-470` 一致的写法）批量查出组织名称，逐条往每个 position 的 `Map` 里塞一个 `orgName` 键。

不修改：
- 数据库里 `tab_approval_request.request_payload` 存的原始 JSON 字符串——只在这次查询返回给前端的 `Map` 对象上追加字段，属于读时展示层的增强，不是"篡改已冻结的申请内容"。
- `UserPositionRequest` DTO 本身——它是创建/更新用户的请求体，不应该为了审批详情展示反过来携带一个仅用于展示的 `orgName` 字段，语义不干净；也不影响用户管理页面正常创建/编辑用户时的提交/校验行为。

为什么不放进 Decision 2 的前端共享工具函数里做：`resolveApprovalTargetLabel()` 解决的是"顶层一个审批对象一个名称"的场景，"一条申请里嵌套一个任职记录数组，每条各自需要一个组织名称"是不同粒度的问题，且 `ApprovalRequestDetailDialog.vue` 里 `positionFieldRows()` 已经有现成的 `orgName` 优先级读取逻辑（现状代码 202 行），后端补上数据源后前端不需要动渲染代码，只需要把过时的注释（193-197 行，说明"新增任职记录没有 orgName 只能展示 orgId"）更新掉。

## Risks / Trade-offs

- **[风险] ENABLE/DISABLE/DELETE 的 `targetSnapshot` 放宽后，返回体体积/一次额外查询成本增加** → 影响面小（每条记录一次已有的 `masterDataOperationExecutor.getCurrentTarget()` 调用，此前 UPDATE 类型已在用同一方法，只是覆盖范围扩大到另外三种操作类型，量级不变）。
- **[风险] 已删除的目标记录（DELETE 审批通过后）查询详情时 `targetSnapshot` 为 `null`** → 前端 `resolveApprovalTargetLabel` 对空值统一兜底为 `'-'`，与当前 `targetId` 为空时的展示保持一致的降级体验。
- **[权衡] POSITION 类型的展示格式（"用户姓名 - 组织名称"）不是需求原文明确要求的**，是本次为保持三处 UI 行为一致而补的合理默认——如与业务预期不符，后续可以单独调整 `resolveApprovalTargetLabel` 里的 POSITION 分支，不影响其余三种类型。
- **[风险] `requestPayload.positions` 里引用的 `orgId` 对应组织已被删除** → 批量查询按 `selectList(in orgIds)` 天然跳过不存在的 id，对应 position 不会拿到 `orgName`，前端沿用既有兜底展示 `orgId` 原始数字，不报错、不中断详情查看。

## Migration Plan

- 无数据库结构变更，无需 Flyway 迁移脚本，纯后端 DTO 填充逻辑 + 前端展示逻辑变更，随正常发布上线。
- 无需灰度开关，无破坏性变化（`targetId` 原样保留在响应体里，只是前端换了渲染方式）。

## Open Questions

（无）
