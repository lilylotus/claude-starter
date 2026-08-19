## Context

同步能力目前有两层控制：一是整个应用一份的"基础同步配置"（`tab_app_config`：同步方式、签名校验等），二是六个数据域各自一份的"是否启用"（`tab_app_sync_domain_config.sync_enabled`）。变更记录的产生入口 `NotifyTargetMapper#selectCandidateAppRefIds` 已经是 `tab_app_config INNER JOIN tab_app INNER JOIN tab_app_sync_domain_config`，按 `sync_domain`+`sync_enabled`+应用状态过滤候选应用；拉取接口 `SyncPullServiceImpl` 各自按数据域 `sync_enabled` 判断是否返回数据；通知发送 `AppNotifyServiceImpl.notifyIfConfigured` 只在变更记录已经产生的前提下被调用。当前没有任何"整个应用同步能力一键关停"的开关，管理员要达到同等效果得把六个数据域逐个关掉，且已经落库的历史变更记录、拉取接口不受影响，仍可正常访问。

## Goals / Non-Goals

**Goals:**
- 新增一个应用级总开关（`tab_app_config.sync_master_enabled`，默认开启），关闭后：
  - 该应用不再产生任何新的 `tab_app_data_change_log` 记录（六个数据域一律不写，不区分各数据域自身是否仍是"允许同步"）。
  - 该应用不再收到任何通知（作为"不产生变更记录"的自然结果）。
  - 该应用调用拉取接口（按 id / 按序列号）一律返回空结果，包括总开关关闭之前已经产生的历史记录。
  - 重新打开后自动恢复正常行为，不做任何补偿/追溯发送。
- 前端在"基础同步配置"子 tab 新增开关控件，与现有字段合并一次保存。

**Non-Goals:**
- 不做"关闭期间遗漏的变更后续补偿同步"这类机制。
- 不新增权限点、不影响 `tab_app.status`（应用启停用）本身的语义——两者独立，应用被停用时开放接口已经在 `OpenApiSignInterceptor` 层直接拒绝，这次新增的开关只影响"应用处于启用状态时，其同步能力本身是否开放"。
- 不改动六个数据域各自的"是否启用"/组织范围/字段映射配置项，两层开关是"与"的关系（数据域启用 AND 总开关开启才真正同步），互不替代。

## Decisions

### Decision 1：总开关实现为候选应用查询里的一个 SQL 过滤条件，而不是在每个消费点各自判断
`NotifyTargetMapper#selectCandidateAppRefIds` 是"某数据类型发生变更时，哪些应用会拿到一条变更记录"这一判断的唯一入口，所有数据域（组织/用户/任职/应用/角色/字典）产生变更事件时都要经过它。在原有 `WHERE d.sync_domain = #{dataType} AND d.sync_enabled = 1 AND a.status = 2000` 后面加一句 `AND c.sync_master_enabled = 1` 就能一次性覆盖"六个数据域新增/编辑都不再写入变更记录"这个要求，不需要在 `AppDataChangeLogServiceImpl`、`DomainChangeEventProcessor` 等下游各处重复判断。通知发送（`AppNotifyServiceImpl.notifyIfConfigured`）本身只在变更记录已经产生的前提下才会被 `DomainChangeEventProcessor` 调用，既然总开关关闭时根本不会产生变更记录，通知也就自然不会发生，不需要在通知服务里再加一层判断。
- **备选方案**：在 `AppDataChangeLogServiceImpl.record()` 里对 `candidateAppRefIds` 做二次内存过滤（额外查一次 `tab_app_config`）。未采纳——候选应用列表本来就要 JOIN `tab_app_config`（別名 `c`），直接在同一条 SQL 里加条件零成本，没必要多一次查询或多一段 Java 过滤逻辑。

### Decision 2：拉取接口在方法入口显式判断总开关，返回空结果而不是报错
`SyncPullServiceImpl` 的两个拉取方法直接按 `appRefId` 查询 `tab_app_data_change_log`，不经过候选应用查询，因此总开关关闭后已存在的历史记录仍然会被查到，必须单独加一道判断。返回空结果（而不是 401/业务异常）是为了和现有"未开通数据类型""组织范围外"两个既有场景保持同一种语义——调用方合法但当前没有可用数据，不视为错误请求。拉取日志（`AppPullRecordService.record`）仍然照常记录本次调用尝试（含返回 0 条），不因总开关关闭而跳过日志写入，便于管理员事后排查"这段时间为什么应用一直拉不到数据"。
- **备选方案**：在 `OpenApiSignInterceptor` 里对总开关关闭的应用直接返回 401（类比 `tab_app.status` 停用时的处理）。未采纳——总开关关闭只是"暂时不开放同步数据"，不等同于应用整体不可用（应用的其他能力，如未来的 SSO 运行时鉴权，不受这个开关影响），复用应用停用的 401 语义会掩盖两种状态的区别，且与"未开通数据域返回空结果"的既有约定不一致。

### Decision 3：新字段随"基础同步配置"合并保存，不新增接口/权限点
前端在同一个 `PUT /api/apps/{id}/config/sync` 请求体（`SyncConfigUpdateRequest`）里新增 `syncMasterEnabled` 字段，与 `syncMode`/`notifyUrl`/`notifyParams`/`needSign` 一起提交，受同一个 `AppManagement:app:config:editSync` 权限点控制。这个开关在语义上和"同步方式""签名校验"一样属于"整个应用一份的基础同步配置"，没有必要为它单独开一个接口或权限点，增加前后端的联调成本。
- **备选方案**：新增独立接口 `PUT /api/apps/{id}/config/sync/master-switch`。未采纳——多一个接口、多一次请求，与现有"基础同步配置合并保存"的既定交互模式（见 `app-config-page-ux-refine` 变更 Decision 3）不一致。

## Risks / Trade-offs

- [关闭总开关和某数据域变更事件几乎同时发生时的竞态：数据库层面 `tab_app_config` 的更新与 `NotifyTargetMapper` 查询之间没有强一致性保证，可能出现"刚关闭总开关，但一条正在处理中的变更事件仍然按关闭前的状态被判定为候选应用"的极小概率窗口] → 可接受，不做额外加锁：这类竞态在关闭六个数据域各自开关时同样存在（现状如此），影响范围是单条记录级别，不影响整体正确性；`app-sync-notify-pull` spec 本身也没有对这类竞态做强一致性承诺。
- [历史记录不删除，只是被拉取接口屏蔽——如果管理员误以为"关闭"等于"清空数据"，可能产生困惑] → 前端开关旁的说明文案需要明确写出"关闭后不清空已产生的历史记录，仅暂停通知与拉取，重新打开后可继续访问"，避免误解。

## Migration Plan

新增 Flyway 迁移脚本 `V6__add_app_sync_master_switch.sql`，对已有 `tab_app_config` 行执行 `ALTER TABLE ... ADD COLUMN sync_master_enabled TINYINT(1) NOT NULL DEFAULT 1`，默认值保证存量应用行为不变（视为"开启"，与新增列前的实际行为一致）。无需数据回填脚本，无需灰度。回滚方式：回退该次迁移脚本对应的应用版本（不提供 DOWN 迁移，与仓库现有迁移脚本风格一致）。

## Open Questions

（无）
