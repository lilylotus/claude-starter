## Why

`2026-07-23-add-form-field-date-multiselect-dict` 那次改动已经修复了 `ext1`~`ext10` 扩展字段
在操作日志变更快照里"展示原始字典编码而不是标签"的问题（`FormFieldSnapshotSupport`），但
用户主数据的"性别"（`UserEntity.gender`）与任职记录的"任职类型"
（`UserPositionEntity.positionType`）这两个非 `ext` 的核心业务列——虽然同样绑定了字典类型
（`gender`/`position_type`）——当时不在修复范围内。用户反馈"用户详细操作历史性别记录是字段
的 code 不是值"，排查发现 `UserServiceImpl.toLogSnapshot()` 和
`PositionLogSnapshotSupport.snapshot()` 里这两个字段都是把 `entity.getGender()`/
`entity.getPositionType()` 的原始字典编码直接放进快照，没有像 `ext` 字段那样解析成标签，是
同一类缺陷在核心列上的遗漏。

## What Changes

- `UserServiceImpl.toLogSnapshot()`：新增私有方法 `genderLabel()`，按字典类型 `gender` 把
  性别存储的编码解析为标签写入操作日志快照，查不到（字典项已停用/删除）时回退展示原始编码。
- `PositionLogSnapshotSupport.snapshot()`（被独立任职管理入口 `PositionServiceImpl` 与用户
  管理内嵌任职子表单 `UserServiceImpl.syncPositions` 共用）：新增私有方法
  `positionTypeLabel()`，按字典类型 `position_type` 做同样的编码→标签解析，两个调用入口
  产生的日志同步受益。
- 不改数据库结构、不改这两个字段的存储格式（数据库里仍然存字典编码），只改操作日志快照
  这一层的展示值；也不改这两个字段在业务表单/详情页面的展示逻辑（前端已经是按标签展示，
  未受影响）。

## Capabilities

### New Capabilities
（无。）

### Modified Capabilities
- `form-field-definition-management`：「操作日志的字典字段值展示为标签而非编码」需求扩大
  覆盖范围，从"仅 `ext1`~`ext10` 扩展字段"扩展到"用户性别、任职记录任职类型这两个绑定字典
  类型的核心业务列"。

## Impact

- **后端代码**：`cn.nihility.rbac.user.service.impl.UserServiceImpl`、
  `cn.nihility.rbac.user.service.support.PositionLogSnapshotSupport`。
- **数据库**：无结构或数据改动。
- **风险**：纯读路径（操作日志快照生成）的展示值修正，不影响写入、不影响其余字段；已通过
  `./gradlew compileJava` 验证编译通过。
