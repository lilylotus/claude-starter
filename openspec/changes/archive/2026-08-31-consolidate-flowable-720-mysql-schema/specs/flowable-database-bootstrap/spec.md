## Purpose

为部署人员提供一个可审阅、可离线执行，并同时兼容 MySQL 5.7 与 MySQL 8.0 的 Flowable 7.2.0 ProcessEngine 空库初始化脚本，替代首次启动时不可见的自动建表过程。

## ADDED Requirements

### Requirement: 单脚本完成 Flowable 7.2.0 空库初始化
系统 SHALL 提供且仅提供一个 Flowable 数据库初始化 SQL 文件；同一文件在已选定的空 MySQL 5.7 或 MySQL 8.0 schema 中成功执行一次后，SHALL 创建当前项目启用的 Flowable 7.2.0 ProcessEngine 正常启动和运行所需的全部表、主键、索引、唯一约束、外键及 schema 版本属性，不得按数据库版本维护两份分叉脚本。

#### Scenario: 在 MySQL 5.7 空 schema 中初始化
- **WHEN** 部署人员在一个不含任何 Flowable 对象的 MySQL 5.7 schema 中执行完整脚本
- **THEN** 脚本执行成功，并形成可供 Flowable 7.2.0 ProcessEngine 使用的完整数据库结构

#### Scenario: 在 MySQL 8.0 空 schema 中初始化
- **WHEN** 部署人员在一个不含任何 Flowable 对象的 MySQL 8.0 schema 中执行同一个完整脚本
- **THEN** 脚本执行成功，并形成与 MySQL 5.7 验证范围一致、可供 Flowable 7.2.0 ProcessEngine 使用的完整数据库结构

#### Scenario: 初始化后启动应用
- **WHEN** 初始化脚本分别在 MySQL 5.7 或 MySQL 8.0 执行成功且应用以 Flowable 7.2.0 启动
- **THEN** 两个数据库版本上的 ProcessEngine 均通过 schema 版本校验且不再尝试自动创建或升级 Flowable 表

### Requirement: 初始化内容与官方 7.2.0 脚本保持一致
合并脚本 SHALL 来源于项目解析到的 Flowable 7.2.0 官方 MySQL `common`、`engine`、`history` 建表脚本，并 SHALL 按 `common`、`engine`、`history` 的依赖顺序排列；除注释、空白、换行和脚本边界标识外，不得遗漏、重复或改写官方有效 SQL 语句。

#### Scenario: 对比官方脚本
- **WHEN** 将合并脚本的有效 SQL 与三个官方源脚本按规定顺序连接后的有效 SQL 进行规范化比较
- **THEN** 两者的语句数量、顺序和语义内容一致

#### Scenario: 识别脚本来源
- **WHEN** 审阅人员打开合并脚本
- **THEN** 文件头和各分段注释清楚标明目标 Flowable 版本、兼容的 MySQL 5.7/8.0、适用场景及每段官方资源路径

### Requirement: 初始化范围匹配当前启用引擎
合并脚本 SHALL 包含 ProcessEngine 及其所需共享服务结构，且 SHALL NOT 包含当前配置已关闭的 IDM、DMN、CMMN、App 或 Event Registry 引擎专属初始化脚本。

#### Scenario: 审核引擎范围
- **WHEN** 审阅人员核对合并来源清单
- **THEN** 来源仅包含 ProcessEngine 所需的 common、engine 和 history 三个 MySQL 建表脚本

### Requirement: 明确限制破坏性误用
初始化脚本 SHALL 明确声明仅适用于空 schema、不可重复执行、不可用于已有 Flowable schema 的版本升级，并 SHALL NOT 包含创建或删除数据库、删除既有表或清空既有数据的语句。

#### Scenario: 已有 Flowable 表时执行
- **WHEN** 部署人员误在已有 Flowable 表的 schema 中执行脚本
- **THEN** 数据库因对象已存在而停止执行，而脚本不会预先删除或清空已有对象以强制继续

#### Scenario: 审查破坏性语句
- **WHEN** 对脚本进行静态检查
- **THEN** 脚本中不存在 `CREATE DATABASE`、`DROP DATABASE`、`DROP TABLE`、`TRUNCATE TABLE` 或面向既有数据的删除语句
