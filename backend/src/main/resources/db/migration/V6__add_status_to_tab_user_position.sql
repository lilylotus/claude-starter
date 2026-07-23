-- ----------------------------------------------------------------------------
-- 任职管理模块 - 为 tab_user_position 增加独立状态列（Flyway 迁移版本 V6）
-- ----------------------------------------------------------------------------

-- ALTER TABLE `tab_user_position`
--     ADD COLUMN `status` INT NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）' AFTER `remark`,
--     ADD KEY `idx_tab_user_position_status` (`status`);
