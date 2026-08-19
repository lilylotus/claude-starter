-- ----------------------------------------------------------------------------
-- 应用数据同步：删除变更记录表（app-sync-drop-changelog change）
-- ----------------------------------------------------------------------------

-- 拉取接口改为直接分页查询组织/用户/任职/应用/角色业务表当前数据，不再需要一张中间的
-- "变更记录"表来承载拉取游标；通知也改为数据变更时直接判定候选应用并即时触发，不再有
-- 任何持久化中转（design.md Migration Plan）。该表历史上只存储"指针"元信息（数据类型、
-- 操作类型、bizId），不含具体业务字段数据，删除不造成业务数据损失，不做数据导出/保留。
DROP TABLE `tab_app_data_change_log`;

-- tab_app_notify_record 去掉 change_log_id 列及其索引：改用该表本已独立存在的
-- data_type/biz_id 两列定位通知记录，不再依赖变更记录表。
ALTER TABLE `tab_app_notify_record`
    DROP INDEX `idx_tab_app_notify_record_change_log_id`,
    DROP COLUMN `change_log_id`;

-- tab_app_pull_record 去掉 pull_mode 列：拉取接口合并为一个统一的分页拉取接口后，
-- 只剩一种拉取方式，该字段失去意义。
ALTER TABLE `tab_app_pull_record`
    DROP COLUMN `pull_mode`;
