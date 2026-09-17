## 1. 后端：补齐目标记录当前值填充范围

- [ ] 1.1 `backend/src/main/java/cn/nihility/rbac/approval/service/impl/ApprovalRequestServiceImpl.java` 的 `toVO()`：把原本只在 `operationType == UPDATE` 时调用 `masterDataOperationExecutor.getCurrentTarget(...)` 填充 `targetSnapshot` 的条件，放宽为 UPDATE/ENABLE/DISABLE/DELETE 四种 `targetId` 非空的操作类型均调用（CREATE 保持不变，不调用）。
- [ ] 1.2 确认 `getCurrentTarget()` 对目标记录已被删除的情况已有的"捕获异常返回 `null`"兜底在放宽后依旧生效，无需额外改动。
- [ ] 1.3 在 `ApprovalRequestServiceImpl` 中新增 `OrgMapper` 依赖（`@RequiredArgsConstructor` 构造器注入，与既有字段风格一致）。
- [ ] 1.4 `toVO()` 里在 `payload` 反序列化之后、`vo.setRequestPayload(payload)` 之前，新增私有方法（如 `enrichPositionOrgNames(Map<String, Object> payload)`）：当 `bizType=USER` 且 `payload.get("positions")` 是非空 `List`，收集所有 `orgId` 去重后用 `OrgMapper.selectList(new LambdaQueryWrapper<OrgEntity>().in(OrgEntity::getId, orgIds))` 批量查询，建 `Map<Long, String>`，逐条往 position 的 `Map` 里塞 `orgName`（查不到的组织保持不塞，前端沿用既有兜底）。

## 2. 后端：测试

- [ ] 2.1 为 `ApprovalRequestServiceImpl`（或其集成测试）新增用例：ENABLE/DISABLE/DELETE 类型申请查询"我的申请"/"待我审批"结果时，`targetSnapshot` 非空且包含目标记录当前字段（如 `name`）。
- [ ] 2.2 新增用例：DELETE 申请审批通过、目标记录已被物理删除后再次查询该申请，接口正常返回且 `targetSnapshot` 为空，不抛异常。
- [ ] 2.3 确认 CREATE/UPDATE 现有测试用例不受影响（UPDATE 仍同时有 `targetSnapshot` 与 `requestPayload`，CREATE 仍只有 `requestPayload`）。
- [ ] 2.4 新增用例：`bizType=USER` 的 CREATE 申请，`requestPayload.positions` 携带 1~2 条任职记录，查询结果中每条记录都带上正确的 `orgName`。
- [ ] 2.5 新增用例：`requestPayload.positions` 中某条记录的 `orgId` 指向一个不存在的组织时，该条记录 `orgName` 为空、其余记录不受影响、接口不报错。

## 3. 前端：共享工具函数

- [ ] 3.1 新建 `frontend/src/utils/approvalTarget.ts`，导出 `resolveApprovalTargetLabel(row)`：
  - CREATE（`requestPayload` 非空、`targetSnapshot` 为空）从 `requestPayload` 取值，其余从 `targetSnapshot` 取值；
  - `bizType` 为 `USER`/`ORG`/`APP` 时取来源对象的 `name` 字段；
  - `bizType` 为 `POSITION` 时取来源对象的 `userName`/`orgName` 字段，拼成 `"用户姓名 - 组织名称"`，某一侧缺失只展示存在的一侧；
  - 所有来源都取不到值时返回 `'-'`。

## 4. 前端：三处 UI 接入

- [ ] 4.1 `frontend/src/views/approval/mine/MyApprovalRequestView.vue`：表格列标题"目标记录ID"改为"审批对象"，渲染内容由 `row.targetId ?? '-'` 改为 `resolveApprovalTargetLabel(row)`。
- [ ] 4.2 `frontend/src/views/approval/pending/PendingApprovalRequestView.vue`：同上。
- [ ] 4.3 `frontend/src/components/ApprovalRequestDetailDialog.vue`：详情项标签"目标记录ID"改为"审批对象"，渲染内容同上；"生效记录ID"（`resultTargetId`）保持不变，不在本次改动范围内。
- [ ] 4.4 `frontend/src/components/ApprovalRequestDetailDialog.vue` 的 `positionFieldRows()` 上方注释（193-197 行）：更新为"新增任职记录的组织名称由后端 `toVO()` 批量注入，不再有取不到 `orgName` 只能展示 `orgId` 的限制（组织已被删除的例外情况仍会展示 `orgId`）"；渲染逻辑本身（202 行）不需要改动，后端补数据后自动生效。

## 5. 前端：本地验证

- [ ] 5.1 `npm run build`（`vue-tsc` 类型检查 + vite build）确认无类型错误。
- [ ] 5.2 启动前后端，登录 `admin`/`admin123`，分别构造 ORG/USER/APP/POSITION 四类的 CREATE、以及至少一种 ENABLE/DISABLE/DELETE 申请，在"我的申请"、"待我审批"、详情弹窗三处核对"审批对象"列展示的名称符合预期，目标记录被删除后的申请展示 `-` 而不报错。
- [ ] 5.3 在用户管理页面提交一条带任职记录的新增用户申请，打开该申请详情，核对"任职信息"卡片里"所属组织"展示的是组织名称而不是数字 id。

## 6. OpenSpec 文档同步

- [ ] 6.1 实现完成后，调用 `openspec-doc-sync` 按真实 diff/测试结果核对并更新本 change 的 `proposal.md`/`design.md`/`tasks.md`。
- [ ] 6.2 待用户确认后执行 `openspec-sync-specs` 把本 change 的 delta spec 应用到 `openspec/specs/master-data-approval-workflow/spec.md`；归档由用户手动触发，不自动执行。
