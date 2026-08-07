-- ----------------------------------------------------------------------------
-- app-api-credentials-config change：跟进两处后续调整，均不修改已应用的 V3/V4
-- 迁移文件本身（Flyway 已记录其 checksum，修改会导致校验失败），改为新增迁移：
-- 1) AppId/AccessKey 生成规则去掉 app_/ak_ 前缀（AppCredentialGenerator 已同步调整，
--    仅影响此后新生成的凭证，历史数据不做回填）；
-- 2) "应用接口配置"能力更名为"应用配置"，更新 V4 已写入 tab_menu/tab_permission 的
--    展示文案。
-- ----------------------------------------------------------------------------

UPDATE `tab_menu`
SET `name` = '应用配置', `remark` = '应用配置页面访问'
WHERE `code` = 'AppManagement:app:config';

UPDATE `tab_permission`
SET `name` = '应用配置页面访问'
WHERE `code` = 'AppManagement:app:config';

-- tab_app_config 建表时列注释里提到的 app_/ak_ 前缀已不再准确，一并修正为与当前
-- AppCredentialGenerator 实现一致的描述
ALTER TABLE `tab_app_config`
    MODIFY COLUMN `open_app_id` VARCHAR(64) NOT NULL COMMENT '对外应用标识（AppId），系统生成，全局唯一，24 位随机十六进制，不带前缀',
    MODIFY COLUMN `access_key`  VARCHAR(64) NOT NULL COMMENT '对外接口 AccessKey，系统生成，全局唯一，32 位随机十六进制，不带前缀';
