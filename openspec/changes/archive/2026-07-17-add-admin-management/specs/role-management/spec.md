## ADDED Requirements

### Requirement: 角色选项查询
系统 SHALL 提供一个不分页的角色选项查询接口，仅返回未被逻辑删除且状态为启用（`status = 2000`）的角色，每项包含角色 id、名称、编码，按 `showOrder` 降序、相同时按 `id` 升序排列，供其他模块的角色多选/单选选择器一次性加载全量选项使用。

#### Scenario: 查询启用状态的角色选项
- **WHEN** 客户端调用 `GET /api/roles/options`
- **THEN** 系统返回全部未被逻辑删除且状态为启用的角色列表，每项包含 `id`、`name`、`code`，不包含已停用或已删除的角色，也不分页
