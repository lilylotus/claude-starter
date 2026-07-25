## Why

任职（POSITION）批量导入目前只能靠"人员编号"（`tab_user.code`）匹配出所属用户，但业务上准备
导入数据时往往还不知道、或编号后续会变更，反而手机号、身份证号是数据整理阶段更容易拿到、更
稳定的标识。需要放宽人员标识列的匹配规则，让同一列可以用编号、手机号或身份证号中的任意一种
值来定位用户，而不强制要求编号。

## What Changes

- POSITION 批量导入行中"人员标识"列（固定标识列，`fieldCode=__userCode`）的匹配逻辑从
  "仅按 `tab_user.code` 精确匹配"扩展为"按 `tab_user.code`、`mobile`、`idCard` 三者任一
  精确相等即命中"，命中零条或匹配到多个不同用户时的失败判定逻辑保持不变（分别提示"无法匹配到
  已有人员记录"与"匹配到多条已存在记录"）。
- 该固定列本身、字段标识（`__userCode`）、是否主键/必填等既有的系统保护约束不变；管理员仍可
  在"表单管理"页面的"导入模板配置"里自行把该列的 Excel 表头文字从默认的"人员编号"改得更贴切
  （如"人员标识"），不属于本次强制的数据变更。
- 不改动应用（APP）导入"负责人编号"列的匹配逻辑，本次只针对任职导入。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `excel-import-export`：修改"任职导入的人员与组织标识映射"需求，人员标识的匹配字段从仅
  `code` 扩展为 `code`/`mobile`/`idCard` 任一匹配。

## Impact

- 后端：`backend/src/main/java/cn/nihility/rbac/excelimport/service/support/ImportRowExecutor.java`
  的 `processPosition()` 方法，查询条件从 `code = 值` 改为 `code = 值 OR mobile = 值 OR idCard = 值`。
- 不涉及数据库结构或种子数据改动，不涉及前端改动（Excel 表头文字已经是管理员可自行调整的既有
  配置项，模板生成逻辑本就动态读取该配置，不需要改代码）。
- 风险提示：`tab_user.mobile` 目前没有唯一性约束（不同于 `code`/`idCard`），如果多个未删除
  用户手机号相同，用该手机号作为人员标识会命中多条记录，按既有"匹配到多条已存在记录"规则判定
  该行导入失败——这是可接受的安全退化，不会误绑定到错误的人员。
