## Context

当前同步能力的数据流：组织/用户/任职/应用/角色任一数据变更 → `DomainEventPublisher` 发布 `DomainChangeEvent`（事务提交后经 Disruptor 异步消费）→ `DomainChangeEventProcessor` 调用 `AppDataChangeLogService.record()`：按数据域候选应用查询（`NotifyTargetMapper`，过滤 `sync_enabled`+`sync_master_enabled`+应用状态）+ 组织范围过滤（`AppSyncOrgScopeResolver`），为每个匹配应用各插入一条 `tab_app_data_change_log` 记录（自增 id 即对外序列号）→ 对每条新记录调用 `AppNotifyService.notifyIfConfigured()`（仅 `syncMode=NOTIFY` 的应用真正发起 HTTP 通知）。拉取接口（`SyncPullServiceImpl`）两个方法都是查 `tab_app_data_change_log`（按 `bizId` 查最新一条 / 按序列号游标增量查），拿到变更记录的元信息后，用 `BizSnapshotResolver` 现查业务表当前状态作为 `data` 字段返回（即拉取响应的 `data` 早已不是变更记录的历史快照，而是实时现查——这次改动是把"现查"这一步从"先经过变更记录定位、再现查"简化为"直接分页现查"）。

本次改动移除 `tab_app_data_change_log` 这一层中间状态：拉取变成直接对业务表做分页查询；通知变成数据变更时直接判定候选应用、直接发起，不再有持久化步骤。`AppSyncOrgScopeResolver`（返回允许的组织 id 集合）、`FieldMappingTransformer`（字段映射转换）、`BizSnapshotResolver`（单 id 现查业务表）三个组件的核心逻辑不变，但服务对象从"变更记录的 bizId 单点查询"扩展为"业务表的条件化分页查询"，需要新增分页查询能力。

## Goals / Non-Goals

**Goals:**
- 删除 `tab_app_data_change_log` 表与相关全部代码，拉取/通知都不再依赖它。
- 拉取接口改为一个统一的分页查询接口：`page`/`pageSize`（默认 1/该应用该数据域配置的 `pageSize`）、按 `update_time ASC, id ASC` 排序、最后一页 `records` 返回空数组标识拉取完毕；响应是一个整页对象，顶层回显 `dataType`/`page`/`pageSize`/`dataSize`（本页实际条数）/`latestUpdateTime`（本页最大更新时间，供下一次增量拉取直接使用，见 Decision 9），`records` 里每条记录就是合并了 `bizId`/`bizCode`/`bizStatus`/`updateTime` 四个通用固定键的字段 Map 本身，不再是"元信息+data"的嵌套结构（见 Decision 1/2）。
- 支持增量拉取（`updateTimeFrom`/`updateTimeTo` 替代原序列号游标）与关键字段精确查询（`ids`/`codes`/`mobile`）。
- 拉取结果不过滤 `status`，停用/已删除记录原样返回，由调用方基于 `data.status` 自行判断。
- 通知触发逻辑改为数据变更时直接判定候选应用（复用现有"数据域启用+总开关+组织范围+`syncMode=NOTIFY`"判定规则）并直接发起，不经过任何持久化中转；通知请求体新增业务编码字段 `bizCode`，五种操作类型区分保留不变。
- 拉取日志（`tab_app_pull_record`）去掉"拉取方式"维度（只剩一种拉取方式，字段失去意义）。
- 新增字典（DICT）作为第六个可拉取数据域（拉取字典项，合并所属字典类型编码 `dictTypeCode`），任职（POSITION）数据额外合并关联用户编码 `userCode` 与关联组织编码 `orgCode`（见 Decision 7/8）。

