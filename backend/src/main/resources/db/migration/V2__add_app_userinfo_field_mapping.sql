-- ----------------------------------------------------------------------------
-- 应用用户信息响应字段映射模块（add-sso-userinfo-field-mapping change）
-- ----------------------------------------------------------------------------

-- 应用用户信息响应字段映射表：每个应用一份（CAS/OAuth2.0 协议共用），驱动 CAS
-- <cas:attributes>（含 JSON 对应节点）与 OAuth2 userinfo 响应体中除各自协议规定的固定
-- 标识（cas:user/sub）外的其余字段生成。metadata_field_id 允许为空：非空表示关联一条
-- tab_metadata_field（bizType=USER）记录，为空表示固定的"用户ID"伪字段（tab_user.id，
-- 主键，不在 tab_metadata_field 目录里，见 design.md Decision 2）。未保存过任何记录的
-- 应用视为默认两行（用户ID、姓名），不落库，由查询接口与运行时解析组件现算兜底
-- （design.md Decision 4）。列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：
-- app_id/metadata_field_id/app_field_name/app_field_code/transform_type/transform_value
-- 均非保留字。全新数据库上无种子数据。
CREATE TABLE IF NOT EXISTS `tab_app_userinfo_field_mapping` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_id`            BIGINT       NOT NULL COMMENT '所属应用 id，关联 tab_app.id',
    `metadata_field_id` BIGINT       NULL COMMENT '本地字段，关联 tab_metadata_field.id；为空表示固定的“用户ID”伪字段',
    `app_field_name`    VARCHAR(128) NOT NULL COMMENT '应用侧目标字段名称，管理员手工填写',
    `app_field_code`    VARCHAR(128) NOT NULL COMMENT '应用侧目标字段编码，管理员手工填写',
    `transform_type`    VARCHAR(16)  NOT NULL DEFAULT 'NO_TRANSFORM'
        COMMENT '转换方式：NO_TRANSFORM=不转换，FIXED_VALUE=固定值，SCRIPT=转换脚本',
    `transform_value`   TEXT         NULL COMMENT '转换取值：固定值的具体值，或脚本源码，NO_TRANSFORM 时为空',
    `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_userinfo_field_mapping` (`app_id`, `app_field_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用用户信息响应字段映射表，CAS/OAuth2.0 协议共用，驱动 CAS 属性/OAuth2 userinfo 响应字段动态生成';
