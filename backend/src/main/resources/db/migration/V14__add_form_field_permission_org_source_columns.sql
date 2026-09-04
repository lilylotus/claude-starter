-- ----------------------------------------------------------------------------
-- production-approval-lifecycle change 第 5 节"表单、身份与安全"配套增量迁移：
-- 1) tab_approval_request 新增表单版本快照 id、冻结的 before/after 快照（5.1）；
-- 2) tab_wf_node_assignee_rule 新增节点字段权限快照（5.2）、组织负责人固定目标组织来源
--    （5.3"指定固定组织管理员审批"）；
-- 3) tab_wf_approval_task_candidate 已在 V11 建好 resolve_basis 列（本迁移不重复处理，仅
--    在实体/代码层补齐读写）；
-- 4) 补齐 workflow-approval-engine/production-approval-lifecycle 第4节遗留的
--    WorkflowDesign:model:review / WorkflowDesign:binding:view / WorkflowDesign:binding:edit
--    三个权限点种子数据缺口（对照 V10 脚本同样的登记方式，此前只在代码注释里承诺
--    "见 权限资源.txt"却从未真正写入 tab_menu/tab_permission，导致这三个接口对包括
--    SUPER_ADMIN 在内的任何角色都不可授权访问；本轮 5.5 固定权限映射表需要引用这些真实
--    存在的权限编码，一并补齐）。
-- 全部使用 MySQL 5.7 兼容写法，不使用窗口函数/CTE/JSON_TABLE/厂商专属 upsert。
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- tab_approval_request：表单版本快照 id、冻结的变更前/变更后快照（design.md Decision 5
-- "申请保存完整业务快照、表单版本、before/after"）。before/after 均可为空，兼容历史申请
-- （历史申请无表单版本概念，快照字段自然为 NULL）。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_approval_request`
    ADD COLUMN `form_version_id`  BIGINT   NULL COMMENT '提交时命中的表单版本 id，关联 tab_wf_form_version.id，历史申请为空' AFTER `previous_request_id`,
    ADD COLUMN `before_snapshot`  LONGTEXT NULL COMMENT '提交时冻结的变更前业务数据快照（JSON），仅 UPDATE/ENABLE/DISABLE/DELETE 类操作有值' AFTER `form_version_id`,
    ADD COLUMN `after_snapshot`   LONGTEXT NULL COMMENT '提交时冻结的变更后业务数据快照（JSON），即 request_payload 的等价只读副本，审批过程中不可再变更' AFTER `before_snapshot`;

-- ----------------------------------------------------------------------------
-- tab_wf_node_assignee_rule：节点字段权限快照（5.2）、组织负责人固定目标组织来源（5.3）。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_node_assignee_rule`
    ADD COLUMN `field_permissions_json` LONGTEXT     NULL COMMENT '节点字段权限快照（JSON：字段标识 -> HIDDEN/READ/WRITE_REQUIRED/WRITE_OPTIONAL），DSL v2 专用，v1 恒为空' AFTER `fallback_role_code`,
    ADD COLUMN `assignee_org_source`    VARCHAR(32)  NULL COMMENT '组织负责人类来源解析组织的方式：APPLICANT_SNAPSHOT（默认，取申请人快照组织）/FIXED_ORG（取 target_org_id 指定的固定组织），仅 ORG_LEADER 类型使用' AFTER `field_permissions_json`,
    ADD COLUMN `target_org_id`          BIGINT       NULL COMMENT 'assignee_org_source=FIXED_ORG 时的固定目标组织 id，关联 tab_org.id' AFTER `assignee_org_source`;

-- ----------------------------------------------------------------------------
-- 补齐 WorkflowDesign:model:review / WorkflowDesign:binding:view / WorkflowDesign:binding:edit
-- 权限点种子数据（写法与 V10 一致）。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';

SET @workflow_model_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'WorkflowDesign:model:view');
SET @workflow_design_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'workflow-design');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '流程发布审核', 'WorkflowDesign:model:review', @workflow_model_view_id, 2, 15,
       '提交流程模型发布审核、审核决策；审核者与编辑者不能是同一人', 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE @workflow_model_view_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'WorkflowDesign:model:review');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '业务绑定查看', 'WorkflowDesign:binding:view', @workflow_design_group_id, 1, 5,
       '流程业务绑定列表/详情查看', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @workflow_design_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'WorkflowDesign:binding:view');

SET @workflow_binding_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'WorkflowDesign:binding:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '业务绑定编辑', 'WorkflowDesign:binding:edit', @workflow_binding_view_id, 2, 5,
       '新建业务绑定、切换绑定版本（含显式回滚）、启停业务绑定', 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE @workflow_binding_view_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'WorkflowDesign:binding:edit');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '流程发布审核', 'WorkflowDesign:model:review', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'WorkflowDesign:model:review');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '业务绑定查看', 'WorkflowDesign:binding:view', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'WorkflowDesign:binding:view');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '业务绑定编辑', 'WorkflowDesign:binding:edit', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'WorkflowDesign:binding:edit');

SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`
WHERE `code` IN ('WorkflowDesign:model:review', 'WorkflowDesign:binding:view', 'WorkflowDesign:binding:edit')
  AND @super_admin_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission` rp
      WHERE rp.`role_id` = @super_admin_role_id AND rp.`permission_id` = `tab_permission`.`id`
  );
