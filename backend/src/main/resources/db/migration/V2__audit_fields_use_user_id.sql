-- ---- 审计字段 create_by/update_by 落库内容从"账号编码"改为"用户 id 的字符串形式" ----
-- 列类型保持 VARCHAR(64) 不变（audit-fields-store-user-id change design.md Decision 5），
-- 本脚本只更新种子数据里出现过的占位字符串 'system'/'admin'（V1 里的实际取值，已逐一核对），
-- 不包含任何 ALTER TABLE 语句。tab_login_log 语义特殊，明确排除在外，不在本脚本范围内。

SET @admin_user_id = (SELECT id FROM tab_user WHERE code = 'admin' LIMIT 1);
SET @admin_user_id_text = CAST(@admin_user_id AS CHAR);

-- 种子超级管理员账号自己那一行：create_by/update_by 改为指向自身 id（自引用——
-- 种子数据里"系统"这个概念本身就是这个初始账号）。
UPDATE tab_user
SET create_by = @admin_user_id_text,
    update_by = @admin_user_id_text
WHERE id = @admin_user_id;

UPDATE tab_user_password
SET create_by = @admin_user_id_text,
    update_by = @admin_user_id_text
WHERE user_id = @admin_user_id;

-- 其余业务表：把占位字符串 'system'/'admin' 替换为种子超级管理员用户 id 的字符串形式。
UPDATE tab_org
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_dict_type
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_dict_item
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_user_position
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_app
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_role
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_permission
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_role_permission
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_menu
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_admin
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_admin_role
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_admin_org_scope
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_operation_log
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_metadata_field
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_form_field_definition
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');

UPDATE tab_import_field_config
SET create_by = @admin_user_id_text, update_by = @admin_user_id_text
WHERE create_by IN ('system', 'admin') OR update_by IN ('system', 'admin');
