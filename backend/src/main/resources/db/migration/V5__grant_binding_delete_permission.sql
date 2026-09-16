-- ----------------------------------------------------------------------------
-- add-process-binding-delete change 遗留缺口修复：V4 只登记了 WorkflowDesign:
-- binding:delete 的 tab_menu/tab_permission 种子数据，漏了 tab_role_permission
-- 补授——V1 里 WorkflowDesign:binding:edit/view 等权限点是在同一个脚本内、
-- 授权 SUPER_ADMIN 全部权限点的 INSERT...SELECT 之前插入的，天然被那条语句覆盖；
-- 但 V4 是独立后续脚本，不会被 V1 那条一次性 INSERT 影响，必须单独补授，否则
-- 该权限点在数据库里存在但任何角色（含 SUPER_ADMIN）都未被授予，前端"删除"按钮
-- 因此对所有用户都不可见（现象：业务绑定页面只有切换版本/启用/停用，没有删除）。
-- ----------------------------------------------------------------------------

SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');
SET @binding_delete_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'WorkflowDesign:binding:delete');
SET @admin_user_id_text := '1';

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @binding_delete_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @binding_delete_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @binding_delete_permission_id);
