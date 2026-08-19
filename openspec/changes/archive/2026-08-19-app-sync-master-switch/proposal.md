## Why

目前"基础同步配置"只有同步方式（通知/拉取）、签名校验、通知地址/参数几项，没有一个能一键彻底关停某个应用全部同步能力的总开关：管理员如果要临时停止对某个外部应用的所有数据同步（比如该应用出了问题、或合作暂停），只能逐个把六个数据域的"是否启用"依次关掉，既繁琐又容易漏掉，而且已经写入 `tab_app_data_change_log` 的历史变更记录、拉取接口仍然可以正常访问，达不到"彻底停"的效果。需要在"基础同步配置"新增一个总开关，关闭后立刻、完整地切断该应用的同步能力。

## What Changes

- "基础同步配置"子 tab 新增一个可来回切换的开关（默认开启），随基础同步配置一起保存（复用现有 `PUT /api/apps/{id}/config/sync` 接口与 `AppManagement:app:config:editSync` 权限点，不新增接口、不新增权限点）。
- 关闭该开关后：
  - 组织、用户、任职、应用、角色、字典六个数据域发生新增、编辑（及其余会触发同步事件的操作）时，系统 SHALL NOT 为该应用写入任何 `tab_app_data_change_log` 记录（不受各数据域各自"是否启用"开关状态影响——即使某数据域本身仍是"允许同步"，只要总开关关闭，该应用也不再产生变更记录）。
  - 该应用的同步方式为"通知"时，系统 SHALL NOT 向其发起任何通知请求（因为已经没有变更记录可供触发通知，是总开关生效的自然结果，不需要额外的独立判断）。
  - 该应用调用按 id 拉取、按序列号拉取两个接口时，系统 SHALL 返回空结果，即使 `tab_app_data_change_log` 中已经存有该应用关闭之前产生的历史记录，也一律不返回（历史记录本身不删除，重新打开开关后可继续拉取）。
- 重新打开该开关后，上述限制解除，系统按各数据域各自"是否启用"及组织范围配置恢复正常同步行为（含关闭期间遗漏的变更不会被追溯补发——本次改动不涉及补偿机制）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-api-credentials`：「应用管理前端"配置"入口与页面」需求中"基础同步配置"子 tab 的字段清单需要补充这个总开关；「修改数据同步配置」需求需要补充该开关字段的存储与默认值。
- `app-sync-notify-pull`：「组织/用户/任职/应用/角色数据变更产生同步事件」需求需要补充"总开关关闭时不为该应用产生变更记录"的约束；「按数据类型与 id 拉取变更数据」「按序列号批量拉取变更数据」两个需求需要补充"总开关关闭时返回空结果"的约束。

## Impact

- `backend/src/main/resources/db/migration/`：新增一个 Flyway 迁移脚本，给 `tab_app_config` 加一列（如 `sync_master_enabled`，`TINYINT(1) NOT NULL DEFAULT 1`）。
- `backend/.../app/entity/AppConfigEntity.java`、`app/dto/AppConfigVO.java`、`app/dto/SyncConfigUpdateRequest.java`：新增对应字段（`syncMasterEnabled`），MapStruct 转换器无需改动（同名字段自动映射）。
- `backend/.../app/service/impl/AppConfigServiceImpl.java`：`createDefaultConfig` 写入默认值 `true`；`updateSyncConfig` 读写新字段，操作日志快照补充该字段。
- `backend/.../sync/notify/mapper/NotifyTargetMapper.xml`：候选应用查询 SQL 增加 `AND c.sync_master_enabled = 1` 条件，一处改动同时覆盖"不写变更记录"与"不触发通知"两个效果。
- `backend/.../sync/openapi/service/impl/SyncPullServiceImpl.java`：两个拉取方法入口增加总开关判断，关闭时直接返回空结果（拉取日志仍照常记录本次调用尝试）。
- `frontend/src/types/app.ts`、`frontend/src/api/app.ts`：`AppConfigVO`、`SyncConfigUpdateRequest` 补充字段。
- `frontend/src/views/application/app/AppConfigView.vue`："基础同步配置"子 tab 表单新增开关控件与说明文案。
- `权限资源.txt`：更新 `AppManagement:app:config:editSync` 的描述文字，补充这个新字段。
- 不新增权限点、不新增路由、不新增顶层导航。
