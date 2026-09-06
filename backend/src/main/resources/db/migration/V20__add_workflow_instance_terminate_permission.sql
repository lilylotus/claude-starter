-- ----------------------------------------------------------------------------
-- production-approval-lifecycle change 第6节"运行时与复杂任务"配套增量迁移（tasks.md 6.8）：
-- 新增运维强制终止流程实例接口 POST /api/v1/workflow/process-instances/{id}/terminate
-- （design.md 第7节/第12节），补齐其独立权限点 WorkflowDesign:instance:terminate 的
-- tab_menu/tab_permission 种子数据，写法与 V14 一致（幂等 INSERT ... WHERE NOT EXISTS，
-- 可重复执行）。前端运维终止入口尚未实现（属于后续批次），权限点已在后端生效。
-- 全部使用 MySQL 5.7 兼容写法，不使用窗口函数/CTE/JSON_TABLE/厂商专属 upsert。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';

SET @workflow_design_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'workflow-design');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '运维终止流程实例', 'WorkflowDesign:instance:terminate', @workflow_design_group_id, 2, 40,
       '独立运维权限：强制终止运行中的流程实例，终止原因必填，结束流程并取消全部开放任务，不触发任何业务执行事件',
       2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @workflow_design_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'WorkflowDesign:instance:terminate');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '运维终止流程实例', 'WorkflowDesign:instance:terminate', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'WorkflowDesign:instance:terminate');

SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`
WHERE `code` = 'WorkflowDesign:instance:terminate'
  AND @super_admin_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission` rp
      WHERE rp.`role_id` = @super_admin_role_id AND rp.`permission_id` = `tab_permission`.`id`
  );
