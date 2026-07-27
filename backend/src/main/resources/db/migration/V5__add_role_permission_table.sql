-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库迁移脚本 V5
-- 新增 tab_role_permission 角色权限点关联表，模式对齐既有 tab_admin_role：
-- 无独立 status，角色新增/更新时整体同步（先按 role_id 物理删除既有关联，再按
-- 提交内容重建），不做按行 diff，也不做逻辑删除。
-- 列名 role_id/permission_id 已核对，与 MySQL/PostgreSQL/Oracle/SQL Server 保留字
-- 均无冲突。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_role_permission` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `role_id`       BIGINT      NOT NULL COMMENT '角色 id，关联 tab_role.id',
    `permission_id` BIGINT      NOT NULL COMMENT '权限点 id，关联 tab_permission.id',
    `create_by`     VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_role_permission` (`role_id`, `permission_id`),
    KEY `idx_tab_role_permission_role_id` (`role_id`),
    KEY `idx_tab_role_permission_permission_id` (`permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '角色权限点关联表，无独立 status，随角色整体同步、物理删除';
