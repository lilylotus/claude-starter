-- 管理员"自动创建标记"列：若非空，表示该管理员记录是通过"按角色批量设置管理员"为这个
-- 角色 id 自动创建的；人工新增管理员、或通过"补充角色"方式获得角色的管理员，本列为 NULL。
-- 供"角色收回联动停用自动创建的管理员"规则读取（add-user-role-batch-assignment change
-- design.md Decision 7）。存量管理员记录默认为 NULL，语义正确，无需回填。
ALTER TABLE `tab_admin`
    ADD COLUMN `auto_created_role_id` BIGINT NULL COMMENT '若非空，表示该管理员是通过"按角色批量设置管理员"为这个角色 id 自动创建的' AFTER `user_id`;
