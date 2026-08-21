## Why

`backend/src/main/resources/db/migration/` 目录自当前 `V1__init_schema.sql` 基线成型以来又累积了 9 个增量版本（`V2`~`V10`），涵盖单点登录用户信息字段映射、登出通知、认证协议回跳地址存储统一、同步通知/拉取日志、同步总开关、下线变更记录表、应用访问授权（策略+人工例外+请求控制条件）等多个 change 的增量变更。`openspec/specs/backend-common-utilities/spec.md` 的"Flyway 迁移目录保持单一基线"需求明确允许"当增量迁移文件积累到影响可维护性的程度时，仿照本次操作重新合并出一份新的基线"，本次即是该需求预见到的重复操作（第 4 次以 OpenSpec change 记录的合并，此前 `consolidate-flyway-migrations`/`-v2`/`-v3` 各一次）。

## What Changes

- 把 `V1__init_schema.sql` ~ `V10__add_app_access_policy_request_control.sql` 共 10 个文件，按当前最终数据库状态（全部建表语句 + 全部种子数据）重新整理合并为一份新的 `V1__init_schema.sql` 基线文件。
- 删除 `V2`~`V10` 共 9 个增量文件（`git rm`，历史仍可通过 git 提交记录追溯）。
- 不改变任何业务代码、接口行为，纯粹是迁移文件的组织方式调整；合并后的最终 schema、种子数据（含全部表结构、菜单树、权限点、角色权限关联等）与旧 `V1~V10` 按顺序执行后的最终状态完全一致。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `backend-common-utilities`：合并后基线表数量为 40 张（现有 31 张——`openspec/specs/backend-common-utilities/spec.md` 里记录的 22 张这一数字早已因此前未记录在案的整合提交而漂移，此次一并更正，`V2` 新增 1 张、`V5` 新增 1 张、`V7` 删除 1 张、`V8` 新增 6 张、`V10` 新增 2 张，净增 9 张）。"Flyway 迁移目录保持单一基线"需求里"新环境执行迁移只需应用基线文件"场景的表数量描述需要同步更新为 40；需求正文本身已经预见并允许本次合并操作，不需要改动。

## Impact

- 后端：`backend/src/main/resources/db/migration/` 目录下全部文件。
- 不涉及生产环境或任何已经执行过旧版本迁移的库的升级路径——本次改动假定使用方清空库（或删除 `flyway_schema_history` 表）后重新执行新基线，与前三次合并的既有约定一致。