**Non-Goals:**
- 不改变 `app-sync-master-switch`（同步总开关）、`app-sync-field-mapping`（字段映射）、`app-sync-org-scope`（组织范围配置）这几个已有能力的配置存储与语义，只是把它们的"生效时机"从写时判定改为读时判定（组织范围/字段映射本来就是读时判定，无需改动；总开关/数据域启用原来是写时判定的一部分，现在挪到拉取查询与通知候选判定各自的读时判定里）。
- 不提供旧接口的过渡期兼容（`proposal.md` 已确认），不做数据迁移/导出 `tab_app_data_change_log` 历史数据的功能——直接丢弃。
- 不改变通知请求的签名/验签机制、`tab_app_notify_record`（通知日志）的字段结构（除去掉 `change_log_id` 外）。
- 不引入分布式锁/强一致性保证：分页查询基于 offset+limit，理论上和"变更记录"方案一样存在并发写入导致的分页边界一致性问题（业界分页拉取的通用局限），本次不做游标分页（keyset pagination）之外的强化处理。

## Decisions

### Decision 1：拉取"不过滤 status"，靠业务表自身状态感知停用/删除；`bizStatus` 固定键保证该信息不受字段映射配置影响
组织/用户/任职/应用/角色五个业务实体全部是软删除（`status`：2000 启用/3000 停用/-1000 已删除，均由同一次 `UPDATE` 完成，`update_time` 会自动刷新），因此拉取查询按 `update_time` 增量扫描时，一条被停用/删除的记录会像普通编辑一样自然出现在增量结果里，只是状态字段变成 3000/-1000。调用方只要按这个字段自行处理，就能感知停用/删除，不需要额外的"删除记录"机制。这是能够安全删除变更记录表的关键前提——如果这五个实体存在任何物理删除，这个方案就不成立（物理删除的行不会再出现在任何后续查询里），但代码库现状确认没有物理删除路径。

初版方案假定"`status` 字段本来就在 `data`/记录里"，但这个假定只在该数据域**未配置任何字段映射**时成立（未配置时原样透传业务表全部字段）；一旦该应用为该数据域配置了字段映射（见「拉取结果按字段映射转换」需求：配置了映射后，输出 SHALL 只包含已映射的字段，以应用字段编码为键），除非管理员恰好显式把 `status` 也映射了一条，否则转换后的字段 Map 里根本不会有状态信息——这就让"不过滤 status，靠 data 里的 status 字段判断"这个方案在配置了字段映射的场景下失效。修正为：与 `bizId`/`bizCode`/`updateTime` 一样，新增一个固定键 `bizStatus`（取值即业务表原始 `status` 字段的整数码值：2000/3000/-1000），由系统在合并阶段直接从查询到的实体状态写入，不经过、也不受字段映射配置影响，保证无论该数据域是否配置了字段映射，调用方都能稳定地从这一个固定键判断记录当前是启用/停用/已删除。
- **备选方案**：拉取默认过滤掉停用/删除记录，另开一个"已删除 id 列表"接口。未采纳——用户已确认倾向"不过滤，靠状态字段自行判断"，且这个方案不需要任何新概念。
- **备选方案（关于 `bizStatus` 是否必要）**：不新增固定键，要求管理员在配置字段映射时必须记得把 `status` 也映射一条。未采纳——这是一个容易被管理员遗漏的隐性前提（配置字段映射时没有任何强制校验会提醒"你没有映射 status，外部应用将感知不到停用/删除"），一旦遗漏就会静默破坏"不过滤 status 靠外部应用自行判断"这个核心设计前提且不易察觉；做成固定键，行为不依赖配置正确性，更稳妥。

### Decision 2：拉取响应是一个带分页信息的整页对象，`dataType` 只在顶层出现一次，每条记录就是合并了 `bizId`/`bizCode`/`updateTime` 的 `data` 本身，不再是"元信息+data"的嵌套结构
拉取响应 SHALL 是一个整页对象（而不是裸的记录数组），顶层携带 `dataType`（本次请求的数据类型，整页只有一种，不需要在每条记录里重复）、`page`（本次实际使用的页码）、`pageSize`（本次实际使用的每页大小）、`records`（本页的数据列表）。调用方靠顶层回显的 `page`/`pageSize` 判断"我现在拉到第几页、下一页该传什么"，不需要自己在请求侧维护这个状态——这是本次修正的直接原因：最初的方案里拉取响应是裸列表，没有回显 `page`/`pageSize`，调用方翻页翻到一半失去上下文（比如重试请求）就无法确定自己在第几页。

