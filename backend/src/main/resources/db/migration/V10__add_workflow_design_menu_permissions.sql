-- ----------------------------------------------------------------------------
-- workflow-approval-engine change 遗留缺口修复：该 change 的后端/前端代码（流程设计器
-- Vue Flow 画布、WorkflowProcessModelController 等）与 权限资源.txt 里"流程设计"章节
-- 早已落地，但当时只更新了 权限资源.txt 文档，漏了本该同步登记的 tab_menu/tab_permission
-- 种子数据（对照 V2__create_chat_tables.sql "聊天"模块的登记方式）。导致 WorkflowDesign:
-- model:view/edit/publish/disable 四个权限点在数据库里从未真实存在，任何角色（含
-- SUPER_ADMIN）都无法被授予，前端侧边栏"流程设计"一级菜单因此对所有用户都不可见
-- （现象：页面上找不到"流程设计"菜单）。本脚本补登记该菜单分组 + 页面 + 三个按钮级
-- 权限点，并为 SUPER_ADMIN 角色补授，写法与 V2 脚本一致。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('流程设计', 'workflow-design', 0, 1, 35, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

SET @workflow_design_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'workflow-design');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('流程模型', 'WorkflowDesign:model:view', @workflow_design_group_id, 1, 10,
        '流程模型列表/版本历史页面访问（查看流程模型列表、按流程编码查看版本历史，只读展示发布人/发布时间/状态/DSL 快照，历史版本不提供编辑入口）',
        2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @workflow_model_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'WorkflowDesign:model:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('编辑流程模型', 'WorkflowDesign:model:edit', @workflow_model_view_id, 2, 30,
        '新增/编辑流程模型草稿，仅更新草稿内容，不触发部署', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('发布流程模型', 'WorkflowDesign:model:publish', @workflow_model_view_id, 2, 20,
        '编译当前草稿为 BPMN 并部署，生成新的不可变版本；与 edit 分别独立校验', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('下线/启用流程模型', 'WorkflowDesign:model:disable', @workflow_model_view_id, 2, 10,
        '下线/重新启用流程模型当前生效版本，启/停复用同一权限点', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
VALUES ('流程模型列表/版本历史页面访问', 'WorkflowDesign:model:view', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('新增/编辑流程模型草稿', 'WorkflowDesign:model:edit', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('发布流程模型', 'WorkflowDesign:model:publish', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('下线/重新启用流程模型', 'WorkflowDesign:model:disable', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

-- 超级管理员角色补授本次新增权限点（V1 基线脚本里的 SUPER_ADMIN 授权在本脚本执行前已完成，
-- 这里需要单独补一条，否则超级管理员账号也看不到"流程设计"菜单）。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`
WHERE `code` IN ('WorkflowDesign:model:view', 'WorkflowDesign:model:edit', 'WorkflowDesign:model:publish',
                 'WorkflowDesign:model:disable')
  AND @super_admin_role_id IS NOT NULL;
