## ADDED Requirements

### Requirement: 权限点选项查询
系统 SHALL 提供一个不分页的权限点选项查询接口，仅返回未被逻辑删除且状态为启用（`status = 2000`）的权限点，每项包含权限点 id、名称、编码，按 `showOrder` 降序、相同时按 `id` 升序排列，供角色管理等其他模块的权限点勾选控件一次性加载全量选项使用。

#### Scenario: 查询启用状态的权限点选项
- **WHEN** 客户端调用 `GET /api/permissions/options`
- **THEN** 系统返回全部未被逻辑删除且状态为启用的权限点列表，每项包含 `id`、`name`、`code`，不包含已停用或已删除的权限点，也不分页
