-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库迁移脚本 V6
-- 把仓库根目录 权限资源.txt 中登记的全部 94 条"模块:资源:操作"三段式资源编码种子化为
-- tab_permission 记录（按 权限资源.txt 原有的模块分段插入，name 取该行编码后的中文
-- 描述，code 就是编码本身，show_order 统一给默认值 0，status=2000 启用），
-- 新增一个拥有全部种子权限点的"超级管理员"角色，并把默认登录账号 admin
-- （password-login-auth change 已种子化 tab_user.code='admin'）接入这个角色，
-- 避免本次新增的运行时鉴权机制把默认账号自己锁死。
--
-- 本文件由一次性脚本（gen_permission_seed.py，解析 权限资源.txt 生成 INSERT 语句）
-- 生成后拼接，不是手工逐条转抄；已核对生成的 INSERT 行数与 权限资源.txt 中
-- "^- [A-Za-z]" 开头的行数一致，均为 94 条。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 1. 权限点种子数据（按 权限资源.txt 模块分段，共 94 条）
-- ============================================================================

-- OrgManagement（组织管理，/identity/orgs）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('组织管理页面访问（含左侧组织树浏览）', 'OrgManagement:org:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增组织', 'OrgManagement:org:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑组织', 'OrgManagement:org:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看组织详情', 'OrgManagement:org:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用组织', 'OrgManagement:org:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用组织', 'OrgManagement:org:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除组织', 'OrgManagement:org:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('下载组织导入模板', 'OrgManagement:org:importTemplate', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('批量导入组织', 'OrgManagement:org:import', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- UserManagement（用户管理，/identity/users）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('用户管理页面访问', 'UserManagement:user:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增用户', 'UserManagement:user:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑用户', 'UserManagement:user:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看用户详情', 'UserManagement:user:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用用户', 'UserManagement:user:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用用户', 'UserManagement:user:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除用户', 'UserManagement:user:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('下载用户导入模板', 'UserManagement:user:importTemplate', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('批量导入用户', 'UserManagement:user:import', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('重置用户密码', 'UserManagement:user:resetPassword', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- PositionManagement（任职管理，/identity/positions）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('任职管理页面访问', 'PositionManagement:position:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增任职记录', 'PositionManagement:position:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑任职记录', 'PositionManagement:position:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看任职详情', 'PositionManagement:position:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用任职记录', 'PositionManagement:position:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用任职记录', 'PositionManagement:position:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除任职记录', 'PositionManagement:position:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('下载任职导入模板', 'PositionManagement:position:importTemplate', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('批量导入任职记录', 'PositionManagement:position:import', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- AppManagement（应用管理，/application/list）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('应用管理页面访问', 'AppManagement:app:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增应用', 'AppManagement:app:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑应用', 'AppManagement:app:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看应用详情', 'AppManagement:app:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用应用', 'AppManagement:app:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用应用', 'AppManagement:app:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除应用', 'AppManagement:app:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('下载应用导入模板', 'AppManagement:app:importTemplate', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('批量导入应用', 'AppManagement:app:import', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- RoleManagement（角色管理，/permission/roles）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('角色管理页面访问', 'RoleManagement:role:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增角色', 'RoleManagement:role:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑角色', 'RoleManagement:role:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看角色详情', 'RoleManagement:role:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用角色', 'RoleManagement:role:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用角色', 'RoleManagement:role:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除角色', 'RoleManagement:role:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- PermissionManagement（权限点管理，/permission/points）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('权限点管理页面访问', 'PermissionManagement:permission:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增权限点', 'PermissionManagement:permission:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑权限点', 'PermissionManagement:permission:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看权限点详情', 'PermissionManagement:permission:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用权限点', 'PermissionManagement:permission:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用权限点', 'PermissionManagement:permission:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除权限点', 'PermissionManagement:permission:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- AdminManagement（管理员管理，/permission/admins）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('管理员管理页面访问', 'AdminManagement:admin:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增管理员', 'AdminManagement:admin:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑管理员', 'AdminManagement:admin:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看管理员详情', 'AdminManagement:admin:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用管理员', 'AdminManagement:admin:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用管理员', 'AdminManagement:admin:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除管理员', 'AdminManagement:admin:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- MenuManagement（菜单管理，/system/menus）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('菜单/资源管理页面访问', 'MenuManagement:menu:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增资源', 'MenuManagement:menu:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑资源', 'MenuManagement:menu:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看资源详情', 'MenuManagement:menu:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用资源', 'MenuManagement:menu:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用资源', 'MenuManagement:menu:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除资源', 'MenuManagement:menu:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- DictManagement（字典管理，/system/dicts）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('字典管理页面访问（含字典类型列表）', 'DictManagement:dictType:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增字典类型', 'DictManagement:dictType:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑字典类型', 'DictManagement:dictType:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看字典类型详情', 'DictManagement:dictType:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用字典类型', 'DictManagement:dictType:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用字典类型', 'DictManagement:dictType:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除字典类型', 'DictManagement:dictType:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增字典项（需先选中左侧字典类型）', 'DictManagement:dictItem:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑字典项', 'DictManagement:dictItem:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看字典项详情', 'DictManagement:dictItem:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用字典项', 'DictManagement:dictItem:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用字典项', 'DictManagement:dictItem:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除字典项', 'DictManagement:dictItem:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- MetadataFieldManagement（元数据配置，/system/metadata-fields）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('元数据配置页面访问', 'MetadataFieldManagement:metadataField:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑元数据字段（仅字段名称可改）', 'MetadataFieldManagement:metadataField:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看元数据字段详情', 'MetadataFieldManagement:metadataField:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用元数据字段', 'MetadataFieldManagement:metadataField:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用元数据字段', 'MetadataFieldManagement:metadataField:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- FormFieldManagement（表单管理，/system/form-fields）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('表单管理页面访问（含"字段定义""导入模板配置"两个 tab）', 'FormFieldManagement:formField:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增表单字段定义（从元数据配置目录选择可用字段绑定）', 'FormFieldManagement:formField:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑表单字段定义', 'FormFieldManagement:formField:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('查看表单字段定义详情', 'FormFieldManagement:formField:detail', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('启用表单字段定义', 'FormFieldManagement:formField:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('停用表单字段定义', 'FormFieldManagement:formField:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除表单字段定义', 'FormFieldManagement:formField:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('新增导入字段配置（从当前业务对象类型下启用的表单字段定义中选择关联字段）', 'FormFieldManagement:importFieldConfig:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('编辑导入字段配置', 'FormFieldManagement:importFieldConfig:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('删除导入字段配置', 'FormFieldManagement:importFieldConfig:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- OperationLogManagement（操作日志管理，/system/logs）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('操作日志管理页面访问', 'OperationLogManagement:log:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- ============================================================================
-- 2. 超级管理员角色，关联全部种子权限点
-- ============================================================================

INSERT INTO `tab_role` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('超级管理员', 'SUPER_ADMIN', 0, '系统初始化角色，拥有全部权限点', 2000, 'system', NOW(), 'system', NOW());

SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, 'system', NOW(), 'system', NOW()
FROM `tab_permission`;

-- ============================================================================
-- 3. 默认账号 admin 引导授权：赋予管理员身份并关联超级管理员角色
-- ============================================================================

SET @admin_user_id := (SELECT `id` FROM `tab_user` WHERE `code` = 'admin');

INSERT INTO `tab_admin` (`name`, `code`, `user_id`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                          `update_by`, `update_time`)
VALUES ('系统管理员', 'admin', @admin_user_id, 0, '系统初始化默认管理员身份', 2000, 'system', NOW(), 'system', NOW());

SET @admin_admin_id := (SELECT `id` FROM `tab_admin` WHERE `code` = 'admin');

INSERT INTO `tab_admin_role` (`admin_id`, `role_id`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES (@admin_admin_id, @super_admin_role_id, 'system', NOW(), 'system', NOW());
