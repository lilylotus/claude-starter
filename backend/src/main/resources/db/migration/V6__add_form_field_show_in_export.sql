-- ----------------------------------------------------------------------------
-- add-master-data-excel-export change：为 tab_form_field_definition 新增
-- show_in_export（是否导出）布尔列，与既有 show_in_list/show_in_create/show_in_edit
-- 同级独立开关，供 master-data-excel-export 能力的导出接口驱动导出列的选取
-- （design.md Decision 3/8）。存量记录按迁移执行时的 show_in_list 值回填初始值，
-- 此后两者相互独立，管理员可在表单管理页面单独调整"是否导出"。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_form_field_definition`
    ADD COLUMN `show_in_export` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否导出' AFTER `show_in_edit`;

UPDATE `tab_form_field_definition`
SET `show_in_export` = `show_in_list`;
