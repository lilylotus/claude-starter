-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库迁移脚本 V3
-- 初始化默认管理登录用户 admin/admin，用于系统首次部署时的登录引导（在这条数据
-- 之前，业务接口已被 IdentityAuthFilter 统一要求携带 identity-token，新装环境
-- 没有任何用户就无法通过接口创建第一个用户，形成"先有鸡还是先有蛋"的问题）。
-- 密码摘要用 MySQL 内置 SHA2() 现算，算法与
-- cn.nihility.rbac.auth.util.PasswordDigestUtils#digest 保持一致：
-- SHA-256(明文密码 + 盐值) 的十六进制小写字符串。
-- 首登标识 first_login 置为 1，登录后会被强制要求先修改密码，缩短默认弱口令
-- （5 位数字字母，短于 ChangePasswordRequest 对"新密码"施加的 6 位下限，但改密
-- 校验只约束新密码长度，不约束旧密码，因此可以正常改密）暴露的窗口。
-- ----------------------------------------------------------------------------

SET @admin_salt = 'e648f0bb6c2c586063398f07dc2d0e08';

INSERT INTO `tab_user` (`name`, `code`, `gender`, `status`, `remark`, `create_by`, `create_time`)
VALUES ('系统管理员', 'admin', 'unknown', 2000, '系统初始化默认管理账号，首次登录后请立即修改密码', 'system', NOW());

INSERT INTO `tab_user_password` (`user_id`, `password_digest`, `salt`, `first_login`, `create_by`, `create_time`)
VALUES (LAST_INSERT_ID(), LOWER(SHA2(CONCAT('admin', @admin_salt), 256)), @admin_salt, 1, 'system', NOW());