`records` 的每一条元素直接就是该记录经字段映射转换后的业务字段 Map，不再额外包一层 `{dataType, bizId, bizCode, updateTime, data}` 的记录级信封——`dataType` 提到顶层后每条记录不需要再重复；`bizId`/`bizCode`/`updateTime`/`bizStatus`（新增，见 Decision 1）四个固定键合并（`put`）进这条记录的字段 Map 本身，与业务字段平铺在一起，不再单独作为 sibling 字段：先把字段映射转换后的业务字段放进结果 Map，再用这四个固定键名覆盖式写入（如果字段映射恰好配置了同名的应用字段编码，以固定键的值为准——顺序上无论是"固定键先放、业务字段后 `putAll`"还是反过来，最终都必须保证固定键的值不被字段映射结果覆盖，这是硬性要求，因为 `bizStatus` 存在的意义就是不受字段映射配置影响，如果允许被覆盖就违背了 Decision 1 的初衷）。`bizCode` 为空（POSITION 数据域）时该键 SHALL 仍然存在，值为 `null`；`bizStatus` 在五个数据域均 SHALL 有值，不为空。不省略这些键，保持每条记录的字段结构一致，便于调用方统一按键取值不用先判断键是否存在。

`sequence`/`operationType` 依然不提供（这部分维持第一版方案的判断不变）：`sequence` 是 `tab_app_data_change_log.id` 的对外别名，表删除后这个概念不存在，分页游标改用 `updateTime`；`operationType`（新增/编辑/启用/停用/删除）在旧模型里来自变更记录本身携带的操作类型，分页查询业务表当前状态天然不携带"上一次发生了什么操作"这个信息，"发生了什么操作"改由通知接口实时提供，拉取只负责"当前状态/当前页是什么样"。
- **备选方案（已否决，记录第一版设计的教训）**：拉取响应是裸的记录数组，每条记录是 `{dataType, bizId, bizCode, updateTime, data}` 的嵌套结构。未采纳——这是本次修正之前的初版实现，问题有二：① 没有在响应里回显分页信息，调用方无法确定自己拉到了第几页、该怎么继续翻页；② `dataType`/`bizId`/`bizCode`/`updateTime` 和 `data` 内部本来就有的同名字段（未配置字段映射时 `data` 直接透传业务表原始字段，含 `id`/`code`/`updateTime`）产生冗余，调用方要在两个地方核对同一份信息，增加了不必要的解析成本。
- **备选方案（关于 `changeType` 推断）**：拉取响应也带一个按 `createTime==updateTime` 推断的简化 `changeType`（NEW/UPDATED 二选一）。未采纳——这个推断在批量导入等场景下不准确（批量导入时 `createTime` 和 `updateTime` 未必精确相等到毫秒级），引入一个"看起来有用但不完全可信"的字段比不提供更容易误导调用方。

### Decision 3：拉取接口合并为一个，用 `dataType` 区分数据域，一套统一的过滤/分页参数
原来两个接口（按 id / 按序列号）现在的差异只剩"要不要传 `ids`/`codes`/`mobile` 精确过滤"，本质上是同一个分页查询的不同过滤条件组合，没有必要维持两个路径。新接口 `GET /open/api/sync/pull`：`dataType`（必填，可选值扩展为组织/用户/任职/应用/角色/字典六者，见 Decision 7）、`page`/`pageSize`（可选，默认见 Decision 5）、`updateTimeFrom`/`updateTimeTo`（可选）、`ids`（可选，逗号分隔）、`codes`（可选，逗号分隔，任职数据类型传入时系统 SHALL 忽略——POSITION 没有业务编码字段，不视为错误，见 tasks.md 校验细节）、`mobile`（可选，仅 `dataType=USER` 时生效）。多个过滤条件同时传入按交集处理。
- **备选方案**：按数据类型拆成独立接口路径（`/pull/org`、`/pull/user`…）。未采纳——各数据域的过滤能力有重叠（分页/更新时间范围通用），拆分路径只会重复大量样板代码，`dataType` 参数化更符合现有代码风格（现有 `BizSnapshotResolver`/`FieldMappingTransformer` 都是按 `dataType` 参数化 dispatch）。

