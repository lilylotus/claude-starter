## Context

见 proposal.md - Why。当前 `UpstreamRowUpserter.upsertOrg` 通过 `resolveParentId(rawRow)` 从取数阶段的**原始行**（`rawRow`，key 为上游字段编码）按固定伪字段编码 `UpstreamOrgPseudoFieldCode.PARENT_CODE`（`__parentCode`）读取上级组织编码；`bindProperties` 只处理**转换后的行**（`row`，key 为系统字段编码）。`tab_metadata_field` 中已存在 `biz_type=ORG, field_code=parentCode`（列 `parent_code`）的常规元数据字段，管理员在字段映射页面能选到它，但 `OrgCreateRequest`/`OrgUpdateRequest` 没有可写的 `parentCode` 属性，`bindProperties` 遍历转换后行时对它调用 `wrapper.isWritableProperty("parentCode")` 返回 `false`，直接跳过——选中即无效，是个隐蔽陷阱。

## Goals / Non-Goals

**Goals:**
- 上级组织编码的取值来源从"原始行按固定伪字段编码读取"改为"转换后行按系统字段编码 `parentCode` 读取"，与其余系统字段一样支持自定义上游字段编码与转换方式。
- 移除对管理员无效的 `parentCode` 选项陷阱：选中后确实生效。
- 保持既有的"空值→顶级组织，不判定失败；非空→按 code 匹配，匹配不到判定失败"语义不变。

**Non-Goals:**
- 不改动任职（POSITION）数据域的 `__userIdentifier`/`__orgCode` 伪字段机制——`userId`/`orgId` 是外键，POSITION bizType 下没有对应的常规元数据字段，没有替代路径。
- 不新增元数据字段或数据库迁移——`parentCode` 元数据字段已存在。
- 不限制 `parentCode` 是否可以同时被勾选为"主键标识"字段——两者是正交的配置项，本次不新增互斥校验。
- 不处理历史数据源升级后的自动迁移——已依赖 `__parentCode` 的数据源，管理员需要手工到字段映射里补配一行映射到 `parentCode`（proposal.md 已标注 **BREAKING**）。

## Decisions

### Decision 1：`resolveParentId` 从原始行改为读取转换后行的 `parentCode` 键，删除 `UpstreamOrgPseudoFieldCode`
`upsertOrg` 不再需要 `rawRow` 参数（POSITION 仍需要，保留）；`resolveParentId(Map<String, Object> row)` 直接从 `bindProperties` 使用的同一份转换后行里取 `row.get("parentCode")`。取值为空（`null`/空白字符串）视为顶级组织（`parentId=0`），非空按 `tab_org.code` 精确匹配、匹配不到判定失败——判定逻辑本身不变，只是数据来源换了。`UpstreamOrgPseudoFieldCode` 类不再有任何引用，直接删除（而不是保留但废弃——仓库约定"确定不再使用就整块删除，不留 `// removed` 之类的兼容性注释"）。

- **备选方案**：保留 `__parentCode` 伪字段作为 `parentCode` 字段映射未配置时的兜底数据源。未采纳——两条平行路径（伪字段 + 字段映射）会互相打架（同时配置时听谁的？），且违背本次改动"统一到字段映射机制"的目的，增加管理员的心智负担。

### Decision 2：`parentCode` 字段映射保持"可选"，不要求配置
不像"主键标识"那样强制"非空列表至少一个"，因为并非所有组织数据源都有层级关系（管理员可能只想同步一批平级组织）。未配置 `parentCode` 映射行时，字段映射转换结果里天然没有 `parentCode` 键，`row.get("parentCode")` 返回 `null`，等价于"取值为空→顶级组织"，不需要在 `UpstreamFieldMappingServiceImpl.assertRequestsValid` 里加新校验，行为在现有转换管线下自然成立。

### Decision 3：前端同步移除 `__parentCode` 相关的提示文案与常量
`UpstreamSourceConfigView.vue` 组织数据域"是否启用"分区下的 `el-alert`（提示"上级组织编码可选：取数结果如包含固定编码列「`__parentCode`」……"）整块删除；`upstreamSource.ts` 的 `UPSTREAM_ORG_PARENT_CODE_FIELD` 常量删除。任职数据域的 `__userIdentifier`/`__orgCode` 提示保留不变。

## Risks / Trade-offs

- [风险] **BREAKING**：已经配置了 `__parentCode` 列的存量数据源，升级后下一次同步会把所有组织都同步为顶级组织（不再判定失败，是静默的层级丢失，而不是报错）——管理员如果没注意到这次改动，可能过一段时间才发现组织树"变平了" → 缓解：这是本次改动的既有取舍（proposal.md 已标注 BREAKING），无法在不了解管理员数据源实际取数配置的前提下自动迁移；建议 `openspec-doc-sync`/PR 描述里明确提示存量数据源需要手工到字段映射补配 `parentCode` 映射行。
- [风险] 管理员如果误将 `parentCode` 同时勾选为"主键标识"字段，会导致按上级组织编码而不是组织自身编码去匹配"是否已存在"，容易造成语义混乱（如两个不同的子组织如果共享同一个上级，会被误判为同一条记录） → 缓解：不在系统层面禁止（`parentCode` 本身是合法的 ORG 系统字段，禁止会显得武断），依赖管理员正确理解"主键标识"应选真正能唯一标识本条记录的字段；本次不做进一步兜底。
