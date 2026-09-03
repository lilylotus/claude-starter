-- ----------------------------------------------------------------------------
-- production-approval-lifecycle change 第 3 节 DSL v2：新增 fallback_role_code 列，配合
-- empty_assignee_strategy 新增的 BLOCK/FALLBACK_ROLE 取值（DSL v2 专用，v1 编译器从不产生，
-- 不影响既有 v1 数据行）。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_node_assignee_rule`
    ADD COLUMN `fallback_role_code` VARCHAR(64) NULL COMMENT '兜底角色编码，仅 empty_assignee_strategy=FALLBACK_ROLE 时使用' AFTER `empty_assignee_strategy`;