### Decision 4：组织范围过滤下推到分页查询的 SQL `WHERE` 条件，而不是查出全量再在内存里过滤
`AppSyncOrgScopeResolver.resolveAllowedOrgIds(appRefId, syncDomain)` 已经返回 `Optional<Set<Long>>`（空 Optional 表示不限制，否则是允许的组织 id 全集，含子孙组织展开）。分页查询需要把这个 id 集合作为 SQL `WHERE ... IN (...)` 条件下推，而不是查出一页数据后再在 Java 里过滤——否则会出现"过滤后一页不满 pageSize 条，但业务表其实还有更多数据"的分页语义错误（调用方无法通过"这页数据不满"判断是否是最后一页，进而破坏"最后一页返回空标识拉取完了"这个约定）。三个数据域各自的过滤条件：
- ORG：`tab_org.id IN (allowedOrgIds)`（未配置指定范围时不加这个条件）。
- POSITION：`tab_user_position.org_id IN (allowedOrgIds)`。
- USER：`tab_user.id IN (SELECT DISTINCT user_id FROM tab_user_position WHERE status <> -1000 AND org_id IN (allowedOrgIds))`（复用 `AppSyncOrgScopeResolver.isUserWithinScope` 现有的"任一未删除任职落在范围内即命中"语义，改写成子查询下推到分页 SQL 里）。
- APP/ROLE：不做组织范围过滤（与现状一致）。

`allowedOrgIds` 集合本身在组织树很大时可能是个不小的 IN 列表，但这和现有 `AppSyncOrgScopeResolver` 的既有实现方式一致（现状已经是"展开成 id 集合"，不是本次改动引入的新问题），暂不做进一步优化。
- **备选方案**：把组织范围判断改写成基于 `parent_code`/路径前缀的 SQL 表达式，避免展开成 id 集合。未采纳——超出本次改动范围，`AppSyncOrgScopeResolver` 是独立能力，改动它的内部实现方式需要单独评估，本次只要求把现有产出（id 集合）正确下推到分页 SQL 里。

### Decision 5：`pageSize` 默认值取该应用该数据域配置的 `pageSize`（`tab_app_sync_domain_config.page_size`），请求显式传入时以显式值为准；`page` 默认 1
延续现有 `AppSyncDomainConfigEntity.pageSize`（管理端"数据范围"里逐数据域配置的"拉取分页大小"）的既定语义——它本来就是"这个数据域默认一次拉多少条"，本次改动不需要引入新的默认值配置项。调用方传入的 `pageSize` 非正数时，系统 SHALL 静默回退到该数据域配置值（与现有 `effectiveLimit` 的 fallback 行为一致），不视为参数错误。

