-- ----------------------------------------------------------------------------
-- app-api-credentials-config change：应用同步配置补充基础同步配置项——同步方式
-- （通知/拉取，整个应用一份，不分组织/用户/应用/字典）；同步方式为"通知"时，
-- 需要额外配置回调接口地址与自定义参数。仍然只做配置存储，不实现真正的 HTTP
-- 通知发送或拉取接口（design.md Decision 8 一致的范围边界）。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：sync_mode/notify_url/
-- notify_params 均非保留字。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_app_config`
    ADD COLUMN `sync_mode` VARCHAR(16) NOT NULL DEFAULT 'PULL' COMMENT '同步方式：NOTIFY=通知（本系统主动回调外部接口），PULL=拉取（外部系统主动调用本系统接口）' AFTER `sync_dict_enabled`,
    ADD COLUMN `notify_url` VARCHAR(255) DEFAULT NULL COMMENT '通知回调接口地址（http/https），sync_mode=NOTIFY 时必填' AFTER `sync_mode`,
    ADD COLUMN `notify_params` TEXT DEFAULT NULL COMMENT '通知请求自定义参数，JSON 对象（key-value 均为字符串），sync_mode=PULL 时通常为空' AFTER `notify_url`;
