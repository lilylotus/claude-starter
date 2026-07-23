## ADDED Requirements

### Requirement: 预置性别字典数据
系统 SHALL 通过数据库迁移预置一个编码为 `gender`、名称为"性别"的字典类型，及其下编码分别为 `unknown`（标签"未知"）、`male`（标签"男"）、`female`（标签"女"）的三个字典项，初始状态均为启用（`2000`）。

#### Scenario: 迁移执行后可查询到预置的性别字典
- **WHEN** 数据库迁移执行完成后客户端调用 `GET /api/dicts/items?typeCode=gender`
- **THEN** 系统返回包含 `unknown`/`male`/`female` 三项、标签分别为"未知"/"男"/"女"的列表
