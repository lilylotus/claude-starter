ALTER TABLE `tab_org`
    ADD COLUMN `org_path` VARCHAR(255) NULL COMMENT '从根组织到当前组织的 id 路径' AFTER `parent_code`,
    ADD COLUMN `org_name_path` VARCHAR(255) NULL COMMENT '从根组织到当前组织的名称路径' AFTER `org_path`,
    ADD COLUMN `org_parent_path` VARCHAR(255) NULL COMMENT '当前组织的父级 id 路径' AFTER `org_name_path`,
    ADD KEY `idx_tab_org_path` (`org_path`),
    ADD KEY `idx_tab_org_name_path` (`org_name_path`);

UPDATE `tab_org`
SET `org_path` = CAST(`id` AS CHAR),
    `org_name_path` = `name`,
    `org_parent_path` = NULL
WHERE `parent_id` = 0;

-- MySQL 5.7 不支持递归 CTE。固定 20 轮足以覆盖系统约定的最大机构层级；
-- 每轮只回填父级路径已经就绪的下一层，重复执行具有幂等性。
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`),
    child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`)
WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
UPDATE `tab_org` child JOIN `tab_org` parent ON child.`parent_id` = parent.`id`
SET child.`org_parent_path` = parent.`org_path`, child.`org_path` = CONCAT(parent.`org_path`, '/', child.`id`), child.`org_name_path` = CONCAT(parent.`org_name_path`, '/', child.`name`) WHERE child.`org_path` IS NULL AND parent.`org_path` IS NOT NULL;
