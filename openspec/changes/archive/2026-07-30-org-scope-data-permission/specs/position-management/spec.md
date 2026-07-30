## MODIFIED Requirements

### Requirement: 任职记录按组织分页查询
系统 SHALL 提供按所属组织 id 分页查询任职记录的接口，`orgId` 为必填参数（不存在"顶级组织聚合查询"的语义）；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）均为可选；仅返回未被逻辑删除（`status != -1000`）的任职记录，每条记录包含所属用户 id 及姓名、所属组织 id 及名称、任职类型编码、任职地址、任职电话、显示序号、备注、状态；结果按 `showOrder` 降序、相同时按 `id` 升序排列。该查询涉及 `tab_user_position`、`tab_user`、`tab_org` 三张表的关联，系统 SHALL 通过 MyBatis XML（`resources/mybatis/mapper/` 下）编写的单条 SQL JOIN 完成，不通过应用层对每张表分别查询后再拼装。当前登录用户的管辖组织范围解析结果为受限、且请求的 `orgId` 不在允许范围内时，系统 SHALL 返回空分页结果，不 SHALL 返回业务错误。

#### Scenario: 查询指定组织下的任职记录
- **WHEN** 客户端调用 `GET /api/positions?orgId={id}&page={page}&pageSize={pageSize}`
- **THEN** 系统返回 `orgId` 等于该 id 的未删除任职记录分页结果，包含 `records`（含 `userName`、`orgName`）、`total`、`page`、`pageSize`

#### Scenario: 未指定 orgId 时拒绝查询
- **WHEN** 客户端调用 `GET /api/positions` 且未携带 `orgId` 参数
- **THEN** 系统返回业务错误（非零 `code`），不做默认聚合查询

#### Scenario: 未指定分页参数时使用默认值
- **WHEN** 客户端调用 `GET /api/positions?orgId={id}` 且未携带 `page`、`pageSize` 参数
- **THEN** 系统按 `page = 1`、`pageSize = 10` 处理

#### Scenario: 已逻辑删除的任职记录不出现在查询结果中
- **WHEN** 某条任职记录的 `status` 为 `-1000`
- **THEN** 该条记录不出现在 `GET /api/positions` 的返回结果中

#### Scenario: 关联的用户或组织已被删除时任职记录仍正常返回
- **WHEN** 某条任职记录关联的 `userId` 或 `orgId` 对应的用户/组织已不存在（或已被逻辑删除）
- **THEN** 该条任职记录仍出现在查询结果中，仅 `userName`/`orgName` 对应字段为空，不会因为关联缺失而整条记录从结果中消失

#### Scenario: 管辖范围受限且请求组织超出范围时返回空分页
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，客户端调用 `GET /api/positions?orgId={id}`，该 `id` 不在允许范围内
- **THEN** 系统返回 `total = 0` 的空分页结果，不返回业务错误

#### Scenario: 管辖范围不受限时行为不变
- **WHEN** 当前登录用户没有配置管辖组织范围（解析结果不受限）
- **THEN** `GET /api/positions?orgId={id}` 行为与本次改动之前一致，只要 `orgId` 合法即正常返回
