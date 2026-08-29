## Why

应用同步配置的通知日志目前直接展示英文数据类型编码和裸 `bizId`，管理员无法快速判断通知对应的业务类型和具体数据，需要额外跳转或查询才能定位。

## What Changes

- 通知日志“数据类型”列将 ORG/USER/POSITION/APP/ROLE 显示为组织/用户/任职/应用/角色。
- 通知日志分页响应新增业务数据名称字段，前端将业务对象展示为 `id（名称）`，例如 `2（测试组织名称）`。
- 对历史空值、未知数据类型或已无法查询名称的记录提供兼容降级，不影响日志分页查询。
- 补充后端名称解析、接口 DTO 和前端展示测试。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-sync-notify-pull`：通知日志查询结果增加业务名称，并要求管理端将数据类型本地化、将业务 id 与名称组合展示。

## Impact

- 后端：通知日志分页查询服务、响应 DTO，以及按数据类型解析业务名称的逻辑。
- 前端：应用同步配置通知日志表格、TypeScript 响应类型和数据类型中文映射。
- API：`GET /api/apps/{id}/config/sync/notify-records` 的列表行兼容新增可空 `bizName` 字段；不修改数据库结构和既有请求参数。

## Implementation Result

已按计划完成后端 `bizName` 批量解析与前端中文/组合展示。后端五类数据按类型各批量查询一次，POSITION 通过单次 JOIN 取得用户和组织名称；前端使用独立格式化函数并以 Node 内置测试运行器验证，无新增依赖或数据库迁移。