### Decision 6：通知触发从"消费已落库记录"改为"数据变更时直接判定候选应用并发起"
`DomainChangeEventProcessor.process(event)` 不再调用 `AppDataChangeLogService.record()`，而是直接：① 查候选应用（`NotifyTargetMapper`，SQL 增加 `sync_mode='NOTIFY'` 条件——PULL 模式应用不需要参与候选匹配，因为它们不接收通知，拉取行为完全由自己按需发起的分页查询决定，不需要"被匹配"这个概念）；② 对组织/用户/任职三个数据域应用现有组织范围过滤（原 `AppDataChangeLogServiceImpl.filterByOrgScope` 的逻辑原样迁移到新的判定组件，如重命名为 `NotifyCandidateResolver`）；③ 对每个匹配应用直接调用通知，通知请求体的 `bizId`/`bizCode`/`dataType`/`operationType`/`occurredAt` 全部来自 `DomainChangeEvent` 本身（`operationType` 原样保留，来自事件真实携带的值，不受这次改动影响）+ 一次现查业务表拿到 `bizCode`（复用 `BizSnapshotResolver` 现有的单 id 现查逻辑，取其 `code` 字段；POSITION 数据域没有 `code` 字段，`bizCode` 为空）。Disruptor 消费者仍然在事务提交后异步执行（`DisruptorDomainEventPublisher` 现有的 `afterCommit` 延迟发布机制不变，继续避免消费者线程读到未提交数据的竞态）。
- **备选方案**：仍然保留一个轻量"候选应用匹配"持久化表，只是不再存储"变更记录"本身。未采纳——用户明确要求的是"应用数据拉取不再依赖数据变更记录表"，如果只是换一张表存差不多的东西，没有解决"应用同步一会儿停用一会儿启用难以维护一致性"这个根本诉求；直接判定候选应用是幂等的（同一个应用配置在判定那一刻的状态就是唯一依据），不存在"表里的候选记录和当前配置对不上"的问题。

### Decision 7：新增字典（DICT）作为第六个可拉取数据域，拉取的是字典项（`tab_dict_item`），额外合并 `dictTypeCode` 固定键
`tab_app_sync_domain_config` 本来就固定给每个应用存 6 行配置（组织/用户/任职/应用/角色/字典），"字典"这个数据域槽位一直存在，只是此前 `SyncDomain.SYNC_PULL_DOMAINS`（原 `CHANGE_LOG_DOMAINS`）没有把它纳入拉取/通知的合法数据类型集合，导致字典数据域的启用开关/分页大小配置形同虚设。本次把 `DICT` 加入 `SYNC_PULL_DOMAINS`，拉取的业务表是 `tab_dict_item`（字典项，如"男/女"这类具体值）而不是 `tab_dict_type`（字典类型，如"性别"这个分类本身）——外部应用要同步落地使用的是具体的字典值，不是分类元数据（用户已确认这个方向）。字典项的 `code` 只在同一个 `dictTypeId` 下唯一、不是全局唯一，因此除了作为该记录的 `bizCode` 固定键之外，还需要额外合并一个 `dictTypeCode` 固定键（该字典项所属字典类型的编码），调用方靠 `dictTypeCode` + `bizCode` 两者组合才能唯一定位一个字典值，单看 `bizCode` 可能在不同类型下重复。`dictTypeCode` 通过批量查询 `tab_dict_type`（按本页出现的 `dictTypeId` 去重后 `selectBatchIds`）解析得到，与 Decision 8 的 `userCode` 解析方式一致，不引入新的 JOIN 语法。

字典数据域 SHALL NOT 参与组织范围过滤（`SyncDomain.ORG_SCOPE_DOMAINS` 本来就不含 `DICT`，与 APP/ROLE 现状一致），也 SHALL NOT 纳入字段级同步映射能力（`SyncDomain.FIELD_MAPPING_DOMAINS` 本次不变，仍不含 `DICT`——管理端"数据范围"页面字典数据域一直就只展示"是否启用"一个二级 tab，没有"字段映射"二级 tab，这次不改变这个既有约定）；`FieldMappingTransformer` 对没有任何已配置映射规则的数据域，天然按"未配置字段映射时原样返回完整字段快照"处理，不需要为 `DICT` 写任何特判代码。

- **备选方案**：拉取字典类型（`tab_dict_type`）本身，或者字典类型+字典项都拉（拆成两个 `dataType`）。未采纳——用户已确认选择"拉取字典项，带上所属字典类型编码"，这个方案外部应用一次拉取就能拿到可直接使用的键值对，不需要先拉类型列表再逐类型拉项目，减少调用方需要发起的请求数量。
- **备选方案**：把字典也纳入组织范围过滤/字段映射能力。超出本次改动范围——用户的诉求是"添加字典拉取数据"，不是"重新设计字典的组织范围/字段映射能力"，且字典数据本来就是全局共享的元数据（不像组织/用户那样天然归属某个组织），没有组织范围的概念。

