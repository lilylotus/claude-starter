-- ----------------------------------------------------------------------------
-- 上游字段映射主键标识（upstream-field-mapping-primary-key change）
-- 为 tab_upstream_field_mapping 新增 is_primary_key 列：标记该字段是否作为落库匹配的
-- 主键标识之一（同一数据域可多选组成联合主键，AND 语义），替换原来写死按 code/
-- positionType 匹配的逻辑（design.md Decision 1）。默认值 0 意味着本次改动上线前已保存
-- 的历史映射行天然"零主键字段"，由同步执行引擎在处理任何一行数据之前做前置拦截
-- （design.md Decision 4），不会用空匹配条件误伤本地数据。不回改已执行过的 V4/V5。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_upstream_field_mapping`
    ADD COLUMN `is_primary_key` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否作为落库匹配的主键标识字段之一，同一数据域可多选组成联合主键' AFTER `metadata_field_id`;
