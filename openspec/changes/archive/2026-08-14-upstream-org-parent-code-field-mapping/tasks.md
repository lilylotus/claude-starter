## 1. 后端：删除固定伪字段编码，改用转换后行读取

- [x] 1.1 删除 `backend/src/main/java/cn/nihility/rbac/identity/upstream/constant/UpstreamOrgPseudoFieldCode.java`
- [x] 1.2 `UpstreamRowUpserter.upsertOrg`：不再接收 `rawRow` 参数，`resolveParentId` 改签名为 `resolveParentId(Map<String, Object> row)`，从转换后行读取 `row.get("parentCode")` 而不是 `rawRow.get(UpstreamOrgPseudoFieldCode.PARENT_CODE)`；取值为空（`null`/空白）视为顶级组织（`parentId=0`），非空按 `tab_org.code` 精确匹配、匹配不到判定失败，判定逻辑本身不变
- [x] 1.3 `UpstreamRowUpserter.upsertRow` 中 `ORG` 分支调用 `upsertOrg` 时同步去掉 `rawRow` 实参（`POSITION` 分支保持不变，仍传 `rawRow` 用于解析 `__userIdentifier`/`__orgCode`）
- [x] 1.4 检查并更新 `UpstreamRowUpserter` 类和方法级 Javadoc 中提到 `UpstreamOrgPseudoFieldCode`/`__parentCode` 的描述，改为描述新的"通过字段映射配置 `parentCode`"机制

## 2. 前端：移除伪字段提示与常量

- [x] 2.1 `UpstreamSourceConfigView.vue` 组织数据域"是否启用"分区下提示"上级组织编码可选：取数结果如包含固定编码列「`__parentCode`」……"的 `el-alert` 整块删除（任职数据域的伪字段提示保留不变）
- [x] 2.2 `frontend/src/types/upstreamSource.ts` 删除 `UPSTREAM_ORG_PARENT_CODE_FIELD` 常量及其在 `UpstreamSourceConfigView.vue` 中的 import/引用

## 3. 测试

- [x] 3.1 `UpstreamRowUpserterTest` 中依赖 `UpstreamOrgPseudoFieldCode.PARENT_CODE`/`rawRow` 传递上级组织编码的既有用例（`upsertRow_shouldCreateOrg_whenNoMatch`、`upsertRow_shouldCreateOrg_whenParentCodeIsZero`、`upsertRow_shouldCreateOrg_whenParentCodeMatched`、`upsertRow_shouldFailOrg_whenParentCodeNotFound`，以及其余传了 `row, row` 但未显式测试 parentCode 的组织用例）改写为通过转换后行的 `parentCode` 键传值，`upsertRow` 调用签名同步去掉多余的 `rawRow` 实参。`upsertRow_shouldCreateOrg_whenParentCodeIsZero` 按新语义（不再特殊对待字面 `"0"`）改写为 `upsertRow_shouldCreateOrg_whenParentCodeBlank`（覆盖"配置了 `parentCode` 但取值为空白字符串"场景）
- [x] 3.2 新增/确认用例覆盖"组织数据域字段映射未配置 `parentCode` 时全部行落为顶级组织"场景（转换后行里不含 `parentCode` 键，即 `upsertRow_shouldCreateOrg_whenNoMatch`）
- [x] 3.3 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.identity.upstream.*"` 全部通过
- [x] 3.4 `frontend/` 目录执行 `npm run build` 确认无类型错误

## 4. 文档同步

- [x] 4.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致，如实现时有调整需回写（见下方说明）
