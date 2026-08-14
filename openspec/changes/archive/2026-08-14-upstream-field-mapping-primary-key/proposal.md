## Why

`identity-upstream-data-sync` 能力当前把落库匹配键写死在代码里：组织/用户固定按 `code` 匹配，任职固定按（已解析的所属人员+组织）加上"如果提供了 `positionType` 就按它匹配、否则任选一条已有记录"这种不严谨的兜底逻辑匹配。这在上游数据没有对应字段、或管理员想用别的字段（如身份证号、外部系统主键、多字段组合）作为唯一标识时无法配置，且任职域"不提供 `positionType` 就随便匹配一条"的行为本身也不安全（同一人在同一组织有多条任职记录时会误更新）。需要把匹配键改为在字段映射里可配置，支持单字段或多字段联合主键。

## What Changes

- 字段映射每行新增"主键标识"勾选项，同一数据源同一数据域下可以勾选一个或多个字段组成联合主键（全部标记字段的值都相等才算命中同一条本地记录）。
- 保存字段映射时，若本次提交的列表非空，系统 SHALL 要求至少勾选一个主键字段，否则拒绝保存。
- 同步落库匹配逻辑改为：组织/用户按标记为主键的字段（转换后的系统字段值）做匹配查询，替换掉写死按 `code` 匹配的旧逻辑；任职在现有 `__userIdentifier`/`__orgCode` 伪字段解析出所属人员/组织的基础上，再叠加标记为主键的字段作为同一人员同一组织下的区分条件，替换掉"提供了 `positionType` 就按它匹配、否则任选一条"的旧逻辑。
- 标记为主键的字段，转换后取值为空时该行判定为失败（无法可靠匹配/新增）。
- 兼容性处理：本次改动上线前已保存的字段映射不会有任何字段被标记为主键（新增列默认值），同步执行引擎在处理某个已启用数据域前 SHALL 先检查该数据域是否配置了至少一个主键字段，未配置时直接判定本次该数据域同步失败并给出清晰提示，不会用空匹配条件误伤本地数据。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `identity-upstream-data-sync`：
  - "上游字段映射配置"需求新增"主键标识"字段与至少勾选一个的校验规则。
  - "数据落库匹配与新增/更新语义"需求的匹配规则从写死的 `code`/`positionType` 改为按字段映射标记的主键字段动态匹配（支持联合主键），并补充"未配置主键字段时同步判定失败"的规则。
  - "组织/任职数据域的固定伪字段编码约定"需求里关于任职匹配逻辑的措辞需要同步更新（伪字段仍然负责解析所属人员/组织这层外键关系，不变；但"同一人员同一组织下具体匹配哪一条任职记录"这部分改用主键字段判定）。

## Impact

- 数据库：`tab_upstream_field_mapping` 新增 `is_primary_key` 列（新开一个 Flyway 版本号迁移，不回改已执行过的 `V4`/`V5`/`V6`）。
- 后端：`UpstreamFieldMappingRow`/`UpstreamFieldMappingVO`/`UpstreamFieldMappingSaveRequest` 新增 `isPrimaryKey` 字段，`UpstreamFieldMappingMapper.xml` 联表查询补上该列；`UpstreamFieldMappingServiceImpl` 新增"至少一个主键"校验；`UpstreamRowUpserter`（组织/用户/任职三处匹配逻辑改为按主键字段动态查询，方法签名需要接收主键字段列表）、`UpstreamSyncExecutor`（同步前置校验"已启用数据域至少有一个主键字段"，未通过时直接记一条 FAILED 执行记录）。
- 前端：`UpstreamSourceConfigView.vue` 字段映射表格新增"主键标识"勾选列，保存前做与后端一致的前端校验。
- 不涉及对外接口契约变化之外的新增依赖；沿用 MyBatis-Plus 已有的 `StringUtils.camelToUnderline` 工具做字段编码到数据库列名的转换，不新增依赖。
