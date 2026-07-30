## MODIFIED Requirements

### Requirement: 应用分页查询
系统 SHALL 提供分页查询应用列表的接口，不支持任何筛选参数；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）均为可选；仅返回未被逻辑删除（`status != -1000`）的应用；每条记录包含应用名称、应用编码、负责人 id 及姓名、所属组织 id 及名称、显示序号、备注、状态；结果按 `showOrder` 降序、相同时按 `id` 升序排列。当前登录用户的管辖组织范围解析结果为受限时，系统 SHALL 只返回 `org_id` 落在允许范围内的应用。

#### Scenario: 查询应用分页列表
- **WHEN** 客户端调用 `GET /api/apps?page={page}&pageSize={pageSize}`
- **THEN** 系统返回未删除应用的分页结果，包含 `records`（含 `ownerName`、`orgName`）、`total`、`page`、`pageSize`，按 `showOrder` 降序排列

#### Scenario: 未指定分页参数时使用默认值
- **WHEN** 客户端调用 `GET /api/apps` 且未携带 `page`、`pageSize` 参数
- **THEN** 系统按 `page = 1`、`pageSize = 10` 处理

#### Scenario: 已逻辑删除的应用不出现在查询结果中
- **WHEN** 某个应用的 `status` 为 `-1000`
- **THEN** 该应用不出现在 `GET /api/apps` 的返回结果中

#### Scenario: 管辖范围受限时只返回范围内组织的应用
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，只包含组织 C 及其子孙
- **THEN** `GET /api/apps` 只返回 `org_id` 属于 C 或其子孙的应用，`org_id` 不在该范围内的应用不出现在返回结果中

#### Scenario: 管辖范围不受限时行为不变
- **WHEN** 当前登录用户没有配置管辖组织范围（解析结果不受限）
- **THEN** `GET /api/apps` 返回全部未删除应用的分页结果，行为与本次改动之前一致
