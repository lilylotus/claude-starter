## Why

RBAC 系统里越来越多字段属于"枚举类"数据（如即将新增的用户管理模块里"认证类型：主职/兼职/挂职"），如果继续像 `OrgStatus` 那样为每个枚举单独写死后端常量，会导致新增/调整枚举值都要改代码重新发版。项目目前没有任何字典能力，`系统管理` 菜单下只有"菜单管理""操作日志"两个占位页面。本 change 补齐一个通用的字典管理模块，作为后续所有枚举类字段的统一数据来源，第一个消费方是即将建设的用户管理模块的"认证类型"字段。

## What Changes

- 新增后端字典模块 `cn.nihility.rbac.dict`：字典类型（dict type）与字典项（dict item）的增删改查、启用/停用、逻辑删除，分层结构参照 `cn.nihility.rbac.org` 模块（entity/constant/mapper/dto/service/controller/mapstruct），复用 `common` 包的统一响应与全局异常处理。
- 新增只读查询接口：按字典类型编码查询其下全部启用状态的字典项列表，供业务模块的下拉框场景调用（不需要感知分页、不需要管理权限）。
- 新增 Flyway 迁移脚本，建表 `tab_dict_type`、`tab_dict_item`。
- 新增前端字典管理页面 `/system/dicts`：左侧字典类型列表 + 右侧选中类型的字典项分页表格（主从结构，参考组织管理页面的左右布局，但左侧是普通列表而非树），支持字典类型与字典项各自的增删改查、启停用。
- `router/menu.ts` 的 `system` 分组新增"字典管理"菜单项，`router/index.ts` 中 `/system/dicts` 路由从 `PlaceholderView.vue` 改为指向真实组件。

## Capabilities

### New Capabilities
- `dict-management`：通用字典类型 + 字典项的维护能力（增删改查、启停用、按类型编码查询启用项），供全系统枚举类字段复用，及配套的前端字典管理主从式界面。

### Modified Capabilities
（无——本 change 不修改任何已归档 capability 的需求。）

## Impact

- **后端代码**：新增 `backend/src/main/java/cn/nihility/rbac/dict/**`（entity/constant/mapper/dto/service/controller/mapstruct）。
- **数据库**：新增 Flyway 迁移脚本，建表 `tab_dict_type`、`tab_dict_item`。
- **前端代码**：新增 `frontend/src/views/system/dict/DictManagementView.vue`、`src/api/dict.ts`、`src/stores/dict.ts`、`src/types/dict.ts`；修改 `router/menu.ts`（新增菜单项）与 `router/index.ts`（路由指向真实组件）。
- **API**：新增字典类型的分页/详情/增/改/启用/停用/删除接口（无树形接口——字典类型之间没有层级关系，与组织模块不同，见 `design.md`），字典项的分页/详情/增/改/启用/停用/删除接口，以及一个按类型编码查启用项的只读接口（`GET /api/dicts/items?typeCode={code}`，归入 `DictItemController`），共 15 个接口，均通过全局响应包装为 `{ code, message, data }`。
- **后续依赖**：用户管理模块（下一个独立 change）将依赖本模块暴露的只读查询接口获取"认证类型"下拉选项，需要预先在字典数据中初始化一个编码为 `position_type` 的字典类型及其 `primary`/`part_time`/`temporary` 三个字典项（本 change 通过 Flyway 迁移的初始化数据 SQL 完成，而非留给用户管理模块临时创建）。
