-- ----------------------------------------------------------------------------
-- 表单字段定义模块 - tab_form_field_definition.dict_type_id 由字典类型主键 id
-- 改为业务编码 dict_type_code（Flyway 迁移版本 V34）。字典类型的自增主键 id 在数据
-- 迁移/环境切换场景下可能变化，业务编码语义稳定不变，改为按编码关联可避免 id 失配
-- 导致字典下拉选项静默失效（详见 openspec/changes/form-field-dict-type-code）。
-- 先加新列、按现有 dict_type_id -> tab_dict_type.id 的关联关系批量回填 code，
-- 再删除旧列，顺序不能反。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_form_field_definition` ADD COLUMN `dict_type_code` VARCHAR(64) NULL
    COMMENT '关联的字典类型编码，关联 tab_dict_type.code，仅 control_type=3/5 时必填' AFTER `dict_type_id`;

UPDATE `tab_form_field_definition` d
    JOIN `tab_dict_type` t ON d.dict_type_id = t.id
    SET d.dict_type_code = t.code
    WHERE d.dict_type_id IS NOT NULL;

ALTER TABLE `tab_form_field_definition` DROP COLUMN `dict_type_id`;