### Decision 8：任职（POSITION）拉取数据额外合并 `userCode`/`orgCode` 两个固定键，不新增数据库字段
`tab_user_position` 没有自己的业务编码字段，此前 `bizCode` 对 POSITION 数据域恒为 `null`。用户已确认不新增持久化的"任职编码"列，而是让任职的拉取数据"默认包含"其关联用户的业务编码（`userCode`）；后续又补充要求同时包含关联组织的业务编码（`orgCode`）——一条任职记录的完整业务身份是"某个用户在某个组织的一次任职"，`userId`/`orgId` 两个外键都缺业务编码，两者应当同等对待，一起补齐。实现方式与 Decision 7 的 `dictTypeCode` 一致：`SyncBizPageQueryResolver` 查到一页 `UserPositionEntity` 后，分别收集本页出现的 `userId`、`orgId` 两个去重集合，各一次批量查询（`UserMapper.selectByIds(...)`、`OrgMapper.selectByIds(...)`，`OrgMapper` 已经是该组件处理 ORG 数据域时注入的既有依赖，不需要新增）拿到 `userId -> code`、`orgId -> code` 两个映射，逐行回填，不逐行单独查询（避免 N+1，`orgCode` 与 `userCode` 走同一套批量回填模式）。

`userCode`/`orgCode`/`dictTypeCode` 与 `bizId`/`bizCode`/`bizStatus`/`updateTime` 四个通用固定键不同——后者是六个数据域都适用的通用概念，每条记录都会出现这四个键（值可能为 `null`，但键本身恒存在）；`userCode`/`orgCode`/`dictTypeCode` 是只对特定数据域有意义的领域特定固定键，SHALL 仅在对应数据域（`userCode`/`orgCode` 仅 POSITION，`dictTypeCode` 仅 DICT）的记录中出现，其余数据域的记录 SHALL NOT 包含这些键（不是"键存在但值为 null"，而是键本身不出现），避免在语义上不相关的记录里（比如一条组织记录）挂着一个 `userCode: null` 让调用方费解。这些键与四个通用固定键遵循同一条"不被字段映射结果覆盖"的合并规则（最后写入，覆盖式）。

`codes` 请求参数的既有语义（"按数据类型对应的业务编码字段过滤"）保持不变，不因为 POSITION 现在多了 `userCode`/`orgCode` 这两个关联字段就把 `codes` 过滤条件扩展到按它们过滤——`codes` 过滤的仍然是"这条记录自己的业务编码"，POSITION 依然没有这个概念，传入 `codes` 参数时系统仍然 SHALL 忽略（沿用 Decision 3 已确立的行为）。
- **备选方案**：给 `tab_user_position` 新增一个持久化的"任职编码"列（类似组织编码/用户编码那样系统自动生成或人工填写）。未采纳——用户已确认不需要新增字段，任职记录的业务身份本来就是"某个用户在某个组织的一次任职"，没有必要为了满足这次拉取需求单独发明一个新的业务编码概念。
- **备选方案**：把 `userCode`/`orgCode` 塞进 `bizCode` 这个已有的通用固定键里（比如拼接成 `orgCode-userCode`）。未采纳——`bizCode` 在其余数据域的语义都是"这条记录自己的业务编码"，POSITION 复用这个键去装拼接值会让调用方困惑且难以拆解回各自的编码；用两个语义明确的独立键 `userCode`/`orgCode` 更清晰，`bizCode` 对 POSITION 继续保持 `null`（沿用现状）。

