## Why

上游组织数据域解析"上级组织编码"目前依赖硬编码的固定伪字段编码 `__parentCode`：管理员必须让上游数据（`API` 响应 JSON 或 `DB_TABLE` 查询列别名）严格携带这个固定命名的列，取值不经过字段映射转换，直接从取数阶段的原始行读取。这与其余系统字段（组织编码、组织名称等）"通过字段映射自由指定上游字段编码、可配置转换方式"的配置体验不一致——上级组织编码反而是唯一一个不能改名、不能配置转换（如上游给的是纯数字 ID 而不是编码，需要脚本转换）的特殊字段。同时，`tab_metadata_field` 里 ORG bizType 下其实已经存在一个 `parentCode`（对应 `tab_org.parent_code`，"恒等于 parentId 对应父组织当前的 code"）的常规元数据字段，管理员在字段映射页面能看到并选择它，但选中后是无效操作——因为 `OrgCreateRequest`/`OrgUpdateRequest` 没有可写的 `parentCode` 属性，落库时被 `UpstreamRowUpserter.bindProperties` 静默跳过，实际不生效，属于一个隐蔽的配置陷阱。

## What Changes

- 上游组织数据域解析"上级组织编码"改为复用常规字段映射机制：管理员和其余字段一样，在字段映射里选择上游字段编码 → 目标系统字段 `parentCode`（ORG bizType 已有的元数据字段），可自由指定上游字段编码名称、可配置转换方式（`NO_TRANSFORM`/`FIXED_VALUE`/`SCRIPT`）。
- 移除组织数据域的固定伪字段编码 `__parentCode` 约定（`UpstreamOrgPseudoFieldCode` 及其在 `UpstreamRowUpserter`/前端提示文案中的引用）。**BREAKING**：已经配置了依赖 `__parentCode` 列的上游数据源，升级后需要管理员改为在字段映射里配置一行映射到 `parentCode` 系统字段，否则上级组织信息不再被解析（视为未配置，全部落为顶级组织，不判定失败——与"parentCode 字段映射可选，未配置时不解析上级组织"的新语义一致，但会改变原本非顶级组织的层级结果，需要管理员知悉并主动迁移配置）。
- 落库匹配语义不变：`parentCode` 转换后取值为空（`null`/空白字符串）时该行的上级组织判定为顶级组织（`parentId=0`，不判定失败）；取值非空时按 `tab_org.code` 精确匹配已有组织，匹配不到该行判定失败。字段映射里配置 `parentCode` 是可选的（不像"主键标识"字段那样强制要求至少一个），不配置时等价于该数据域全部记录都视为顶级组织。
- 任职（`POSITION`）数据域现有的 `__userIdentifier`/`__orgCode` 固定伪字段约定不受影响，继续保留——`userId`/`orgId` 是外键关系，不是 `POSITION` bizType 下可开放配置的元数据字段，没有等价的常规字段映射路径可替代。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `identity-upstream-data-sync`：
  - "组织/任职数据域的固定伪字段编码约定"需求：移除组织数据域 `__parentCode` 伪字段的相关描述，明确只保留任职数据域的 `__userIdentifier`/`__orgCode` 两个固定伪字段。
  - "数据落库匹配与新增/更新语义"需求：补充"上级组织编码通过字段映射配置的 `parentCode` 系统字段解析，为空视为顶级组织，非空匹配不到判定失败"的规则，替换原先"由固定伪字段解析"的措辞。

## Impact

- 后端：`UpstreamOrgPseudoFieldCode` 类删除或废弃；`UpstreamRowUpserter.upsertOrg`/`resolveParentId` 改为从字段映射转换后的行（`row.get("parentCode")`）而非原始行读取上级组织编码；方法签名可能不再需要专门为组织传入 `rawRow`（任职域仍需要 `rawRow` 解析 `__userIdentifier`/`__orgCode`，组织域取数原始行参数视实现需要保留或去除）。
- 前端：`UpstreamSourceConfigView.vue` 里组织数据域"是否启用"分区下关于 `__parentCode` 伪字段的 `el-alert` 提示文案需要移除；`upstreamSource.ts` 中 `UPSTREAM_ORG_PARENT_CODE_FIELD` 常量删除或废弃。
- 数据库：无新增迁移（`parentCode` 元数据字段与 `tab_org.parent_code` 列已存在，不需要新表结构）。
- 测试：`UpstreamRowUpserterTest` 中依赖 `UpstreamOrgPseudoFieldCode.PARENT_CODE`/原始行读取上级组织编码的既有用例需要改写为通过转换后行的 `parentCode` 键传值。
- 不涉及新增第三方依赖。
