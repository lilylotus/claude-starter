## 1. 数据库

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V7__upstream_sync_record_detail.sql`：建表 `tab_upstream_sync_record_detail`（字段与索引见 design.md Decision 2），不回改 V1~V6

## 2. 后端：明细实体/DTO/Mapper

- [x] 2.1 新增 `UpstreamSyncRecordDetailEntity`（`id`/`syncRecordId`/`sourceId`/`rowNo`/`rowData`/`status`/`failReason`/审计字段）
- [x] 2.2 新增常量类 `UpstreamSyncRecordDetailStatus`（`SUCCESS`/`FAILED`，独立于记录级别的 `UpstreamSyncStatus`）
- [x] 2.3 新增 `UpstreamSyncRecordDetailMapper extends BaseMapper<UpstreamSyncRecordDetailEntity>`
- [x] 2.4 新增 `UpstreamSyncRecordDetailVO`（`id`/`rowNo`/`rowData`/`status`/`failReason`）

## 3. 后端：`UpstreamSyncExecutor` 改造

- [x] 3.1 `syncDomain` 取数成功后，`rawRows.isEmpty()` 时更新 `domainConfig.lastSyncTime` 后直接 `return`，不写执行记录（design.md Decision 1）
- [x] 3.2 逐行处理循环里收集 `List<UpstreamSyncRecordDetailEntity>`：`rowNo` 从 1 递增，`rowData` 为 `JacksonUtils.toJson(rawRow)`，成功行 `status=SUCCESS`，失败行 `status=FAILED` 且 `failReason` 为完整异常消息（不受 `FAIL_SUMMARY_MAX_ITEMS` 截断限制）
- [x] 3.3 `saveSyncRecord` 插入后用其自增 id 回填每条明细的 `syncRecordId`/`sourceId`，逐行 `insert` 写入明细表（design.md Decision 3，不引入批量插入框架）
- [x] 3.4 注入 `UpstreamSyncRecordDetailMapper` 依赖

## 4. 后端：删除级联 + 查询接口分页改造

- [x] 4.1 `UpstreamSourceServiceImpl.delete` 新增按 `source_id` 删除 `tab_upstream_sync_record_detail`
- [x] 4.2 `UpstreamSyncRecordService.listBySource` 签名改为 `PageResult<UpstreamSyncRecordVO> listBySource(Long sourceId, Integer page, Integer pageSize)`，`UpstreamSyncRecordServiceImpl` 改用 `upstreamSyncRecordMapper.selectPage`
- [x] 4.3 `UpstreamSyncRecordService` 新增 `PageResult<UpstreamSyncRecordDetailVO> listDetailsByRecord(Long sourceId, Long recordId, Integer page, Integer pageSize)`，查询条件同时带 `source_id`/`sync_record_id`（design.md Decision 4 越权防护）
- [x] 4.4 `UpstreamSourceController.listSyncRecords` 新增 `page`/`pageSize` 请求参数（默认 1/10，比照 `OrgController.children`），返回类型改为 `PageResult<UpstreamSyncRecordVO>`
- [x] 4.5 `UpstreamSourceController` 新增 `GET /api/identity/upstream-sources/{id}/sync-records/{recordId}/details` 端点，`page`/`pageSize` 请求参数，返回 `PageResult<UpstreamSyncRecordDetailVO>`

## 5. 前端

- [x] 5.1 `frontend/src/types/upstreamSource.ts` 新增 `UpstreamSyncRecordDetailVO` 类型；`listUpstreamSyncRecords` 相关类型改为分页请求/响应
- [x] 5.2 `frontend/src/api/upstreamSource.ts`：`listUpstreamSyncRecords` 增加 `page`/`pageSize` 参数、返回类型改为 `PageResult<UpstreamSyncRecordVO>`；新增 `listUpstreamSyncRecordDetails(sourceId, recordId, page, pageSize)`
- [x] 5.3 `UpstreamSourceConfigView.vue` "同步记录"分区改为分页表格（复用 `PAGE_SIZE_OPTIONS`/`el-pagination`，与仓库其余管理列表页交互一致），每行新增"查看明细"操作列
- [x] 5.4 新增"查看明细"`el-dialog`：点击后按当前 `sourceId`/记录 `id` 分页加载明细列表，展示行序号、原始数据（`<pre>` 展示）、状态（`el-tag`）、失败原因
- [x] 5.5 "数据范围"分区"是否启用"表单里紧挨"是否启用"开关新增"上次同步时间"只读展示（`UpstreamDomainConfigVO.lastSyncTime` 已有该字段，此前未渲染，design.md Risks 缓解）
- [x] 5.6 `frontend/` 目录执行 `npm run build` 确认无类型错误

## 6. 测试与验证

- [x] 6.1 `UpstreamSyncExecutorTest` 新增用例：覆盖"取数结果为空时不写执行记录但更新 lastSyncTime"、"全部成功时为每行写入 SUCCESS 明细"、"部分失败时明细状态与失败原因正确"、"取数异常场景仍照常写执行记录（且无明细）"；既有"未配置主键前置拦截"用例补充断言明细 mapper 无交互
- [x] 6.2 新增 `UpstreamSyncRecordServiceImplTest`：覆盖分页查询记录列表、按记录 id 分页查询明细（含越权场景：错误的 `sourceId`/`recordId` 组合查不到数据）
- [x] 6.3 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.identity.upstream.*"` 全部通过，并跑 `RbacApplicationTests` 确认 V7 迁移正常执行（均通过）
- [x] 6.4 `frontend/` 目录执行 `npm run build` 确认无类型错误（与 5.6 合并验证，通过）

## 7. 文档同步

- [x] 7.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致，未发现需要回写的调整
