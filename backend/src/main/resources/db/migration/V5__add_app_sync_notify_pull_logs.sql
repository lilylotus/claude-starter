-- ----------------------------------------------------------------------------
-- 应用同步通知/拉取日志模块（add-app-sync-notify-pull-logs change）
-- ----------------------------------------------------------------------------

-- tab_app_notify_record 新增 data_type/biz_id/notify_url 三列：分别记录触发本次通知的
-- 数据类型、被变更对象 id、本次回调实际使用的地址快照（不再依赖 tab_app_config.notify_url
-- 的当前值，避免管理员事后改了回调地址导致历史记录被误导，design.md Decision 1）。三列均
-- 可空，历史记录本来就查不到这些信息，不做历史回填（proposal.md）。列名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：data_type/biz_id/notify_url 均非保留字。
ALTER TABLE `tab_app_notify_record`
    ADD COLUMN `data_type` VARCHAR(20) NULL COMMENT '数据类型：ORG/USER/POSITION/APP/ROLE，历史数据为空' AFTER `app_ref_id`,
    ADD COLUMN `biz_id` BIGINT NULL COMMENT '被变更对象 id，历史数据为空' AFTER `data_type`,
    ADD COLUMN `notify_url` VARCHAR(255) NULL COMMENT '本次回调实际使用的地址快照，历史数据为空' AFTER `error_msg`;

-- 新增支持"某应用按时间倒序查通知日志"这个最常见查询模式的复合索引，状态过滤直接在此索引
-- 基础上加 notify_status 等值条件（design.md Decision 1）。
ALTER TABLE `tab_app_notify_record`
    ADD KEY `idx_tab_app_notify_record_app_time` (`app_ref_id`, `create_time`);

-- 新建 tab_app_pull_record 表：记录外部应用调用现有两个拉取接口（按数据类型+id 拉取、按
-- 序列号批量拉取）的请求，仅用于问题排查/展示，不驱动任何业务逻辑，不涉及日志保留/清理
-- 策略（design.md Decision 2，proposal.md Non-Goals）。列名已核对 MySQL/PostgreSQL/
-- Oracle/SQL Server 保留字：app_ref_id/pull_mode/data_type/request_summary/result_count
-- 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_pull_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`      BIGINT       NOT NULL COMMENT '发起拉取的应用 id，关联 tab_app.id',
    `pull_mode`       VARCHAR(20)  NOT NULL COMMENT '拉取方式：BY_ID=按id拉取，BY_SEQUENCE=按序列号拉取',
    `data_type`       VARCHAR(20)  NULL COMMENT '请求的数据类型，按序列号拉取未传时为空',
    `request_summary` VARCHAR(255) NULL COMMENT '请求参数摘要：按id拉取记bizId个数，按序列号拉取记fromSequence/limit',
    `result_count`    INT          NOT NULL DEFAULT 0 COMMENT '本次返回的记录条数',
    `create_by`       VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_pull_record_app_time` (`app_ref_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用拉取变更数据请求记录，仅用于问题排查/展示';
