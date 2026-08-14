## 1. 数据库

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V6__upstream_field_mapping_primary_key.sql`：`ALTER TABLE tab_upstream_field_mapping ADD COLUMN is_primary_key TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否作为落库匹配的主键标识字段之一，同一数据域可多选组成联合主键'`，不回改 V4/V5

## 2. 后端：DTO / Mapper

- [x] 2.1 `UpstreamFieldMappingRow` 新增 `isPrimaryKey` 字段
- [x] 2.2 `UpstreamFieldMappingMapper.xml` 的 `selectBySourceIdAndDataType` 补上 `m.is_primary_key AS isPrimaryKey`
- [x] 2.3 `UpstreamFieldMappingVO`、`UpstreamFieldMappingSaveRequest` 新增 `isPrimaryKey`（后者 `@NotNull`），确认 `UpstreamFieldMappingConvert`（MapStruct）按属性名自动映射即可，无需手写转换逻辑

## 3. 后端：字段映射保存校验

- [x] 3.1 `UpstreamFieldMappingServiceImpl.assertRequestsValid`：请求列表非空时，校验至少一条记录 `isPrimaryKey=true`，否则抛 `BusinessException`

## 4. 后端：同步匹配逻辑改造

- [x] 4.1 `UpstreamRowUpserter.upsertRow` 方法签名新增 `List<String> primaryKeyFieldCodes` 参数，透传给 `upsertOrg`/`upsertUser`/`upsertPosition`
- [x] 4.2 `upsertOrg`：去掉硬编码 `row.get("code")` 匹配，改为遍历 `primaryKeyFieldCodes`，用 `com.baomidou.mybatisplus.core.toolkit.StringUtils.camelToUnderline(fieldCode)` 转换列名，构造 `QueryWrapper<OrgEntity>().eq(column, value)...ne("status", OrgStatus.DELETED)`；任一主键字段取值为空时该行判定失败；沿用现有 `__parentCode` 上级组织解析逻辑（不变）
- [x] 4.3 `upsertUser`：同上，去掉硬编码 `code` 匹配，改用动态 `QueryWrapper<UserEntity>`
- [x] 4.4 `upsertPosition`：保留 `__userIdentifier`/`__orgCode` 解析 `userId`/`orgId`（不变），去掉"提供了 `positionType` 就 eq、否则不 eq"的旧逻辑，改为在 `userId`/`orgId` 匹配的基础上，遍历 `primaryKeyFieldCodes` 动态追加 `eq` 条件（`QueryWrapper<UserPositionEntity>`，为保持代码风格一致，`userId`/`orgId`/`status` 三个固定条件也一并改用 `QueryWrapper` 原生列名写法，不再混用 `LambdaQueryWrapper`）
- [x] 4.5 `UpstreamSyncExecutor.syncDomain`：在现有取数流程最前面新增前置校验——从该数据域字段映射里筛出 `isPrimaryKey=true` 的字段编码列表，为空时不调用取数组件，直接写入一条 `status=FAILED`、`total_count=0` 的执行记录（`fail_summary` 提示"该数据域尚未在字段映射中标记主键字段，无法判断新增/更新，请先在字段映射里标记至少一个主键字段后再同步"），随后 `return`；非空时把该列表传给 `upstreamRowUpserter.upsertRow`

## 5. 前端

- [x] 5.1 `UpstreamSourceConfigView.vue` 字段映射表格新增"主键标识"列（`el-checkbox` 或 `el-switch`），`FieldMappingRow`/`toFieldMappingRow`/`handleAddField`/保存请求体同步加上 `isPrimaryKey` 字段（新增行默认不勾选）
- [x] 5.2 `validateFieldMappingRows` 新增前端校验：行数组非空时至少一行 `isPrimaryKey=true`，否则提示错误、阻止保存（与后端校验一致，减少一次无意义的请求往返）
- [x] 5.3 `frontend/src/types/upstreamSource.ts` 的 `UpstreamFieldMappingVO`/`UpstreamFieldMappingSaveRequest` 新增 `isPrimaryKey: boolean`

## 6. 测试与验证

- [x] 6.1 后端单测更新/新增：`UpstreamRowUpserterTest` 覆盖"按单字段主键匹配新增/更新/多条失败"、"按联合主键匹配"、"主键字段取值为空该行失败"（组织/用户/任职各至少一个场景）；`UpstreamFieldMappingServiceImpl` 相关测试覆盖"非空列表未标记主键被拒绝"；`UpstreamSyncExecutor`（或其单测覆盖范围内）覆盖"零主键字段时同步前置判定失败、不调用取数组件"
- [x] 6.2 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.identity.upstream.*"` 全部通过（41 项）。`RbacApplicationTests` 在本机因本地 MySQL `rbac` 库的 Flyway 历史记录里 V2 迁移 checksum 与磁盘不一致（`03da31b` 提交修改过 V2 内容，与本次改动无关，此前已存在）而无法启动上下文，未能验证到 V6；已确认 `V6__upstream_field_mapping_primary_key.sql` 语法在 MySQL 5.7 下合法（标准 `ALTER TABLE ... ADD COLUMN`），且本地环境问题与本次改动无关
- [x] 6.3 `frontend/` 目录执行 `npm run build` 确认无类型错误

## 7. 文档同步

- [x] 7.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致，如实现时有调整需回写
