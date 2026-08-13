-- ----------------------------------------------------------------------------
-- 应用同步范围与变更记录按应用物化（app-sync-org-scope-and-app-change-log change）
-- 1. tab_app_data_change_log 新增 app_ref_id 列：变更记录从"全局一条"改为"按目标应用
--    各自一条"。存量数据无法回填应用归属信息，先清空引用方 tab_app_notify_record，
--    再清空 tab_app_data_change_log（design.md Decision 8，BREAKING，见 proposal.md）。
-- 2. tab_app_data_change_log 新增索引 (app_ref_id, data_type, id)，支撑拉取接口按应用+
--    数据类型+序列号游标查询。
-- 3. 新建 tab_app_sync_org_scope：组织/用户/任职三个数据域按应用配置的同步范围，
--    与 tab_admin_org_scope 的既有约定一致（零行=全部数据，>=1 行=指定组织范围）。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_app_data_change_log`
    ADD COLUMN `app_ref_id` BIGINT NOT NULL COMMENT '目标应用 id（tab_app.id）' AFTER `id`;

-- TRUNCATE 顺序：先清引用方 tab_app_notify_record（逻辑关联 change_log_id，无物理外键），
-- 再清被引用方 tab_app_data_change_log，避免顺序颠倒带来的隐患（即使没有物理外键约束）。
TRUNCATE TABLE `tab_app_notify_record`;
TRUNCATE TABLE `tab_app_data_change_log`;

ALTER TABLE `tab_app_data_change_log`
    ADD INDEX `idx_tab_app_data_change_log_app_type` (`app_ref_id`, `data_type`, `id`);

-- 应用同步组织范围配置表：按 (app_ref_id, sync_domain) 直接关联，不额外增加模式开关列，
-- 零行=全部数据，>=1 行=指定组织范围（design.md Decision 2）。列名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_ref_id/sync_domain/org_id/
-- include_children 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_sync_org_scope` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`       BIGINT      NOT NULL COMMENT '所属应用 id，关联 tab_app.id',
    `sync_domain`      VARCHAR(16) NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `org_id`           BIGINT      NOT NULL COMMENT '组织 id，关联 tab_org.id',
    `include_children` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_sync_org_scope` (`app_ref_id`, `sync_domain`, `org_id`),
    KEY `idx_tab_app_sync_org_scope_app_domain` (`app_ref_id`, `sync_domain`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用同步组织范围配置表，零行=全部数据，>=1 行=指定组织范围';
