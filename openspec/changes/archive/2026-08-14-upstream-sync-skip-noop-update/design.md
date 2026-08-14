## Context

见 proposal.md - Why。`UpstreamRowUpserter.upsertOrg`/`upsertUser`/`upsertPosition` 在命中一条已有记录（`matches.size() == 1`）时，构造对应的 `XxxUpdateRequest`、用 `bindProperties(request, row)` 把转换后行的字段值反射设置到请求对象上（组织额外单独 `setParentId`、任职额外单独 `setOrgId`），随后无条件调用 `xxxService.update(id, request)`。该 `update()` 方法内部无条件写库、调用 `operationLogRecorder.recordUpdate(...)`（对 before/after 快照做逐字段 diff，diff 为空时仍然写一条 `changeDetail=[]` 的记录）、发布 `DomainChangeEvent`。

## Goals / Non-Goals

**Goals:**
- 上游同步命中已有记录、且本次数据与当前实际值完全一致时，不调用 `update()`，从而不产生空 diff 的操作日志、不刷新 `update_time`、不发布无意义的 `DomainChangeEvent`。
- 只在 `UpstreamRowUpserter` 内新增判断，不改动 `OrgService`/`UserService`/`PositionService.update()` 本身。

**Non-Goals:**
- 不修复"字段映射里没有配置的字段，在每次更新时被静默重置为请求 DTO 的 Java 默认值（`null`/`0`）"这个更底层、独立存在的问题——`bindProperties` 只遍历 `row`（转换后行）的 key 逐个设置，未出现在 `row` 里的属性会保持 `XxxUpdateRequest` 声明的默认值；这个行为在本次改动之前就已经存在（不是本次引入的回归），且发生在"本来就该调用 update()"的既有代码路径里（不影响"是否要跳过 update() 调用"这个判断本身的正确性——凡是有未映射字段导致的差异，本次改动依然会如实检测出"有变化"并照常更新，语义上不比现状更差）；如果要彻底解决，需要重新设计"未映射字段应该保留当前值而不是被清空"的语义，属于另一个独立的问题，本次不展开。
- 不新增"跳过更新"这一独立的行明细状态——跳过的行仍然计入 `UpstreamSyncRecordDetailStatus.SUCCESS`（数据本身是"一致"这个正确结果），不引入第三种状态。
- 不改变新增（`matches.isEmpty()`）分支的行为——新增场景不存在"比较是否有变化"这回事。

## Decisions

### Decision 1：新增通用的"请求对象 vs 已匹配实体逐属性比较"辅助方法，比较 Update 请求自身声明的全部属性
`isUnchanged(Object request, Object entity)`：用 `BeanWrapper` 遍历 `request`（`OrgUpdateRequest`/`UserUpdateRequest`/`PositionUpdateRequest`）自身声明的全部属性（`getPropertyDescriptors()`，跳过 `class` 伪属性），逐个用同名属性从 `entity` 读取当前值，`Objects.equals` 比较；只要有一个属性不相等就返回 `false`（存在变化，照常更新）。

选择"遍历 request 的属性"而不是"遍历 row 的 key"：`request` 在调用比较之前已经完整构造好（`bindProperties` 设置的普通字段 + `parentId`/`orgId` 等特殊字段都已就位），天然覆盖了这次更新实际会写入的全部属性，不需要在比较逻辑里重复"哪些是普通字段、哪些是特殊字段"这套分支；且 `request`/`entity` 的同名属性类型天然一致（`XxxUpdateRequest` 的字段本来就是照着 `XxxEntity` 对应字段的类型声明的，如 `parentId: Long`、`showOrder: Integer`），不需要额外的类型转换或容忍逻辑。

- **备选方案**：只比较 `row`（转换后行）里出现的字段，不比较 `parentId`/`orgId` 这类特殊字段。未采纳——会漏掉"上级组织变了但其余字段都没变"这种场景（应该照常更新却被误判为无变化）。

### Decision 2：三个数据域的更新分支统一在构造好 request 之后、调用 `xxxService.update()` 之前插入判断
```java
} else {
    OrgUpdateRequest request = new OrgUpdateRequest();
    bindProperties(request, row);
    request.setParentId(parentId);
    if (isUnchanged(request, matches.get(0))) {
        return;
    }
    orgService.update(matches.get(0).getId(), request);
}
```
`upsertUser`/`upsertPosition` 的更新分支同样处理。跳过时直接 `return`（本方法 `void`，`upsertRow` 调用方 `UpstreamSyncExecutor` 视为该行处理成功——没有抛异常即为成功，见现有 `syncDomain` 逐行 try/catch 逻辑，不需要额外改动执行引擎）。

## Risks / Trade-offs

- [风险] 见 Non-Goals："未映射字段被静默重置为默认值"这个既有问题会让"是否有变化"的判断在个别情况下产生假阳性（认为有变化，实际是默认值差异）——本次不解决，接受现状，仅确保不引入新的假阴性（真正有变化时漏判为无变化）。
- [风险] `BeanWrapper.getPropertyValue` 在个别属性上抛异常的可能性（理论上不应该发生，`request`/`entity` 都是普通 POJO）→ 缓解：沿用仓库已有的"BeanWrapper 反射操作在这个上下文里被认为足够可靠"的既有假设（`bindProperties` 已经在用同样的机制），不额外加 try/catch 静默吞掉。
