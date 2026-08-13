-- ----------------------------------------------------------------------------
-- 身份上游数据同步（identity-upstream-data-sync change）
-- 新增 4 张表：上游数据源（连接信息+调度配置）、数据域配置（组织/用户/任职三个数据域各自
-- 独立启用与取数来源）、字段映射（上游字段→系统元数据字段）、同步执行记录。
-- 所有列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字，未使用窗口函数/CTE 等
-- 版本相关写法，兼容 MySQL 5.7（design.md Migration Plan）。
-- ----------------------------------------------------------------------------

-- 上游数据源主表：一套连接信息（接口自定义请求头 / 数据库 JDBC 连接）+ 一套调度配置。
-- 接口模式的请求头取值、数据库模式的密码均为 SM4 加密文本，复用
-- AppSecretProperties 的同一把主密钥（design.md Decision 1）。
CREATE TABLE IF NOT EXISTS `tab_upstream_source`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`               VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `sync_type`          VARCHAR(16)  NOT NULL COMMENT '同步方式：API=接口，DB_TABLE=数据库表',
    `enabled`            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用，创建时默认 0（未启用）',
    `schedule_type`      VARCHAR(16)  NOT NULL DEFAULT 'INTERVAL' COMMENT '调度方式：INTERVAL=按间隔，FIXED_TIME=按每日固定时间点',
    `interval_unit`      VARCHAR(16)  NULL COMMENT '间隔单位：MINUTE/HOUR，schedule_type=INTERVAL 时使用',
    `interval_value`     INT          NULL COMMENT '间隔取值（正整数），schedule_type=INTERVAL 时使用',
    `fixed_time`         VARCHAR(5)   NULL COMMENT '每日固定时间点，HH:mm 文本，schedule_type=FIXED_TIME 时使用',
    `last_trigger_time`  DATETIME     NULL COMMENT '上次定时触发时间，供轮询任务判断是否到点，手动触发不更新该列',
    `api_auth_headers`   TEXT         NULL COMMENT '接口模式自定义请求头，JSON 文本（{key: SM4密文}），syncType=API 时使用',
    `db_jdbc_url`        VARCHAR(500) NULL COMMENT '数据库模式 JDBC 连接地址，仅支持 jdbc:mysql:// 前缀，syncType=DB_TABLE 时使用',
    `db_username`        VARCHAR(128) NULL COMMENT '数据库模式连接用户名，syncType=DB_TABLE 时使用',
    `db_password`        VARCHAR(500) NULL COMMENT '数据库模式连接密码，SM4 加密文本，syncType=DB_TABLE 时使用',
    `create_by`          VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_source_enabled` (`enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源配置表';

-- 数据域配置：每个数据源固定 3 行（ORG/USER/POSITION），各自独立启用开关与取数来源
-- （接口模式的请求 URL+方式，或数据库模式的只读查询 SQL）。
CREATE TABLE IF NOT EXISTS `tab_upstream_domain_config`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `source_id`       BIGINT       NOT NULL COMMENT '所属上游数据源 id，关联 tab_upstream_source.id',
    `data_type`       VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用该数据域',
    `api_url`         VARCHAR(500) NULL COMMENT '接口模式该数据域的请求地址，syncType=API 时使用',
    `api_method`      VARCHAR(8)   NULL COMMENT '接口模式请求方式：GET/POST，syncType=API 时使用',
    `db_sql`          TEXT         NULL COMMENT '数据库模式该数据域的只读查询 SQL（列别名对应上游字段编码），syncType=DB_TABLE 时使用',
    `last_sync_time`  DATETIME     NULL COMMENT '该数据域上次同步完成时间，仅展示用途，不驱动增量逻辑',
    `create_by`       VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_upstream_domain_config` (`source_id`, `data_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源数据域配置表';

-- 字段映射：管理员手工填写"上游字段名称/编码"作为源，选择本系统元数据字段作为目标
-- （方向与 tab_app_sync_field_mapping 相反，见 design.md Decision 5）。只存
-- metadata_field_id 外键，不落快照，查询时实时 JOIN tab_metadata_field 读取。
CREATE TABLE IF NOT EXISTS `tab_upstream_field_mapping`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `source_id`            BIGINT       NOT NULL COMMENT '所属上游数据源 id，关联 tab_upstream_source.id',
    `data_type`            VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `upstream_field_name`  VARCHAR(128) NOT NULL COMMENT '上游字段名称，管理员手工填写',
    `upstream_field_code`  VARCHAR(128) NOT NULL COMMENT '上游字段编码，管理员手工填写，同一数据源同一数据域内不允许重复',
    `metadata_field_id`    BIGINT       NOT NULL COMMENT '目标元数据字段 id，关联 tab_metadata_field.id',
    `transform_type`       VARCHAR(16)  NOT NULL COMMENT '转换方式：NO_TRANSFORM/FIXED_VALUE/SCRIPT',
    `transform_value`      VARCHAR(5000) NULL COMMENT '转换取值：固定值的具体值，或脚本源码',
    `create_by`            VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_field_mapping_source_type` (`source_id`, `data_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源字段映射表';

-- 同步执行记录：粒度对齐"数据源+数据域"，一次同步触发会为其下已启用的每个数据域各写一条
-- （design.md Decision 1）。仅用于展示排查，不驱动任何自动重试。
CREATE TABLE IF NOT EXISTS `tab_upstream_sync_record`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `source_id`       BIGINT       NOT NULL COMMENT '所属上游数据源 id，关联 tab_upstream_source.id',
    `data_type`       VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `trigger_type`    VARCHAR(16)  NOT NULL COMMENT '触发方式：SCHEDULE=定时触发，MANUAL=手动触发',
    `start_time`      DATETIME     NOT NULL COMMENT '本次同步开始时间',
    `end_time`        DATETIME     NULL COMMENT '本次同步结束时间',
    `status`          VARCHAR(16)  NOT NULL COMMENT '执行状态：SUCCESS=全部成功，PARTIAL=部分失败，FAILED=全部失败或执行异常',
    `total_count`     INT          NOT NULL DEFAULT 0 COMMENT '处理总行数',
    `success_count`   INT          NOT NULL DEFAULT 0 COMMENT '成功行数',
    `fail_count`      INT          NOT NULL DEFAULT 0 COMMENT '失败行数',
    `fail_summary`    VARCHAR(500) NULL COMMENT '失败摘要文本，截断到合理长度，非完整堆栈',
    `create_by`       VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_sync_record_source` (`source_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源同步执行记录表';
