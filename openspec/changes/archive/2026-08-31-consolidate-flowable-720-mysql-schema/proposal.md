## Why

当前 Flowable 7.2.0 的 MySQL 建表 SQL 分散在依赖包内，部署时依赖 `database-schema-update` 自动执行，无法作为一个可审阅、可人工执行的数据库初始化交付物。需要把当前项目实际启用的 ProcessEngine 所需官方脚本按依赖顺序合并并规整为单一脚本，降低离线部署和数据库变更审核成本。

## What Changes

- 新增一个同时兼容 MySQL 5.7 与 MySQL 8.0、目标版本为 Flowable 7.2.0 的完整初始化脚本，用于空库一次性创建当前项目启用的 Flowable ProcessEngine 全部 `ACT_*` 表、索引、约束及版本属性。
- 合并 Flowable 7.2.0 官方依赖包中的 common、engine、history 建表内容，保留必要的执行顺序和来源说明，移除重复说明并统一文件格式。
- 在每个 `ACT_*`/`FLW_*` 建表语句前补充中文用途说明，便于部署审阅；说明不改动官方有效 SQL。
- 不合并旧版本增量升级脚本；最终脚本直接表达 Flowable 7.2.0 的全新安装终态，不支持已有 Flowable schema 的原地升级或重复执行。
- 不纳入项目已关闭的 IDM、DMN、CMMN、App 和 Event Registry 引擎专属表。
- 增加静态校验和双版本空库执行验证要求，确保合并脚本与 Flowable 7.2.0 官方建表脚本的有效 SQL 语句一致，且能分别被 MySQL 5.7 与 MySQL 8.0 接受。

## Capabilities

### New Capabilities

- `flowable-database-bootstrap`: 定义 Flowable 7.2.0 ProcessEngine 在 MySQL 5.7 与 MySQL 8.0 上通过同一个 SQL 文件完成空库初始化的交付和校验要求。

### Modified Capabilities

无。

## Impact

- 后端资源：新增独立的 Flowable 数据库初始化 SQL；不放入 Flyway 自动扫描目录，避免与 Flowable 自身 schema 管理及现有业务迁移混用。
- 部署流程：部署人员可在启动应用前人工审阅并执行单一脚本；本 change 不处理已有 Flowable 数据库的升级。
- 运行配置：是否同时关闭 `flowable.database-schema-update` 将在设计中明确，以避免部署后由应用再次尝试自动建表。
- 依赖与 API：Flowable 版本保持 7.2.0，不新增依赖，不修改业务接口、前端或权限资源编码。
