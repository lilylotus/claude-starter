## Context

`identity-upstream-data-sync` 已实现并归档（`openspec/changes/archive/2026-08-14-identity-upstream-data-sync/`）。当前 `UpstreamRowUpserter` 的匹配逻辑是硬编码的：`upsertOrg`/`upsertUser` 固定读取转换后行里的 `code` 属性做匹配；`upsertPosition` 在解析出所属人员（`__userIdentifier`）、所属组织（`__orgCode`）两个伪字段之后，如果转换后行里有 `positionType` 就把它也作为匹配条件，没有就只按 `userId+orgId` 匹配（可能命中该人员在该组织下的任意一条任职记录，存在误更新风险）。`tab_upstream_field_mapping` 目前没有任何标记字段用途的列。

## Goals / Non-Goals

**Goals:**
- 字段映射里可以勾选一个或多个字段作为"主键标识"，落库时按这些字段（联合、AND 语义）匹配已有记录。
- 组织/用户/任职三个数据域统一使用这套机制（任职额外叠加伪字段解析出的所属人员/组织范围限定）。
- 对本次改动上线前已保存、没有任何字段标记为主键的历史配置做安全兜底（同步判定失败而不是误操作全表数据）。

**Non-Goals:**
- 不改变字段映射整体替换的保存语义（仍是"提交完整列表，先删后插"）。
- 不引入除 MyBatis-Plus 已有工具类之外的新依赖。
- 不处理"修改主键标识配置后，之前用旧主键匹配落地的历史数据"的数据修复/迁移问题——这属于管理员切换匹配策略时需要自行评估的运维影响，本次不做特殊处理（如自动去重、历史数据回溯匹配等）。

## Decisions

### Decision 1：`tab_upstream_field_mapping` 新增 `is_primary_key` 列，单独开一个新版本号迁移
新增 `is_primary_key TINYINT(1) NOT NULL DEFAULT 0`。不回改已执行过的 `V4__identity_upstream_data_sync.sql`（建表）、`V5__seed_upstream_menu_resource.sql`（菜单种子数据），新开 `V6__upstream_field_mapping_primary_key.sql`，与仓库里"已执行迁移不回改，未执行的允许合并"的既有约定一致（`identity-upstream-data-sync` 归档文档里已经用过一次同样的处理方式）。默认值 `0` 意味着历史已保存的字段映射行天然"零主键字段"，这是 Decision 4 兜底逻辑要处理的前提条件，不是缺陷。

### Decision 2：匹配查询改用 MyBatis-Plus 原生 `QueryWrapper` + `StringUtils.camelToUnderline`，不用 `LambdaQueryWrapper`
主键字段是运行时才知道的动态集合（管理员在字段映射里勾选的任意字段组合），无法用 `LambdaQueryWrapper` 的方法引用语法（`Entity::getXxx`）在编译期表达。改用 `com.baomidou.mybatisplus.core.toolkit.StringUtils.camelToUnderline(fieldCode)` 把字段映射里的系统字段编码（即 CreateRequest/UpdateRequest 的 Java 属性名，等价于对应实体的属性名）转换成数据库列名，再用 `QueryWrapper<T>().eq(columnName, value)` 逐个拼接查询条件（多个 `.eq()` 默认 AND 语义，天然满足联合主键"全部相等"的要求）。这个转换之所以可靠：元数据字段目录里的字段本身就是"与业务表物理列一一对应的开放配置列"这一既有不变量（`app-sync`/`excel-import` 等既有能力都依赖同一假设），不存在字段编码与列名对不上驼峰/下划线转换规则的情形。
- **备选方案**：给每个可能作为主键的字段单独写 `if/else` 分支（如"如果主键包含 code 就 eq code，如果包含 mobile 就 eq mobile……"）。未采纳——字段组合是任意的，穷举分支不可维护，且未来元数据字段增删无法自动适配。

