-- ----------------------------------------------------------------------------
-- 菜单管理模块 - 补充字典类型、字典项的"详情"按钮资源种子数据（Flyway 迁移版本 V16）
-- 数据来源：仓库根目录 权限资源.txt 新增的 DictManagement:dictType:detail、
-- DictManagement:dictItem:detail 两条编码。挂在 V11 已插入的
-- DictManagement:dictType:view 节点下，showOrder 参照其余模块"详情"按钮的相对
-- 位置（新增之后、编辑之前）：字典类型现有 add=100/edit=90，插入 detail=95；
-- 字典项现有 add=50/edit=40，插入 detail=45。
-- ----------------------------------------------------------------------------

SET @dict_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'DictManagement:dictType:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('查看字典类型详情', 'DictManagement:dictType:detail', @dict_id, 2, 95, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('查看字典项详情', 'DictManagement:dictItem:detail', @dict_id, 2, 45, NULL, 2000, 'admin', NOW(), 'admin',
        NOW());
