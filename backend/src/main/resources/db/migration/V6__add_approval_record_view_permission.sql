-- ----------------------------------------------------------------------------
-- add-approval-history-menu change：新增"审批历史"页面权限点
-- ApprovalManagement:record:view（design.md Decision 3）。与既有的
-- request:view/request:approve/switch:view/switch:edit 一样，遵循"一个前端页面对应
-- 一个独立登记的 view 权限点"约定，不复用 request:approve。
--
-- 必须在同一条脚本内一次性完成 tab_menu + tab_permission + tab_role_permission
-- （授予 SUPER_ADMIN）三部分：V1 里 SUPER_ADMIN 授权全部权限点是用一条
-- INSERT...SELECT 覆盖当时已插入的全部 tab_permission 行，之后新增的独立迁移脚本
-- 不会被那条一次性语句覆盖，必须自己显式补一条 tab_role_permission。仓库里已有真实
-- 先例踩过"漏了这一步"的坑：V4 只登记了 WorkflowDesign:binding:delete 的
-- tab_menu/tab_permission，漏了 tab_role_permission 补授，导致该权限点在数据库里
-- 存在但任何角色（含 SUPER_ADMIN）都未被授予，后来用 V5 单独补了一刀修复。本次不
-- 重复这个错误。全部写法参照 V5 的幂等模式（NOT EXISTS 防重复插入）。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';
SET @approval_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'approval');

-- 1) tab_menu：挂在"审批管理"分组下，show_order 取小于既有最小值（审批设置=10）的 5，
--    排在"审批设置"之后（本分组按 show_order 降序展示）。
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '审批历史', 'ApprovalManagement:record:view', @approval_group_id, 1, 5,
       '查看当前登录用户自己已经审批过的历史记录页面访问', 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE @approval_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'ApprovalManagement:record:view');

-- 2) tab_permission：对应权限点行。
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '查看审批历史', 'ApprovalManagement:record:view', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'ApprovalManagement:record:view');

-- 3) tab_role_permission：默认授予 SUPER_ADMIN（用户明确要求"默认给管理员分配这个菜单
--    资源"），admin 账号迁移后即可直接看到新菜单，不需要额外手动勾选。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');
SET @record_view_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'ApprovalManagement:record:view');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @record_view_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @record_view_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @record_view_permission_id);