### Decision 9：响应顶层新增 `latestUpdateTime`（本页记录最大更新时间）与 `dataSize`（本页记录条数）
`page`/`pageSize` 只能告诉调用方"这次分页参数是什么"，不能直接告诉调用方"下一次增量拉取的 `updateTimeFrom` 该传什么"——调用方要么自己在 `records` 里遍历取最大 `updateTime`，要么继续用纯 `page` 自增翻页（但翻页天然不是增量游标，业务表持续写入时旧数据会不断往后挪，纯翻页在增量场景下并不可靠）。响应顶层新增 `latestUpdateTime`，取值为本页 `records` 中最大的 `updateTime`（即最后一条记录的 `updateTime`——`records` 本来就按 `updateTime ASC, id ASC` 排序，取最后一条即可，不需要额外遍历比较）；调用方增量拉取的推荐用法是：本次拿到 `latestUpdateTime` 后，下一次请求把它原样传给 `updateTimeFrom`。`records` 为空（翻到最后一页/未开通/总开关关闭等场景）时 `latestUpdateTime` SHALL 为 `null`。

同时新增 `dataSize`，取值为本页 `records` 的实际条数（即 `records.size()`），与 `pageSize`（本次请求的每页大小上限）是两个不同的概念——`dataSize` 可能小于 `pageSize`（最后一页不满页时），调用方不需要自己数 `records` 数组长度。

这两个新字段与 `dataType`/`page`/`pageSize` 同级，都放在响应顶层，不进入 `records` 的每条记录里（这是"整页范围的元信息"，不是"单条记录的字段"）。
- **备选方案**：不新增 `latestUpdateTime`，让调用方自己从 `records` 最后一条记录的固定键 `updateTime` 读取。未采纳——用户明确要求响应顶层直接提供，减少调用方自己写"取数组最后一条的某个字段"这种样板逻辑，且顶层字段语义更清晰（一眼看出这是"本页游标推进到哪了"，不需要读者自己推导"最后一条记录的 updateTime 就是最大值"这个隐含前提）。

## Risks / Trade-offs

- [Offset 分页（`page`/`pageSize`）在业务表持续写入的情况下，理论上可能出现同一条记录跨两次拉取请求被重复返回、或极少数记录被跳过（经典的 offset 分页一致性局限，keyset 分页可以缓解但复杂度更高）] → 接受该权衡：变更记录方案本身也没有对"拉取过程中又发生了新变更"做强一致性保证（序列号游标同样可能因为并发写入顺序而有类似的边界情况），且调用方本来就应该支持"重复拉到同一条记录、按 `data` 内容幂等处理"这种消费模式（外部系统按 `bizId` upsert 即可），不因为这次改动新增实质性风险。
- [组织范围下推到 SQL 后，`AppSyncOrgScopeResolver.resolveAllowedOrgIds` 展开出的 id 集合如果非常大（比如上万个组织节点），拼进 `IN (...)` 可能有性能/SQL 长度问题] → 现状本来就是这个实现方式（只是之前用在"判断单个 id 是否在集合里"，改动后用在"IN 列表过滤查询"，量级敏感度更高），本次不额外优化，作为已知风险记录；如果后续遇到实际性能问题，再单独评估把组织范围判断改写成 SQL 子查询/JOIN 的方案（Decision 4 备选方案）。
- [破坏性变更：旧的两个拉取接口路径直接下线，没有过渡期] → 用户已在澄清问题里明确选择"直接替换，不兼容旧接口"，本次改动前该能力尚未有已知的外部系统实际接入（`app-sync-notify-pull` 相关变更都还在同一个迭代周期内持续演进），风险可控。

## Migration Plan

新增 Flyway 迁移脚本 `V7__drop_app_data_change_log.sql`：
1. `DROP TABLE tab_app_data_change_log;`
2. `ALTER TABLE tab_app_notify_record DROP INDEX idx_tab_app_notify_record_change_log_id, DROP COLUMN change_log_id;`
3. `ALTER TABLE tab_app_pull_record DROP COLUMN pull_mode;`

不提供 DOWN 脚本（与仓库现有迁移脚本风格一致）。这是破坏性的表删除，一旦执行不可回滚数据（`tab_app_data_change_log` 里的历史变更记录会被永久丢弃）——按 proposal.md 的确认，这是本次改动明确要达成的效果（不再需要这张表），执行前无需数据备份/导出（该表历史数据本来就只是"指针"，不含具体业务数据）。

## Open Questions

（无）
