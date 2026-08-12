## Why

`backend/src/main/resources/db/migration/` 目录自上一次基线合并（2026-08-03 的 `consolidate-flyway-migrations-v2`）以来又累积了 10 个增量版本（`V2`~`V11`），涵盖应用对外接口配置、同步配置、字段映射、角色元数据字段、组织上级编码等多个 change 的增量变更。`openspec/specs/backend-common-utilities/spec.md` 的"Flyway 迁移目录保持单一基线"需求明确允许"当增量迁移文件积累到影响可维护性的程度时，仿照本次操作重新合并出一份新的基线"，本次即是该需求预见到的重复操作。

## What Changes

- 把 `V1__init_schema.sql` ~ `V11__org_parent_code_metadata_field.sql` 共 11 个文件，按当前最终数据库状态（全部建表语句 + 全部种子数据）重新整理合并为一份新的 `V1__init_schema.sql` 基线文件。
- 删除 `V2`~`V11` 共 10 个增量文件（`git rm`，历史仍可通过 git 提交记录追溯）。
- 不改变任何业务代码、接口行为，纯粹是迁移文件的组织方式调整；合并后的最终 schema、种子数据（含全部表结构、菜单树、权限点、角色权限关联、元数据字段目录等）与旧 `V1~V11` 按顺序执行后的最终状态完全一致。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `backend-common-utilities`：合并后基线表数量从 19 张增至 22 张（新增 `tab_app_config`/`tab_app_sync_domain_config`/`tab_app_sync_field_mapping`），"Flyway 迁移目录保持单一基线"需求里"新环境执行迁移只需应用基线文件"场景的表数量描述需要同步更新；需求正文本身已经预见并允许本次合并操作，不需要改动。

## Impact

- 后端：`backend/src/main/resources/db/migration/` 目录下全部文件。
- 不涉及生产环境或任何已经执行过旧版本迁移的库的升级路径——本次改动假定使用方清空库（或删除 `flyway_schema_history` 表）后重新执行新基线，与前两次合并的既有约定一致。
