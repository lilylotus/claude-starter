-- ----------------------------------------------------------------------------
-- add-process-binding-delete change：业务绑定新增删除（软删除）能力（design.md
-- Decision 1/2/5）。tab_wf_process_binding 原 enabled 布尔字段扩展为 status 三态
-- （启用=2000/停用=3000/已删除=-1000），原 (biz_type, operation_type, scope_type,
-- scope_id) 数据库级 UNIQUE 索引降级为普通索引——软删除后同一维度需要允许重新新建
-- 绑定，"同维度仅一条未删除绑定"的唯一性规则改由应用层查询保证（与本项目 AdminEntity/
-- RoleEntity 等 code 字段"未删除范围内唯一、无数据库级 UNIQUE 兜底"的既有惯例一致）。
-- MySQL 5.7 兼容语法：不使用窗口函数/CTE/JSON_TABLE 等 8.0+ 特性。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_process_binding`
    DROP INDEX `uk_tab_wf_process_binding_dimension`,
    ADD COLUMN `status` INT NOT NULL DEFAULT 2000 COMMENT '绑定状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）' AFTER `revision`,
    ADD INDEX `idx_tab_wf_process_binding_dimension` (`biz_type`, `operation_type`, `scope_type`, `scope_id`);

UPDATE `tab_wf_process_binding` SET `status` = IF(`enabled` = 1, 2000, 3000);

ALTER TABLE `tab_wf_process_binding` DROP COLUMN `enabled`;

-- ----------------------------------------------------------------------------
-- 补齐 WorkflowDesign:binding:delete 权限点种子数据（写法与 V1 里
-- WorkflowDesign:binding:edit 的插入语句一致，NOT EXISTS 幂等守卫）。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';
SET @workflow_binding_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'WorkflowDesign:binding:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '业务绑定删除', 'WorkflowDesign:binding:delete', @workflow_binding_view_id, 2, 6,
       '删除业务绑定（软删除），删除后该绑定维度可重新新建绑定', 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE @workflow_binding_view_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'WorkflowDesign:binding:delete');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '业务绑定删除', 'WorkflowDesign:binding:delete', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'WorkflowDesign:binding:delete');
