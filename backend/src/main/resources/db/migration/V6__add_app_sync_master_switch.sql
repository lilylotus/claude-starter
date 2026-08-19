-- ----------------------------------------------------------------------------
-- 应用同步总开关（app-sync-master-switch change）
-- ----------------------------------------------------------------------------

-- tab_app_config 新增 sync_master_enabled 列：应用级同步总开关，默认开启（1），保证存量应用
-- 行为不变。关闭后该应用不再产生新的 tab_app_data_change_log 记录、不再发送通知、拉取接口
-- （按 id / 按序列号）一律返回空结果；历史记录不删除，重新打开后自动恢复（design.md
-- Migration Plan）。列名已核对 MySQL 保留字：sync_master_enabled 非保留字。
ALTER TABLE `tab_app_config`
    ADD COLUMN `sync_master_enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '同步总开关：1=开启，0=关闭，关闭后不再产生该应用的数据变更记录、不发送通知、拉取接口返回空结果'
        AFTER `need_sign`;
