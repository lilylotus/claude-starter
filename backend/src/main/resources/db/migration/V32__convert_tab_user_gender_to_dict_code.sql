-- ----------------------------------------------------------------------------
-- 用户管理模块 - tab_user.gender 由 INT 码值改为字典编码（Flyway 迁移版本 V32）
-- 历史数据按原有码值语义原地转换：0=未知→unknown，1=男→male，2=女→female。
-- 先 MODIFY COLUMN（MySQL 会把已有的 0/1/2 原样转成字符串 "0"/"1"/"2"），
-- 再用 UPDATE 语句把这三个数字字符串重新映射成字典编码，顺序不能反。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_user`
    MODIFY COLUMN `gender` VARCHAR(64) NOT NULL DEFAULT 'unknown' COMMENT '性别，取自字典类型 gender 下的字典项编码';

UPDATE `tab_user` SET `gender` = 'unknown' WHERE `gender` = '0';
UPDATE `tab_user` SET `gender` = 'male' WHERE `gender` = '1';
UPDATE `tab_user` SET `gender` = 'female' WHERE `gender` = '2';
