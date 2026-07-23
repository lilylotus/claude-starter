-- ----------------------------------------------------------------------------
-- 操作日志模块 - 为 tab_operation_log 增加操作来源列（Flyway 迁移版本 V30）
-- 区分某条操作日志记录是页面手动操作还是 Excel 批量导入产生的
-- （fix-excel-import-followups design.md 决策 3）。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_operation_log`
    ADD COLUMN `operate_source` INT NOT NULL DEFAULT 0 COMMENT '操作来源：0=界面操作，1=Excel导入' AFTER `operation_type`;
