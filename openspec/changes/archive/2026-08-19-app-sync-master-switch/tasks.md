## 1. 数据库

- [x] 1.1 新增 Flyway 迁移脚本 `backend/src/main/resources/db/migration/V6__add_app_sync_master_switch.sql`：给 `tab_app_config` 加列 `sync_master_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '同步总开关：1=开启，0=关闭，关闭后不再产生该应用的数据变更记录、不发送通知、拉取接口返回空结果'`（沿用仓库现有迁移脚本风格：不写 DOWN 脚本，通用标准 SQL，不使用 MySQL 8.0+/其他厂商专属特性）

## 2. 后端：实体/DTO/转换

- [x] 2.1 `AppConfigEntity` 新增 `syncMasterEnabled` 字段（`Boolean`，MyBatis-Plus 按驼峰↔下划线自动映射到 `sync_master_enabled`）
- [x] 2.2 `AppConfigVO` 新增 `syncMasterEnabled` 字段
- [x] 2.3 `SyncConfigUpdateRequest` 新增 `syncMasterEnabled` 字段（`@NotNull`，布尔）
- [x] 2.4 确认 `AppConfigConvert`（MapStruct）无需改动——同名字段自动映射，`toVO` 现有 `@Mapping` 忽略列表不涉及这个新字段

## 3. 后端：配置存储与回显

- [x] 3.1 `AppConfigServiceImpl.createDefaultConfig`：写入默认值 `syncMasterEnabled=true`
- [x] 3.2 `AppConfigServiceImpl.updateSyncConfig`：读取并落库 `request.getSyncMasterEnabled()`
- [x] 3.3 `AppConfigServiceImpl.toLogSnapshot`：操作日志字段快照补充"同步总开关"（值展示为"开启"/"关闭"，与现有"是否需要签名验签校验"字段的展示风格一致）

## 4. 后端：变更记录产生与通知联动

- [x] 4.1 `backend/src/main/resources/mybatis/mapper/NotifyTargetMapper.xml` 的 `selectCandidateAppRefIds` 查询 SQL，在现有 `WHERE` 条件后追加 `AND c.sync_master_enabled = 1`
- [x] 4.2 确认 `AppDataChangeLogServiceImpl`、`DomainChangeEventProcessor`、`AppNotifyServiceImpl` 均无需改动（候选应用查询已经是唯一入口，通知服务只在变更记录已产生时才被调用）

## 5. 后端：拉取接口

- [x] 5.1 `SyncPullServiceImpl` 注入 `AppConfigMapper`（或复用已有依赖），新增私有方法判断给定 `appRefId` 当前同步总开关是否开启
- [x] 5.2 `pullByBizIds` 方法入口：总开关关闭时直接返回空结果（跳过后续查询），拉取日志仍照常记录本次调用（含返回 0 条）
- [x] 5.3 `pullBySequence`（`doPullBySequence`）方法入口：总开关关闭时直接返回空结果，拉取日志仍照常记录

## 6. 前端

- [x] 6.1 `frontend/src/types/app.ts`：`AppConfigVO`、`SyncConfigUpdateRequest` 新增 `syncMasterEnabled: boolean` 字段
- [x] 6.2 `frontend/src/views/application/app/AppConfigView.vue`："基础同步配置"子 tab 表单新增开关控件（`el-switch`，绑定新的本地状态，随 `saveBasicSyncConfig` 一并提交），旁附说明文案（关闭后不再产生新变更记录/不通知/拉取返回空，历史记录不清空，可随时重新打开）；`applyConfig` 回填该字段

## 7. 文档

- [x] 7.1 更新 `权限资源.txt` 中 `AppManagement:app:config:editSync` 的描述文字，补充同步总开关字段
- [x] 7.2 后端为 `SyncConfigUpdateRequest`/`AppConfigVO` 涉及的 Controller 方法确认 springdoc-openapi 注解（`@Schema` 描述）已覆盖新字段，无需新增 `@Operation`

## 8. 验证

- [x] 8.1 `./gradlew test`（在 `backend/` 目录下）通过，新增/调整必要的单元测试：候选应用查询过滤总开关关闭的应用、拉取接口在总开关关闭时返回空（含历史记录场景）
- [x] 8.2 `npm run build`（在 `frontend/` 目录下）通过
- [ ] 8.3 手工验证（如环境允许）：关闭某应用同步总开关后，触发一次该应用已启用同步的数据域变更，确认 `tab_app_data_change_log` 未新增该应用的记录；用该应用凭证调用拉取接口（含请求总开关关闭前已产生的历史 bizId/序列号），确认返回空结果；重新打开开关后拉取接口恢复正常

## 9. OpenSpec 收尾

- [ ] 9.1 实现完成后运行 `openspec-doc-sync` 对齐 `proposal.md`/`design.md`/`tasks.md` 与实际改动
- [ ] 9.2 视用户指示决定是否执行 `openspec-sync-specs` 把本变更的 delta spec 应用到 `openspec/specs/app-api-credentials/spec.md`、`openspec/specs/app-sync-notify-pull/spec.md`（归档仍为用户手动触发，不自动执行）