### Decision 3：ORG/USER 完全去掉对 `code` 的硬编码依赖，任职在伪字段解析的基础上叠加主键字段
`upsertOrg`/`upsertUser` 不再读取固定的 `row.get("code")`，改为遍历本次调用传入的主键字段编码列表，从转换后的行里取值拼接查询条件；任一主键字段取值为空（`null` 或空白字符串）时，该行直接判定失败（复用现有"必填校验失败"的错误提示模式，提示是哪个字段编码取值缺失）。`upsertPosition` 保持"先用 `__userIdentifier`/`__orgCode` 解析出 `userId`/`orgId`"不变（这是解析外键归属关系，不是本次改动的范围），在此基础上，把原来"有 `positionType` 就 eq、没有就不 eq"的逻辑整体替换为"遍历主键字段列表逐个 eq"——如果管理员把 `positionType` 标记为主键，效果和原来"提供了就按它匹配"一致；如果标记了其他字段或多个字段组合，同样按新机制统一处理，不再有特殊分支。

### Decision 4：同步执行前置校验"已启用数据域至少有一个主键字段"，未通过时不发起取数请求直接记失败
`UpstreamSyncExecutor.syncDomain` 在现有"取数→转换→逐行落库"流程最前面新增一步：从本次数据域的字段映射里筛出 `isPrimaryKey=true` 的字段编码列表，为空时立即写入一条 `status=FAILED`、`total_count=0`、`fail_summary` 为"该数据域尚未在字段映射中标记主键字段，无法判断新增/更新，请先在字段映射里标记至少一个主键字段后再同步"的执行记录，直接返回，不调用 `UpstreamHttpFetcher`/`UpstreamJdbcFetcher`（避免对上游发起没有意义的请求）。这一步是为了兜底 Decision 1 提到的历史数据（本次改动上线前保存、默认零主键字段的已有配置）——如果没有这道前置校验，旧配置的数据源下次自动同步时，`upsertOrg`/`upsertUser` 会拿着一个空的主键字段列表去拼 `QueryWrapper`（不 `eq` 任何条件，退化成"匹配全表所有未删除记录"），要么直接被"匹配到多条记录"判定失败（如果表里现有数据 > 1 行，问题不大，只是每行都失败），要么在表里恰好只有 0/1 行未删除记录的边界情况下，错误地把新同步的一行数据更新到一条无关的已有记录上——这是必须提前拦截的数据安全风险，因此选择在处理任何一行之前就整体拦截，而不是让每行各自去撞见这个空条件的边界情况。
- **备选方案**：保存字段映射时校验、不在同步时二次校验。未采纳——保存时的校验只能拦住"这次改动上线之后新保存的配置"，拦不住"上线前已经保存、之后不会再触碰保存接口、但会被定时任务持续调度"的存量配置，必须在执行路径上也兜底。

## Risks / Trade-offs

- [风险] 已经启用、且已经稳定运行过若干次同步的存量数据源，升级后下一次同步会突然全部判定为 `FAILED`（因为零主键字段），管理员如果没注意到这次改动、没有及时去补标主键，会造成同步"看起来停摆"的观感 → 缓解：`fail_summary` 文案直接给出修复路径（去字段映射标记主键），且这本来就是必须做的一次性配置迁移动作，没有办法在不了解管理员意图的前提下自动猜出该用哪个字段做主键；接受这个一次性的手动迁移成本。
- [风险] 联合主键字段值如果不是真正唯一（如管理员错误地只标记了一个实际会重复的字段），会导致"匹配到多条记录"判定失败增多，或者匹配到错误的一条记录被误更新 → 缓解：沿用既有的"匹配到多条记录=该行失败"保护机制（不会静默误更新到错误记录上，只会拒绝处理该行），"匹配到唯一一条但其实是错误的那条"这类由管理员配置失误导致的语义错误无法在系统层面兜底，责任在管理员正确选择真正唯一的字段组合。
