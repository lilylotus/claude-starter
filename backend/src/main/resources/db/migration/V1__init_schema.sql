-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库基线脚本（Flyway 迁移版本 V1）
-- 本文件由原 V1~V10 共 10 个迁移文件合并而来（openspec change
-- consolidate-flyway-migrations-v4，此前三次分别是 consolidate-flyway-migrations/
-- -v2/-v3），代表这些迁移按顺序执行完毕后的最终数据库状态（全部 40 张表的建表语句 +
-- 全部种子数据），不再保留中间过程中的 ALTER/UPDATE/DROP 步骤与已被后续存量数据
-- 回填但对全新数据库无意义的 INSERT...SELECT。整体按"先建表、后插入有依赖关系的
-- 种子数据"的顺序线性组织。
-- 数据库需提前手动创建，例如：
--   CREATE DATABASE rbac_demo DEFAULT CHARACTER SET utf8mb4;
-- 注意：本地开发库如果已经跑过旧的 V1~V10，需要先清空该库（或删除
-- flyway_schema_history 表）后重新执行本文件，否则 Flyway 会因为找不到对应版本号
-- 的历史文件而报错。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 第一部分：建表语句（共 40 张表）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 组织管理模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_org`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '组织名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '组织编码',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级组织 id，0 表示顶级/根节点',
    `parent_code` VARCHAR(64)  NULL COMMENT '上级组织编码',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `ext1`        VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`        VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`        VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`        VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`        VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`        VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`        VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`        VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`        VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`       VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_org_parent_id` (`parent_id`),
    KEY `idx_tab_org_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '组织机构表';

-- ----------------------------------------------------------------------------
-- 字典管理模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_dict_type`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '字典类型名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '字典类型编码，未删除范围内全局唯一',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_dict_type_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '字典类型表';

CREATE TABLE IF NOT EXISTS `tab_dict_item`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `dict_type_id` BIGINT       NOT NULL COMMENT '所属字典类型 id，关联 tab_dict_type.id',
    `label`        VARCHAR(64)  NOT NULL COMMENT '字典项标签（展示文案）',
    `code`         VARCHAR(64)  NOT NULL COMMENT '字典项编码，同一 dict_type_id 下未删除范围内唯一',
    `show_order`   INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`       VARCHAR(255) NULL COMMENT '备注',
    `status`       INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`    VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`  DATETIME     NULL COMMENT '创建时间',
    `update_by`    VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`  DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_dict_item_dict_type_id` (`dict_type_id`),
    KEY `idx_tab_dict_item_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '字典项表';

-- ----------------------------------------------------------------------------
-- 用户管理 / 任职管理模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_user`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64) NOT NULL COMMENT '用户姓名',
    `code`        VARCHAR(64) NOT NULL COMMENT '用户编号，未删除范围内唯一',
    `gender`      VARCHAR(64) NOT NULL DEFAULT 'unknown' COMMENT '性别，取自字典类型 gender 下的字典项编码',
    `mobile`      VARCHAR(20) NULL COMMENT '手机号，不做唯一性约束',
    `id_card`     VARCHAR(18) NULL COMMENT '身份证号，若提供需在未删除范围内唯一',
    `show_order`  INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `status`      INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `ext1`        VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`        VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`        VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`        VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`        VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`        VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`        VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`        VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`        VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`       VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`   VARCHAR(64) NULL COMMENT '创建人',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64) NULL COMMENT '更新人',
    `update_time` DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_code` (`code`),
    KEY `idx_tab_user_id_card` (`id_card`),
    KEY `idx_tab_user_mobile` (`mobile`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS `tab_user_position`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`          BIGINT      NOT NULL COMMENT '所属用户 id，关联 tab_user.id',
    `org_id`           BIGINT      NOT NULL COMMENT '所属组织 id，关联 tab_org.id，不建物理外键',
    `position_type`    VARCHAR(64) NOT NULL COMMENT '任职类型编码，取自字典类型 position_type 下的字典项编码',
    `position_address` VARCHAR(255) NULL COMMENT '任职地址',
    `position_phone`   VARCHAR(20) NULL COMMENT '任职电话',
    `show_order`       INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `status`           INT NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `ext1`             VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`             VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`             VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`             VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`             VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`             VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`             VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`             VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`             VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`            VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`        VARCHAR(64) NULL COMMENT '创建人',
    `create_time`      DATETIME    NULL COMMENT '创建时间',
    `update_by`        VARCHAR(64) NULL COMMENT '更新人',
    `update_time`      DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_position_user_id` (`user_id`),
    KEY `idx_tab_user_position_org_id` (`org_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户任职记录表，无独立 status 列，任职记录做物理删除';

-- 用户密码表：独立存放密码摘要、盐值与首登强制改密标识，不侵入 tab_user 表结构。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：password_digest（不用
-- password，避免与历史版本 MySQL 的 PASSWORD() 函数产生歧义）、salt、first_login、
-- user_id 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_user_password` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户 id，关联 tab_user.id，唯一',
    `password_digest` VARCHAR(64)  NOT NULL COMMENT 'SHA-256(明文密码 + 盐值) 摘要，十六进制小写',
    `salt`            VARCHAR(32)  NOT NULL COMMENT '摘要盐值，SecureRandom 随机生成，十六进制编码',
    `first_login`     TINYINT   NOT NULL DEFAULT 1 COMMENT '是否处于待首次登录强制改密状态：1=是，0=否',
    `create_by`       VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_password_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '用户密码表，每个用户仅保留一条当前有效密码记录，改密即整行 UPDATE，不保留历史密码';

-- ----------------------------------------------------------------------------
-- 应用管理模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_app` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '应用名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '应用编码',
    `owner_id`    BIGINT       NOT NULL COMMENT '负责人用户 id',
    `org_id`      BIGINT       NOT NULL COMMENT '所属组织 id',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `ext1`        VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`        VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`        VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`        VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`        VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`        VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`        VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`        VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`        VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`       VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_owner_id` (`owner_id`),
    KEY `idx_tab_app_org_id` (`org_id`),
    KEY `idx_tab_app_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用主数据表';

-- 应用对外接口凭证配置表：与 tab_app 一对一，存放 AppId/AccessKey/SecretKey、签名
-- 算法与基础同步配置（同步方式 + 通知回调地址/参数 + 同步总开关）。列名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_id/open_app_id/access_key/
-- secret_key/sign_algorithm/sync_master_enabled/sync_mode/notify_url/notify_params
-- 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_config` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_id`               BIGINT       NOT NULL COMMENT '所属应用 id，关联 tab_app.id，一对一唯一',
    `open_app_id`          VARCHAR(64)  NOT NULL COMMENT '对外应用标识（AppId），系统生成，全局唯一，24 位随机十六进制，不带前缀',
    `access_key`           VARCHAR(64)  NOT NULL COMMENT '对外接口 AccessKey，系统生成，全局唯一，32 位随机十六进制，不带前缀',
    `secret_key`           VARCHAR(255) NOT NULL COMMENT '对外接口 SecretKey，落库前经 SM4 对称加密（Base64），不存明文，仅重置接口单次返回明文',
    `sign_algorithm`       VARCHAR(16)  NOT NULL DEFAULT 'SHA256' COMMENT '接口签名算法：SHA256 或 SM3',
    `need_sign`            TINYINT   NOT NULL DEFAULT 0 COMMENT '是否需要签名/验签校验',
    `sync_master_enabled`  TINYINT   NOT NULL DEFAULT 1
        COMMENT '同步总开关：1=开启，0=关闭，关闭后不再产生该应用的数据变更记录、不发送通知、拉取接口返回空结果',
    `sync_mode`            VARCHAR(16)  NOT NULL DEFAULT 'PULL' COMMENT '同步方式：NOTIFY=通知（本系统主动回调外部接口），PULL=拉取（外部系统主动调用本系统接口）',
    `notify_url`     VARCHAR(255)          DEFAULT NULL COMMENT '通知回调接口地址（http/https），sync_mode=NOTIFY 时必填',
    `notify_params`  TEXT                  DEFAULT NULL COMMENT '通知请求自定义参数，JSON 对象（key-value 均为字符串），sync_mode=PULL 时通常为空',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_config_app_id` (`app_id`),
    UNIQUE KEY `uk_tab_app_config_open_app_id` (`open_app_id`),
    UNIQUE KEY `uk_tab_app_config_access_key` (`access_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用对外接口凭证配置表，与 tab_app 一对一，无独立 status，随所属应用整体维护';

-- 应用单点登录协议配置表：与 tab_app 一对一，仅存协议类型（无/CAS/OAuth2.0）、回跳
-- 地址匹配规则列表（CAS/OAuth2.0 等协议共用同一列，unify-app-auth-service-patterns
-- change design.md Decision 1/2）与登出通知回调地址（add-sso-single-logout change
-- design.md Decision 2/3），不含运行时票据/令牌数据（app-auth-protocol-config
-- change design.md Decision 1）。列名已核对 MySQL/PostgreSQL/Oracle/SQL Server
-- 保留字：auth_protocol/service_patterns/logout_notify_url 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_auth_config` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_id`            BIGINT      NOT NULL COMMENT '所属应用 id，关联 tab_app.id，一对一唯一',
    `auth_protocol`     VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '单点登录协议类型：NONE/CAS/OAUTH2',
    `service_patterns`  TEXT                 DEFAULT NULL COMMENT '回跳地址 ANT 匹配规则列表（JSON 字符串数组），CAS/OAuth2.0 等协议共用，auth_protocol=NONE 时为空',
    `logout_notify_url` VARCHAR(255)         DEFAULT NULL COMMENT '登出通知回调地址，POST 回调该地址通知应用登出事件，未配置时登出不通知该应用',
    `create_by`         VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_auth_config_app_id` (`app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用单点登录协议配置表，与 tab_app 一对一，仅存协议类型、回跳地址匹配规则与登出通知回调地址，不含运行时票据/令牌数据';

-- 应用用户信息响应字段映射表：每个应用一份（CAS/OAuth2.0 协议共用），驱动 CAS
-- <cas:attributes>（含 JSON 对应节点）与 OAuth2 userinfo 响应体中除各自协议规定的固定
-- 标识（cas:user/sub）外的其余字段生成。metadata_field_id 允许为空：非空表示关联一条
-- tab_metadata_field（bizType=USER）记录，为空表示固定的"用户ID"伪字段（tab_user.id，
-- 主键，不在 tab_metadata_field 目录里）。未保存过任何记录的应用视为默认两行（用户ID、
-- 姓名），不落库，由查询接口与运行时解析组件现算兜底（add-sso-userinfo-field-mapping
-- change design.md Decision 2/4）。列名已核对 MySQL/PostgreSQL/Oracle/SQL Server
-- 保留字：app_id/metadata_field_id/app_field_name/app_field_code/transform_type/
-- transform_value 均非保留字。全新数据库上无种子数据。
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

-- 应用同步数据域配置表：每个应用固定 5 行，分别对应组织/用户/应用/角色/字典五个
-- 数据域，每行携带"是否启用"与"每次拉取分页大小"两个属性。列名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_ref_id/sync_domain/sync_enabled/
-- page_size 均非保留字（不用更容易和 SQL 标准 DOMAIN 关键字混淆的 domain/enabled）。
-- 全新数据库上无种子数据（应用类数据是运行时业务数据，不通过 Flyway 种子）。
CREATE TABLE IF NOT EXISTS `tab_app_sync_domain_config` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`   BIGINT      NOT NULL COMMENT '所属应用 id，关联 tab_app.id',
    `sync_domain`  VARCHAR(16) NOT NULL COMMENT '数据域：ORG/USER/APP/ROLE/DICT',
    `sync_enabled` TINYINT  NOT NULL DEFAULT 0 COMMENT '是否允许同步该数据域：0=否，1=是',
    `page_size`    INT         NOT NULL DEFAULT 20 COMMENT '每次拉取分页大小',
    `create_by`    VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_sync_domain_config_app_domain` (`app_ref_id`, `sync_domain`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用同步数据域配置表，每个应用固定 5 行（组织/用户/应用/角色/字典）';

-- 应用同步字段映射表：为组织/用户/应用/角色四个数据域（不含字典）提供字段级同步
-- 映射配置，每行关联一个 tab_metadata_field 记录（同步的源字段，字段名称/字段编码
-- 实时 JOIN 读取，不落快照），加上应用侧目标字段名称/编码、转换方式与转换取值。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_ref_id/sync_domain/
-- metadata_field_id/app_field_name/app_field_code/transform_type/transform_value
-- 均非保留字。全新数据库上无种子数据。
CREATE TABLE IF NOT EXISTS `tab_app_sync_field_mapping` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`        BIGINT       NOT NULL COMMENT '所属应用 id，关联 tab_app.id',
    `sync_domain`       VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/APP/ROLE',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '同步的源字段，关联 tab_metadata_field.id',
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
    UNIQUE KEY `uk_tab_app_sync_field_mapping` (`app_ref_id`, `sync_domain`, `metadata_field_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用同步字段映射表，组织/用户/应用/角色四个数据域各自的字段级同步映射配置';

-- ----------------------------------------------------------------------------
-- 应用数据同步通知/拉取模块
-- ----------------------------------------------------------------------------

-- 应用通知发送记录表：仅用于问题排查/展示，不驱动自动重试。定位通知记录直接使用
-- data_type/biz_id 两列，通知也改为数据变更时直接判定候选应用并即时触发，不再有
-- 任何"变更记录"表居中转发（app-sync-drop-changelog change design.md Migration
-- Plan）。notify_url 记录本次回调实际使用的地址快照，不依赖 tab_app_config.notify_url
-- 的当前值，避免管理员事后改了回调地址导致历史记录被误导（add-app-sync-notify-pull-logs
-- change design.md Decision 1）。列名已核对 MySQL/PostgreSQL/Oracle/SQL Server
-- 保留字：app_ref_id/data_type/biz_id/notify_status/http_status/error_msg/notify_url
-- 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_notify_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`    BIGINT       NOT NULL COMMENT '关联 tab_app.id',
    `data_type`     VARCHAR(20)  NULL COMMENT '数据类型：ORG/USER/POSITION/APP/ROLE，历史数据为空',
    `biz_id`        BIGINT       NULL COMMENT '被变更对象 id，历史数据为空',
    `notify_status` TINYINT      NOT NULL COMMENT '通知状态：1=成功，2=失败',
    `http_status`   INT          NULL COMMENT '外部接口返回的 HTTP 状态码，失败且未收到响应时为空',
    `error_msg`     VARCHAR(500) NULL COMMENT '失败原因摘要',
    `notify_url`    VARCHAR(255) NULL COMMENT '本次回调实际使用的地址快照，历史数据为空',
    `create_by`     VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_notify_record_app_ref_id` (`app_ref_id`),
    KEY `idx_tab_app_notify_record_app_time` (`app_ref_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用通知发送记录表，仅用于问题排查/展示，不驱动自动重试';

-- 应用拉取变更数据请求记录表：记录外部应用调用分页拉取接口的请求，仅用于问题排查/
-- 展示，不驱动任何业务逻辑，不涉及日志保留/清理策略（add-app-sync-notify-pull-logs
-- change design.md Decision 2，proposal.md Non-Goals）。拉取接口后来改为直接分页
-- 查询业务表当前数据，原按 id/按序列号两种拉取方式合并为统一分页拉取接口，
-- pull_mode 列随之下线（app-sync-drop-changelog change design.md Migration Plan）。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_ref_id/data_type/
-- request_summary/result_count 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_pull_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`      BIGINT       NOT NULL COMMENT '发起拉取的应用 id，关联 tab_app.id',
    `data_type`       VARCHAR(20)  NULL COMMENT '请求的数据类型，未传时为空',
    `request_summary` VARCHAR(255) NULL COMMENT '请求参数摘要',
    `result_count`    INT          NOT NULL DEFAULT 0 COMMENT '本次返回的记录条数',
    `create_by`       VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_pull_record_app_time` (`app_ref_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用拉取变更数据请求记录，仅用于问题排查/展示';

-- ----------------------------------------------------------------------------
-- 应用同步组织范围模块
-- ----------------------------------------------------------------------------

-- 应用同步组织范围配置表：按 (app_ref_id, sync_domain) 直接关联，不额外增加模式开关列，
-- 零行=全部数据，>=1 行=指定组织范围。列名已核对 MySQL/PostgreSQL/Oracle/SQL Server
-- 保留字：app_ref_id/sync_domain/org_id/include_children 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_sync_org_scope` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`       BIGINT      NOT NULL COMMENT '所属应用 id，关联 tab_app.id',
    `sync_domain`      VARCHAR(16) NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `org_id`           BIGINT      NOT NULL COMMENT '组织 id，关联 tab_org.id',
    `include_children` TINYINT  NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_sync_org_scope` (`app_ref_id`, `sync_domain`, `org_id`),
    KEY `idx_tab_app_sync_org_scope_app_domain` (`app_ref_id`, `sync_domain`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用同步组织范围配置表，零行=全部数据，>=1 行=指定组织范围';

-- ----------------------------------------------------------------------------
-- 应用访问授权模块（app-access-authorization / app-access-request-control change）
-- ----------------------------------------------------------------------------
-- 八张表：策略规则本身、策略的组织范围条件、策略的用户属性条件（关联
-- tab_metadata_field，biz_type=USER）、策略的目标应用、策略的浏览器白名单条件、策略
-- 的 IP/网段白名单条件、策略计算结果（POLICY 来源授权记录，按 policy_id 整体重建），
-- 以及独立的人工例外表（GRANT/DENY，与策略结果表物理隔离，互不影响）。请求控制
-- （浏览器/IP 白名单）是运行时校验，不参与策略"执行"的批量身份计算，不影响
-- tab_app_access_policy_grant 表结构（app-access-request-control change design.md
-- Decision 2）。全部不建物理外键，字段命名已核对 MySQL/PostgreSQL/Oracle/SQL Server
-- 保留字：name/remark/status/last_exec_time/last_exec_by/policy_id/org_id/
-- include_children/metadata_field_id/operator/attr_value/app_id/browser_code/
-- ip_cidr/user_id/override_type 均非保留字。

-- 策略规则主表：名称、备注、启用状态、最近一次执行时间/执行人。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`           VARCHAR(128) NOT NULL COMMENT '策略名称',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `status`         INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用',
    `last_exec_time` DATETIME     NULL COMMENT '最近一次执行时间，从未执行过为空',
    `last_exec_by`   VARCHAR(64)  NULL COMMENT '最近一次执行人',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_access_policy_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略规则表';

-- 策略组织范围条件：零条或多条，整体替换语义（先删后插），字段形状对齐
-- tab_admin_org_scope 范式。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_org_scope` (
    `id`               BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`        BIGINT     NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `org_id`           BIGINT     NOT NULL COMMENT '组织 id，关联 tab_org.id，不建物理外键',
    `include_children` TINYINT NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_org_scope` (`policy_id`, `org_id`),
    KEY `idx_tab_app_access_policy_org_scope_org_id` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略组织范围条件表';

-- 策略用户属性条件：零条或多条，关联 tab_metadata_field（biz_type=USER）的
-- metadata_field_id，运算符仅 EQ/NE/IN 三种，IN 时 attr_value 为逗号分隔多值。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_user_attr` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`         BIGINT       NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '关联的元数据字段 id，关联 tab_metadata_field.id（biz_type=USER），不建物理外键',
    `operator`          VARCHAR(8)   NOT NULL COMMENT '运算符：EQ=等于，NE=不等于，IN=属于多值',
    `attr_value`        VARCHAR(255) NOT NULL COMMENT '比较值，EQ/NE 为单个值，IN 为逗号分隔的多个值',
    `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_user_attr` (`policy_id`, `metadata_field_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略用户属性条件表';

-- 策略目标应用：一条策略对应一批具体应用 id，多选，至少一个。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_target_app` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`   BIGINT   NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `app_id`      BIGINT   NOT NULL COMMENT '目标应用 id，关联 tab_app.id，不建物理外键',
    `create_by`   VARCHAR(64)        DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)        DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_target_app` (`policy_id`, `app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略目标应用表';

-- 策略浏览器白名单条件：零条或多条，browser_code 取值枚举 CHROME/FIREFOX/SAFARI/
-- EDGE/OPERA/IE，与 UserAgentParser.parseBrowser 能识别的浏览器一一对应。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_browser_rule` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`    BIGINT      NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `browser_code` VARCHAR(16) NOT NULL COMMENT '浏览器编码：CHROME/FIREFOX/SAFARI/EDGE/OPERA/IE',
    `create_by`    VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_browser_rule` (`policy_id`, `browser_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略浏览器白名单条件表';

-- 策略 IP/网段白名单条件：零条或多条，ip_cidr 存原始字符串（单 IP 或 CIDR 网段），保存前
-- 由服务层用 IpCidrMatcher.isValidRule 校验格式合法。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_ip_rule` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`    BIGINT      NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `ip_cidr`      VARCHAR(64) NOT NULL COMMENT '单个 IP 地址或 CIDR 网段，如 192.168.1.100 或 192.168.1.0/24',
    `create_by`    VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_ip_rule` (`policy_id`, `ip_cidr`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略 IP/网段白名单条件表';

-- 策略计算结果表（POLICY 来源授权记录）：点击"执行"后按 policy_id 整体重建
-- （DELETE ... WHERE policy_id=:id 后批量插入），只存策略计算结果，不存人工例外
-- （与 tab_app_access_manual_override 物理隔离）。同一 user_id+app_id 组合可能出现
-- 在多条不同 policy_id 下（多个策略都命中同一人同一应用是正常场景），故唯一约束含
-- policy_id。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_grant` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`   BIGINT   NOT NULL COMMENT '产生该授权记录的策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 id，关联 tab_user.id，不建物理外键',
    `app_id`      BIGINT   NOT NULL COMMENT '应用 id，关联 tab_app.id，不建物理外键',
    `create_by`   VARCHAR(64)        DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)        DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_grant` (`policy_id`, `user_id`, `app_id`),
    KEY `idx_tab_app_access_policy_grant_user_app` (`user_id`, `app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用访问授权策略计算结果表，按 policy_id 整体重建，SHALL NOT 影响人工例外记录';

-- 人工例外表：对具体"用户+应用"组合手动追加授权（GRANT）或手动收回授权（DENY），
-- 优先级最高，与策略计算结果表物理隔离。每个用户+应用组合同一时刻至多一条记录，
-- upsert 语义（重复提交更新已有记录而不是新增）。
CREATE TABLE IF NOT EXISTS `tab_app_access_manual_override` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`        BIGINT      NOT NULL COMMENT '用户 id，关联 tab_user.id，不建物理外键',
    `app_id`         BIGINT      NOT NULL COMMENT '应用 id，关联 tab_app.id，不建物理外键',
    `override_type`  VARCHAR(8)  NOT NULL COMMENT '例外类型：GRANT=手动追加授权，DENY=手动收回授权',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_manual_override` (`user_id`, `app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权人工例外表（GRANT/DENY），优先级高于策略计算结果';

-- ----------------------------------------------------------------------------
-- 角色管理模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '角色编码',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_role_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色主数据表';

-- ----------------------------------------------------------------------------
-- 权限点管理模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_permission` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '权限编码',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_permission_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限点主数据表';

-- 角色权限点关联表：模式对齐 tab_admin_role，无独立 status，角色新增/更新时整体
-- 同步（先按 role_id 物理删除既有关联，再按提交内容重建），不做按行 diff，也不做
-- 逻辑删除。列名 role_id/permission_id 已核对，与 MySQL/PostgreSQL/Oracle/SQL
-- Server 保留字均无冲突。
CREATE TABLE IF NOT EXISTS `tab_role_permission` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `role_id`       BIGINT      NOT NULL COMMENT '角色 id，关联 tab_role.id',
    `permission_id` BIGINT      NOT NULL COMMENT '权限点 id，关联 tab_permission.id',
    `create_by`     VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_role_permission` (`role_id`, `permission_id`),
    KEY `idx_tab_role_permission_role_id` (`role_id`),
    KEY `idx_tab_role_permission_permission_id` (`permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '角色权限点关联表，无独立 status，随角色整体同步、物理删除';

-- ----------------------------------------------------------------------------
-- 菜单管理模块（菜单/按钮/API 资源）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_menu` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`          VARCHAR(64)  NOT NULL COMMENT '资源名称',
    `code`          VARCHAR(64)  NOT NULL COMMENT '资源编码',
    `parent_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '上级资源 id，0 表示顶级',
    `resource_type` INT          NOT NULL COMMENT '资源类型：1=菜单，2=按钮，3=API',
    `show_order`    INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`        INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`     VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_menu_parent_id` (`parent_id`),
    KEY `idx_tab_menu_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单/按钮/API 资源主数据表';

-- ----------------------------------------------------------------------------
-- 管理员管理模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '管理员名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '管理员编码',
    `user_id`     BIGINT       NOT NULL COMMENT '关联用户 id，关联 tab_user.id，不建物理外键',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_admin_code` (`code`),
    KEY `idx_tab_admin_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员主数据表';

CREATE TABLE IF NOT EXISTS `tab_admin_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`    BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `role_id`     BIGINT       NOT NULL COMMENT '角色 id，关联 tab_role.id',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_role` (`admin_id`, `role_id`),
    KEY `idx_tab_admin_role_admin_id` (`admin_id`),
    KEY `idx_tab_admin_role_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员角色关联表，无独立 status，随管理员整体同步、物理删除';

CREATE TABLE IF NOT EXISTS `tab_admin_org_scope` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`         BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `org_id`           BIGINT       NOT NULL COMMENT '组织 id，关联 tab_org.id',
    `include_children` TINYINT   NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_org_scope` (`admin_id`, `org_id`),
    KEY `idx_tab_admin_org_scope_admin_id` (`admin_id`),
    KEY `idx_tab_admin_org_scope_org_id` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员组织管辖范围表，无独立 status，随管理员整体同步、物理删除';

-- ----------------------------------------------------------------------------
-- 操作日志模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_operation_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `module`             VARCHAR(32)  NOT NULL COMMENT '业务模块中文名，如"组织管理"',
    `resource_type`      VARCHAR(32)  NOT NULL COMMENT '资源类型编码，如 org/user/position/app/role/permission/admin/menu/dictType/dictItem',
    `resource_name`      VARCHAR(32)  NOT NULL COMMENT '资源类型中文名，如"组织"',
    `operation_type`     INT          NOT NULL COMMENT '操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除',
    `operate_source`     INT          NOT NULL DEFAULT 0 COMMENT '操作来源：0=界面操作，1=Excel导入',
    `target_id`          BIGINT       NOT NULL COMMENT '被操作对象主键 id',
    `target_name`        VARCHAR(128) NOT NULL COMMENT '被操作对象名称快照，即使对象后续被改名/删除也保留操作当时的名称',
    `change_detail`      TEXT         NOT NULL COMMENT '字段变更详情，JSON 数组，每项 {field,oldValue,newValue}',
    `operate_ip`         VARCHAR(64)  DEFAULT NULL COMMENT '操作发起 IP，从当前 HTTP 请求自动获取，取不到（如非 HTTP 上下文中调用）时为空',
    `operate_terminal`   VARCHAR(32)  DEFAULT NULL COMMENT '操作终端类型，如 PC/Mobile/Tablet，从 User-Agent 解析，解析不出时为空',
    `operate_os`         VARCHAR(64)  DEFAULT NULL COMMENT '操作系统，如 Windows 10/macOS/Android，从 User-Agent 解析，解析不出时为空',
    `operate_browser`    VARCHAR(64)  DEFAULT NULL COMMENT '操作浏览器，如 Chrome 120，从 User-Agent 解析，解析不出时为空',
    `operate_user_agent` VARCHAR(512) DEFAULT NULL COMMENT '原始 User-Agent 请求头，取不到时为空',
    `create_by`          VARCHAR(64)  NOT NULL COMMENT '操作人，即创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间，即创建时间',
    `update_by`          VARCHAR(64)  NOT NULL COMMENT '更新人，日志不可变更，恒等于 create_by',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，日志不可变更，恒等于 create_time',
    PRIMARY KEY (`id`),
    KEY `idx_tab_operation_log_resource` (`resource_type`, `target_id`),
    KEY `idx_tab_operation_log_module` (`module`),
    KEY `idx_tab_operation_log_create_by` (`create_by`),
    KEY `idx_tab_operation_log_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表，只追加不更新不删除';

-- 登录日志表：记录每一次登录尝试（成功 + 失败），只追加不更新不删除。字段名
-- （login_account/user_id/user_name/login_result/fail_reason/login_ip/
-- login_terminal/login_os/login_browser/login_user_agent）已逐一核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字，均无冲突。
CREATE TABLE IF NOT EXISTS `tab_login_log`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `login_account`    VARCHAR(64)  NULL COMMENT '本次登录尝试提交的账号，解密失败时为 NULL',
    `user_id`          BIGINT       NULL COMMENT '关联的 tab_user.id，账号不存在/解密失败时为 NULL',
    `user_name`        VARCHAR(64)  NULL COMMENT '用户姓名快照，账号不存在/解密失败时为 NULL',
    `login_result`     TINYINT      NOT NULL COMMENT '登录结果：1=成功，2=失败',
    `fail_reason`      VARCHAR(64)  NULL COMMENT '失败原因文案，登录成功时为 NULL',
    `login_ip`         VARCHAR(64)  NULL COMMENT '登录发起 IP，取不到时为空',
    `login_terminal`   VARCHAR(32)  NULL COMMENT '登录终端类型，从 User-Agent 解析，解析不出时为空',
    `login_os`         VARCHAR(32)  NULL COMMENT '登录操作系统，从 User-Agent 解析，解析不出时为空',
    `login_browser`    VARCHAR(32)  NULL COMMENT '登录浏览器，从 User-Agent 解析，解析不出时为空',
    `login_user_agent` VARCHAR(512) NULL COMMENT '原始 User-Agent 请求头，取不到时为空',
    `create_by`        VARCHAR(64)  NOT NULL COMMENT '创建人，即本次登录尝试提交的账号，为空时存 unknown',
    `create_time`      DATETIME     NOT NULL COMMENT '创建时间，即本次登录尝试发生时间',
    `update_by`        VARCHAR(64)  NOT NULL COMMENT '更新人，恒等于 create_by（本表只追加不更新）',
    `update_time`      DATETIME     NOT NULL COMMENT '更新时间，恒等于 create_time（本表只追加不更新）',
    PRIMARY KEY (`id`),
    KEY `idx_tab_login_log_login_account` (`login_account`),
    KEY `idx_tab_login_log_user_id` (`user_id`),
    KEY `idx_tab_login_log_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '登录日志表，记录每一次登录尝试（成功+失败），只追加不更新不删除';

-- ----------------------------------------------------------------------------
-- 元数据字段配置模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_metadata_field`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`    VARCHAR(20)  NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP/ROLE',
    `table_name`  VARCHAR(64)  NOT NULL COMMENT '字段所属表名称，如 tab_org，创建后不可修改',
    `column_name` VARCHAR(64)  NOT NULL COMMENT '字段列名（数据库字段定义），如 code、ext6，创建后不可修改',
    `field_code`  VARCHAR(64) NOT NULL DEFAULT '' COMMENT '字段标识（前端/DTO 使用），创建后不可修改',
    `column_type` VARCHAR(32)  NOT NULL COMMENT '字段类型（数据库字段类型），如 VARCHAR(255)，创建后不可修改',
    `field_name`  VARCHAR(64)  NOT NULL COMMENT '字段名称，如"组织编码"，可编辑',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除，当前不使用）',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_metadata_field_table_column` (`table_name`, `column_name`),
    UNIQUE KEY `uk_tab_metadata_field_biz_field_code` (`biz_type`, `field_code`),
    KEY `idx_tab_metadata_field_biz_type` (`biz_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '元数据字段配置目录表';

-- ----------------------------------------------------------------------------
-- 表单字段定义模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_form_field_definition`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`          VARCHAR(20)  NOT NULL COMMENT '业务对象类型，创建时取自所绑定元数据字段，之后不可变',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '绑定的元数据字段 id，关联 tab_metadata_field.id，创建后不可改绑',
    `field_name`        VARCHAR(64)  NOT NULL COMMENT '展示名称，创建时默认取自元数据字段的 field_name，此后可独立编辑',
    `field_code`        VARCHAR(64)  NOT NULL COMMENT '前端/DTO 使用的字段标识，如 idCardNo，同一 biz_type 下唯一',
    `control_type`      INT          NOT NULL COMMENT '控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉',
    `dict_type_code`    VARCHAR(64)  NULL COMMENT '关联的字典类型编码，关联 tab_dict_type.code，仅 control_type=3/5 时必填',
    `is_unique`         TINYINT   NOT NULL DEFAULT 0 COMMENT '是否要求同 biz_type 下有效数据唯一',
    `is_required`       TINYINT   NOT NULL DEFAULT 0 COMMENT '是否必填',
    `show_in_list`      TINYINT   NOT NULL DEFAULT 1 COMMENT '是否在列表中展示',
    `show_in_create`    TINYINT   NOT NULL DEFAULT 1 COMMENT '是否在新增表单中展示',
    `show_in_edit`      TINYINT   NOT NULL DEFAULT 1 COMMENT '是否在编辑表单中展示',
    `editable`          TINYINT   NOT NULL DEFAULT 1 COMMENT '表单中展示时是否可编辑，为否则只读展示',
    `validate_regex`    VARCHAR(255) NULL COMMENT '正则校验规则，前后端共用同一个字符串',
    `placeholder`       VARCHAR(128) NULL COMMENT '输入提示文字',
    `show_order`        INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `status`            INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`         VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`       DATETIME     NULL COMMENT '创建时间',
    `update_by`         VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`       DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_form_field_definition_biz_type` (`biz_type`),
    KEY `idx_tab_form_field_definition_metadata_field_id` (`metadata_field_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '表单字段定义表';

-- ----------------------------------------------------------------------------
-- Excel 导入字段配置模块
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_import_field_config`
(
    `id`                        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`                  VARCHAR(20) NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP，创建后不可变',
    `form_field_definition_id`  BIGINT      NULL COMMENT '关联的表单字段定义 id，关联 tab_form_field_definition.id，可空',
    `field_code`                VARCHAR(64) NOT NULL COMMENT '字段标识：关联表单字段定义时取自其 field_code，POSITION/APP/ORG 的固定标识列为 __userCode/__orgCode/__ownerCode/__parentCode',
    `excel_header_name`         VARCHAR(64) NOT NULL COMMENT 'Excel 表头文字，可与表单展示名称不同',
    `is_primary_key`            TINYINT  NOT NULL DEFAULT 0 COMMENT '是否作为匹配已有记录的主键列之一',
    `is_required`               TINYINT  NOT NULL DEFAULT 0 COMMENT '导入语义下的必填，独立于表单字段定义的必填开关',
    `show_order`                INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前，决定模板表头列顺序',
    `status`                    INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，-1000=已删除（逻辑删除）',
    `create_by`                 VARCHAR(64) NULL COMMENT '创建人',
    `create_time`               DATETIME    NULL COMMENT '创建时间',
    `update_by`                 VARCHAR(64) NULL COMMENT '更新人',
    `update_time`               DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_import_field_config_biz_type` (`biz_type`),
    KEY `idx_tab_import_field_config_form_field_definition_id` (`form_field_definition_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'Excel 导入字段配置表';

-- ----------------------------------------------------------------------------
-- 身份管理 - 上游数据管理模块
-- ----------------------------------------------------------------------------

-- 上游数据源配置表：一套连接信息（接口自定义请求头 / 数据库 JDBC 连接）+ 一套调度
-- 配置。接口模式的请求头取值、数据库模式的密码均为 SM4 加密文本，复用
-- AppSecretProperties 的同一把主密钥。
CREATE TABLE IF NOT EXISTS `tab_upstream_source`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`               VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `sync_type`          VARCHAR(16)  NOT NULL COMMENT '同步方式：API=接口，DB_TABLE=数据库表',
    `enabled`            TINYINT   NOT NULL DEFAULT 0 COMMENT '是否启用，创建时默认 0（未启用）',
    `schedule_type`      VARCHAR(16)  NOT NULL DEFAULT 'INTERVAL' COMMENT '调度方式：INTERVAL=按间隔，FIXED_TIME=按每日固定时间点',
    `interval_unit`      VARCHAR(16)  NULL COMMENT '间隔单位：MINUTE/HOUR，schedule_type=INTERVAL 时使用',
    `interval_value`     INT          NULL COMMENT '间隔取值（正整数），schedule_type=INTERVAL 时使用',
    `fixed_time`         VARCHAR(5)   NULL COMMENT '每日固定时间点，HH:mm 文本，schedule_type=FIXED_TIME 时使用',
    `last_trigger_time`  DATETIME     NULL COMMENT '上次定时触发时间，供轮询任务判断是否到点，手动触发不更新该列',
    `api_auth_headers`   TEXT         NULL COMMENT '接口模式自定义请求头，JSON 文本（{key: SM4密文}），syncType=API 时使用',
    `db_jdbc_url`        VARCHAR(500) NULL COMMENT '数据库模式 JDBC 连接地址，仅支持 jdbc:mysql:// 前缀，syncType=DB_TABLE 时使用',
    `db_username`        VARCHAR(128) NULL COMMENT '数据库模式连接用户名，syncType=DB_TABLE 时使用',
    `db_password`        VARCHAR(500) NULL COMMENT '数据库模式连接密码，SM4 加密文本，syncType=DB_TABLE 时使用',
    `create_by`          VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_source_enabled` (`enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源配置表';

-- 数据域配置：每个数据源固定 3 行（ORG/USER/POSITION），各自独立启用开关与取数来源
-- （接口模式的请求 URL+方式，或数据库模式的只读查询 SQL）。
CREATE TABLE IF NOT EXISTS `tab_upstream_domain_config`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `source_id`       BIGINT       NOT NULL COMMENT '所属上游数据源 id，关联 tab_upstream_source.id',
    `data_type`       VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `enabled`         TINYINT   NOT NULL DEFAULT 0 COMMENT '是否启用该数据域',
    `api_url`         VARCHAR(500) NULL COMMENT '接口模式该数据域的请求地址，syncType=API 时使用',
    `api_method`      VARCHAR(8)   NULL COMMENT '接口模式请求方式：GET/POST，syncType=API 时使用',
    `db_sql`          TEXT         NULL COMMENT '数据库模式该数据域的只读查询 SQL（列别名对应上游字段编码），syncType=DB_TABLE 时使用',
    `last_sync_time`  DATETIME     NULL COMMENT '该数据域上次同步完成时间，仅展示用途，不驱动增量逻辑',
    `create_by`       VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_upstream_domain_config` (`source_id`, `data_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源数据域配置表';

-- 字段映射：管理员手工填写"上游字段名称/编码"作为源，选择本系统元数据字段作为目标
-- （方向与 tab_app_sync_field_mapping 相反）。只存 metadata_field_id 外键，不落快照，
-- 查询时实时 JOIN tab_metadata_field 读取。is_primary_key 标记该字段是否作为落库匹配
-- 的主键标识字段之一，同一数据域可多选组成联合主键（AND 语义）。
CREATE TABLE IF NOT EXISTS `tab_upstream_field_mapping`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `source_id`            BIGINT       NOT NULL COMMENT '所属上游数据源 id，关联 tab_upstream_source.id',
    `data_type`            VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `upstream_field_name`  VARCHAR(128) NOT NULL COMMENT '上游字段名称，管理员手工填写',
    `upstream_field_code`  VARCHAR(128) NOT NULL COMMENT '上游字段编码，管理员手工填写，同一数据源同一数据域内不允许重复',
    `metadata_field_id`    BIGINT       NOT NULL COMMENT '目标元数据字段 id，关联 tab_metadata_field.id',
    `is_primary_key`       TINYINT   NOT NULL DEFAULT 0 COMMENT '是否作为落库匹配的主键标识字段之一，同一数据域可多选组成联合主键',
    `transform_type`       VARCHAR(16)  NOT NULL COMMENT '转换方式：NO_TRANSFORM/FIXED_VALUE/SCRIPT',
    `transform_value`      VARCHAR(5000) NULL COMMENT '转换取值：固定值的具体值，或脚本源码',
    `create_by`            VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_field_mapping_source_type` (`source_id`, `data_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源字段映射表';

-- 同步执行记录：粒度对齐"数据源+数据域"，一次同步触发会为其下已启用的每个数据域各写
-- 一条。仅用于展示排查，不驱动任何自动重试。
CREATE TABLE IF NOT EXISTS `tab_upstream_sync_record`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `source_id`       BIGINT       NOT NULL COMMENT '所属上游数据源 id，关联 tab_upstream_source.id',
    `data_type`       VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/POSITION',
    `trigger_type`    VARCHAR(16)  NOT NULL COMMENT '触发方式：SCHEDULE=定时触发，MANUAL=手动触发',
    `start_time`      DATETIME     NOT NULL COMMENT '本次同步开始时间',
    `end_time`        DATETIME     NULL COMMENT '本次同步结束时间',
    `status`          VARCHAR(16)  NOT NULL COMMENT '执行状态：SUCCESS=全部成功，PARTIAL=部分失败，FAILED=全部失败或执行异常',
    `total_count`     INT          NOT NULL DEFAULT 0 COMMENT '处理总行数',
    `success_count`   INT          NOT NULL DEFAULT 0 COMMENT '成功行数',
    `fail_count`      INT          NOT NULL DEFAULT 0 COMMENT '失败行数',
    `fail_summary`    VARCHAR(500) NULL COMMENT '失败摘要文本，截断到合理长度，非完整堆栈',
    `create_by`       VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_sync_record_source` (`source_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据源同步执行记录表';

-- 上游数据同步执行记录明细表：记录每次执行记录下每一行处理的原始上游数据与结果，
-- 成功/失败均记录。source_id 冗余自所属执行记录，供按数据源级联删除，不需要联表。
CREATE TABLE IF NOT EXISTS `tab_upstream_sync_record_detail`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `sync_record_id` BIGINT       NOT NULL COMMENT '所属同步执行记录 id，关联 tab_upstream_sync_record.id',
    `source_id`      BIGINT       NOT NULL COMMENT '所属上游数据源 id，冗余自所属执行记录，供按数据源级联删除，不需要联表',
    `row_no`         INT          NOT NULL COMMENT '本次执行内该行的序号，从 1 开始',
    `row_data`       TEXT         NOT NULL COMMENT '该行的原始上游数据（取数阶段的原始行，JSON 文本）',
    `status`         VARCHAR(16)  NOT NULL COMMENT '该行处理状态：SUCCESS=成功，FAILED=失败',
    `fail_reason`    VARCHAR(500) NULL COMMENT '失败原因，仅 status=FAILED 时有值',
    `create_by`      VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_sync_record_detail_record` (`sync_record_id`, `id`),
    KEY `idx_tab_upstream_sync_record_detail_source` (`source_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据同步执行记录明细表，记录每行处理的原始数据与结果，成功/失败均记录';

-- ============================================================================
-- 第二部分：种子数据（按依赖关系顺序插入）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 默认账号引导：默认管理登录用户 admin/admin
-- ----------------------------------------------------------------------------
-- 在这条数据之前，业务接口已被 IdentityAuthFilter 统一要求携带 identity-token，
-- 新装环境没有任何用户就无法通过接口创建第一个用户，形成"先有鸡还是先有蛋"的问题。
-- 密码摘要用 MySQL 内置 SHA2() 现算，算法与
-- cn.nihility.rbac.auth.util.PasswordDigestUtils#digest 保持一致：
-- SHA-256(明文密码 + 盐值) 的十六进制小写字符串。
-- 首登标识 first_login 置为 1，登录后会被强制要求先修改密码，缩短默认弱口令
-- （5 位数字字母，短于 ChangePasswordRequest 对"新密码"施加的 6 位下限，但改密
-- 校验只约束新密码长度，不约束旧密码，因此可以正常改密）暴露的窗口。
-- 该行是 tab_user 表在全新数据库中插入的第一行，AUTO_INCREMENT 保证其 id 为 1，
-- 因此这里及后续全部种子数据的 create_by/update_by 直接写字面值 '1'（管理员账号
-- 自身也自引用该值），不再依赖运行时 SELECT 或后续 UPDATE 改写。
SET @admin_salt = 'e648f0bb6c2c586063398f07dc2d0e08';
SET @admin_user_id_text := '1';

INSERT INTO `tab_user` (`name`, `code`, `gender`, `status`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('系统管理员', 'admin', 'unknown', 2000, '系统初始化默认管理账号，首次登录后请立即修改密码', @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @admin_user_id := LAST_INSERT_ID();

INSERT INTO `tab_user_password` (`user_id`, `password_digest`, `salt`, `first_login`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES (@admin_user_id, LOWER(SHA2(CONCAT('admin', @admin_salt), 256)), @admin_salt, 1, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ----------------------------------------------------------------------------
-- 字典种子数据
-- ----------------------------------------------------------------------------

-- 预置"任职类型"字典数据，供用户管理模块任职记录的 positionType 字段消费
INSERT INTO `tab_dict_type` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('任职类型', 'position_type', 0, '用户管理模块任职类型字段的数据来源', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '主职', 'primary', 3, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '兼职', 'part_time', 2, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '挂职', 'temporary', 1, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

-- 预置"性别"字典数据，供用户管理模块的 gender 字段消费
INSERT INTO `tab_dict_type` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('性别', 'gender', 0, '用户管理模块性别字段的数据来源', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '未知', 'unknown', 1, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '男', 'male', 2, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '女', 'female', 3, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

-- ----------------------------------------------------------------------------
-- 菜单种子数据（菜单树 + 按钮资源）
-- ----------------------------------------------------------------------------

-- ---- 第一层：侧边栏一级导航分组（resourceType = 1 菜单，parentId = 0） ----

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('身份管理', 'identity', 0, 1, 40, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('应用管理', 'application', 0, 1, 30, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('权限管理', 'permission', 0, 1, 20, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('系统管理', 'system', 0, 1, 10, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('日志管理', 'log', 0, 1, 5, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @identity_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'identity');
SET @application_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'application');
SET @permission_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'permission');
SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');
SET @log_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'log');

-- ---- 第二层：8 个已实现管理页面（resourceType = 1 菜单） ----

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('组织管理', 'OrgManagement:org:view', @identity_id, 1, 30, '组织管理页面访问（含左侧组织树浏览）', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('用户管理', 'UserManagement:user:view', @identity_id, 1, 20, '用户管理页面访问', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('任职管理', 'PositionManagement:position:view', @identity_id, 1, 10, '任职管理页面访问', 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('应用管理', 'AppManagement:app:view', @application_id, 1, 10, '应用管理页面访问', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('角色管理', 'RoleManagement:role:view', @permission_id, 1, 20, '角色管理页面访问', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('权限点管理', 'PermissionManagement:permission:view', @permission_id, 1, 10, '权限点管理页面访问', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('菜单管理', 'MenuManagement:menu:view', @system_id, 1, 20, '菜单/资源管理页面访问', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('字典管理', 'DictManagement:dictType:view', @system_id, 1, 10, '字典管理页面访问（含字典类型列表）', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @org_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'OrgManagement:org:view');
SET @user_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'UserManagement:user:view');
SET @position_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'PositionManagement:position:view');
SET @app_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AppManagement:app:view');
SET @role_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'RoleManagement:role:view');
SET @permission_point_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'PermissionManagement:permission:view');
SET @menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'MenuManagement:menu:view');
SET @dict_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'DictManagement:dictType:view');

-- ---- 第三层：按钮（resourceType = 2 按钮） ----

-- 组织管理（含导入相关按钮：下载模板、批量导入）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增组织', 'OrgManagement:org:add', @org_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看组织详情', 'OrgManagement:org:detail', @org_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('编辑组织', 'OrgManagement:org:edit', @org_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用组织', 'OrgManagement:org:enable', @org_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用组织', 'OrgManagement:org:disable', @org_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除组织', 'OrgManagement:org:delete', @org_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('下载组织导入模板', 'OrgManagement:org:importTemplate', @org_id, 2, 5, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('批量导入组织', 'OrgManagement:org:import', @org_id, 2, 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 用户管理（含重置密码按钮、导入相关按钮：下载模板、批量导入）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增用户', 'UserManagement:user:add', @user_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看用户详情', 'UserManagement:user:detail', @user_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('编辑用户', 'UserManagement:user:edit', @user_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用用户', 'UserManagement:user:enable', @user_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用用户', 'UserManagement:user:disable', @user_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('重置密码', 'UserManagement:user:resetPassword', @user_id, 2, 15, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('删除用户', 'UserManagement:user:delete', @user_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('下载用户导入模板', 'UserManagement:user:importTemplate', @user_id, 2, 5, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('批量导入用户', 'UserManagement:user:import', @user_id, 2, 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 任职管理（含导入相关按钮：下载模板、批量导入）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增任职记录', 'PositionManagement:position:add', @position_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('查看任职详情', 'PositionManagement:position:detail', @position_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('编辑任职记录', 'PositionManagement:position:edit', @position_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('启用任职记录', 'PositionManagement:position:enable', @position_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('停用任职记录', 'PositionManagement:position:disable', @position_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('删除任职记录', 'PositionManagement:position:delete', @position_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('下载任职导入模板', 'PositionManagement:position:importTemplate', @position_id, 2, 5, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('批量导入任职记录', 'PositionManagement:position:import', @position_id, 2, 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

-- 应用管理（含导入相关按钮：下载模板、批量导入；应用配置相关按钮：配置入口、重置
-- SecretKey、修改签名算法、修改同步配置、修改认证管理配置——最终文案"应用配置"）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增应用', 'AppManagement:app:add', @app_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看应用详情', 'AppManagement:app:detail', @app_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('编辑应用', 'AppManagement:app:edit', @app_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用应用', 'AppManagement:app:enable', @app_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用应用', 'AppManagement:app:disable', @app_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除应用', 'AppManagement:app:delete', @app_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('下载应用导入模板', 'AppManagement:app:importTemplate', @app_id, 2, 5, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('批量导入应用', 'AppManagement:app:import', @app_id, 2, 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('应用配置', 'AppManagement:app:config', @app_id, 2, 4, '应用配置页面访问', 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('重置 SecretKey', 'AppManagement:app:config:resetSecret', @app_id, 2, 3, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('修改签名算法', 'AppManagement:app:config:editSignAlgorithm', @app_id, 2, 2, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('修改同步配置', 'AppManagement:app:config:editSync', @app_id, 2, 1, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('修改认证管理配置', 'AppManagement:app:config:editAuth', @app_id, 2, 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

-- 角色管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增角色', 'RoleManagement:role:add', @role_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看角色详情', 'RoleManagement:role:detail', @role_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('编辑角色', 'RoleManagement:role:edit', @role_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用角色', 'RoleManagement:role:enable', @role_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用角色', 'RoleManagement:role:disable', @role_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除角色', 'RoleManagement:role:delete', @role_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 权限点管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增权限点', 'PermissionManagement:permission:add', @permission_point_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('查看权限点详情', 'PermissionManagement:permission:detail', @permission_point_id, 2, 50, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('编辑权限点', 'PermissionManagement:permission:edit', @permission_point_id, 2, 40, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('启用权限点', 'PermissionManagement:permission:enable', @permission_point_id, 2, 30, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('停用权限点', 'PermissionManagement:permission:disable', @permission_point_id, 2, 20, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('删除权限点', 'PermissionManagement:permission:delete', @permission_point_id, 2, 10, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW());

-- 菜单管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增资源', 'MenuManagement:menu:add', @menu_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看资源详情', 'MenuManagement:menu:detail', @menu_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('编辑资源', 'MenuManagement:menu:edit', @menu_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用资源', 'MenuManagement:menu:enable', @menu_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用资源', 'MenuManagement:menu:disable', @menu_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除资源', 'MenuManagement:menu:delete', @menu_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 字典管理（字典类型 + 字典项两组按钮，同挂在字典管理页面节点下）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增字典类型', 'DictManagement:dictType:add', @dict_id, 2, 100, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看字典类型详情', 'DictManagement:dictType:detail', @dict_id, 2, 95, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('编辑字典类型', 'DictManagement:dictType:edit', @dict_id, 2, 90, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用字典类型', 'DictManagement:dictType:enable', @dict_id, 2, 80, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('停用字典类型', 'DictManagement:dictType:disable', @dict_id, 2, 70, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('删除字典类型', 'DictManagement:dictType:delete', @dict_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('新增字典项', 'DictManagement:dictItem:add', @dict_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看字典项详情', 'DictManagement:dictItem:detail', @dict_id, 2, 45, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('编辑字典项', 'DictManagement:dictItem:edit', @dict_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用字典项', 'DictManagement:dictItem:enable', @dict_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用字典项', 'DictManagement:dictItem:disable', @dict_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('删除字典项', 'DictManagement:dictItem:delete', @dict_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 管理员管理（挂在 permission 一级分组下，排在角色管理、权限点管理之后）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('管理员管理', 'AdminManagement:admin:view', @permission_id, 1, 5, '管理员管理页面访问', 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW());

SET @admin_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AdminManagement:admin:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增管理员', 'AdminManagement:admin:add', @admin_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看管理员详情', 'AdminManagement:admin:detail', @admin_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('编辑管理员', 'AdminManagement:admin:edit', @admin_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用管理员', 'AdminManagement:admin:enable', @admin_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用管理员', 'AdminManagement:admin:disable', @admin_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除管理员', 'AdminManagement:admin:delete', @admin_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 应用访问授权（挂在 permission 一级分组下，排在权限管理分组现有子菜单的最后）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('应用访问授权', 'AppAccessManagement:appAccess:view', @permission_id, 1, 1,
        '应用访问授权页面访问（含最终生效权限查询板块的只读查询）', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @app_access_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AppAccessManagement:appAccess:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增策略规则', 'AppAccessManagement:policy:add', @app_access_menu_id, 2, 80, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('编辑策略规则', 'AppAccessManagement:policy:edit', @app_access_menu_id, 2, 70, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('启用策略规则', 'AppAccessManagement:policy:enable', @app_access_menu_id, 2, 60, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('停用策略规则', 'AppAccessManagement:policy:disable', @app_access_menu_id, 2, 50, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('执行策略规则', 'AppAccessManagement:policy:execute', @app_access_menu_id, 2, 40, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('删除策略规则', 'AppAccessManagement:policy:delete', @app_access_menu_id, 2, 30, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('新增/编辑人工例外', 'AppAccessManagement:override:add', @app_access_menu_id, 2, 20, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除人工例外', 'AppAccessManagement:override:delete', @app_access_menu_id, 2, 10, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 操作日志、登录日志（挂在"日志管理"一级分组下，只读，没有按钮资源）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('操作日志', 'OperationLogManagement:log:view', @log_id, 1, 5, '操作日志管理页面访问', 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('登录日志', 'LoginLogManagement:loginLog:view', @log_id, 1, 4, '登录日志管理页面访问', 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW());

-- 字典类型、字典项"详情"按钮已直接并入上面字典管理的批量 INSERT 中，此处不再重复

-- 元数据配置（挂在 system 一级分组下，只支持编辑/详情，不提供新增/删除入口）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('元数据配置', 'MetadataFieldManagement:metadataField:view', @system_id, 1, 5, '元数据字段配置页面访问', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @metadata_field_id := (SELECT `id`
                            FROM `tab_menu`
                            WHERE `code` = 'MetadataFieldManagement:metadataField:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('查看元数据字段详情', 'MetadataFieldManagement:metadataField:detail', @metadata_field_id, 2, 40, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('编辑元数据字段', 'MetadataFieldManagement:metadataField:edit', @metadata_field_id, 2, 30, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用元数据字段', 'MetadataFieldManagement:metadataField:enable', @metadata_field_id, 2, 20, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用元数据字段', 'MetadataFieldManagement:metadataField:disable', @metadata_field_id, 2, 10, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 表单管理（挂在 system 一级分组下，排在元数据配置之后，完整支持新增/编辑/启停用/删除/详情）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('表单管理', 'FormFieldManagement:formField:view', @system_id, 1, 1, '表单字段定义管理页面访问', 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW());

SET @form_field_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'FormFieldManagement:formField:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增表单字段', 'FormFieldManagement:formField:add', @form_field_id, 2, 60, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('查看表单字段详情', 'FormFieldManagement:formField:detail', @form_field_id, 2, 50, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('编辑表单字段', 'FormFieldManagement:formField:edit', @form_field_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('启用表单字段', 'FormFieldManagement:formField:enable', @form_field_id, 2, 30, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('停用表单字段', 'FormFieldManagement:formField:disable', @form_field_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('删除表单字段', 'FormFieldManagement:formField:delete', @form_field_id, 2, 10, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

-- 上游数据管理（挂在 identity 一级分组下，排在表单管理之后新增）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('上游数据管理', 'UpstreamManagement:source:view', @identity_id, 1, 0, '上游数据管理页面访问', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @upstream_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'UpstreamManagement:source:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增上游数据源', 'UpstreamManagement:source:add', @upstream_id, 2, 80, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('编辑上游数据源基础信息', 'UpstreamManagement:source:edit', @upstream_id, 2, 70,
        '编辑名称、同步方式，含独立配置页"基础信息" tab 的保存', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除上游数据源', 'UpstreamManagement:source:delete', @upstream_id, 2, 60,
        '级联删除数据域配置、字段映射、同步执行记录', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用上游数据源', 'UpstreamManagement:source:enable', @upstream_id, 2, 50, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('停用上游数据源', 'UpstreamManagement:source:disable', @upstream_id, 2, 40, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('进入数据源配置页', 'UpstreamManagement:source:config', @upstream_id, 2, 30,
        '进入 /identity/upstream/:id/config 独立配置页', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('修改数据源配置', 'UpstreamManagement:source:config:edit', @upstream_id, 2, 20,
        '配置页内连接配置、调度配置、数据范围（数据域启用+取数来源、字段映射）的全部保存动作', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('立即同步一次', 'UpstreamManagement:source:manualSync', @upstream_id, 2, 10,
        '手动触发一次同步，不等定时调度到点', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ----------------------------------------------------------------------------
-- 权限点种子数据（118 条，按 权限资源.txt 模块分段插入）
-- ----------------------------------------------------------------------------
-- 组织/用户/任职/应用/角色/权限点/管理员/菜单/字典/元数据配置/表单管理/操作日志
-- 共 95 条源自 权限资源.txt（一次性脚本 gen_permission_seed.py 解析生成，此前版本的
-- 注释误记为 94 条），登录日志 1 条、应用配置（页面访问 + 重置 SecretKey + 修改签名
-- 算法 + 修改同步配置）4 条、上游数据管理（页面访问 + 新增 + 编辑基础信息 + 删除 +
-- 启用 + 停用 + 进入配置页 + 修改数据源配置 + 立即同步一次）9 条、应用访问授权
-- （页面访问 + 策略规则新增/编辑/启用/停用/执行/删除 + 人工例外新增编辑/删除）9 条
-- 均为后续新增能力追加，合计 118 条。

-- OrgManagement（组织管理，/identity/orgs）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('组织管理页面访问（含左侧组织树浏览）', 'OrgManagement:org:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增组织', 'OrgManagement:org:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑组织', 'OrgManagement:org:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看组织详情', 'OrgManagement:org:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用组织', 'OrgManagement:org:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用组织', 'OrgManagement:org:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除组织', 'OrgManagement:org:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('下载组织导入模板', 'OrgManagement:org:importTemplate', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('批量导入组织', 'OrgManagement:org:import', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- UserManagement（用户管理，/identity/users）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('用户管理页面访问', 'UserManagement:user:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增用户', 'UserManagement:user:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑用户', 'UserManagement:user:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看用户详情', 'UserManagement:user:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用用户', 'UserManagement:user:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用用户', 'UserManagement:user:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除用户', 'UserManagement:user:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('下载用户导入模板', 'UserManagement:user:importTemplate', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('批量导入用户', 'UserManagement:user:import', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('重置用户密码', 'UserManagement:user:resetPassword', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- PositionManagement（任职管理，/identity/positions）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('任职管理页面访问', 'PositionManagement:position:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增任职记录', 'PositionManagement:position:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑任职记录', 'PositionManagement:position:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看任职详情', 'PositionManagement:position:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用任职记录', 'PositionManagement:position:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用任职记录', 'PositionManagement:position:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除任职记录', 'PositionManagement:position:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('下载任职导入模板', 'PositionManagement:position:importTemplate', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('批量导入任职记录', 'PositionManagement:position:import', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- AppManagement（应用管理，/application/list；含应用配置能力：AppId/AccessKey/SecretKey、
-- 签名算法、同步配置、认证管理配置）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('应用管理页面访问', 'AppManagement:app:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增应用', 'AppManagement:app:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑应用', 'AppManagement:app:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看应用详情', 'AppManagement:app:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用应用', 'AppManagement:app:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用应用', 'AppManagement:app:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除应用', 'AppManagement:app:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('下载应用导入模板', 'AppManagement:app:importTemplate', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('批量导入应用', 'AppManagement:app:import', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('应用配置页面访问', 'AppManagement:app:config', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('重置 SecretKey', 'AppManagement:app:config:resetSecret', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('修改签名算法', 'AppManagement:app:config:editSignAlgorithm', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('修改同步配置', 'AppManagement:app:config:editSync', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('修改认证管理配置', 'AppManagement:app:config:editAuth', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- RoleManagement（角色管理，/permission/roles）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('角色管理页面访问', 'RoleManagement:role:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增角色', 'RoleManagement:role:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑角色', 'RoleManagement:role:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看角色详情', 'RoleManagement:role:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用角色', 'RoleManagement:role:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用角色', 'RoleManagement:role:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除角色', 'RoleManagement:role:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- PermissionManagement（权限点管理，/permission/points）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('权限点管理页面访问', 'PermissionManagement:permission:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增权限点', 'PermissionManagement:permission:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑权限点', 'PermissionManagement:permission:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看权限点详情', 'PermissionManagement:permission:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用权限点', 'PermissionManagement:permission:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用权限点', 'PermissionManagement:permission:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除权限点', 'PermissionManagement:permission:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- AdminManagement（管理员管理，/permission/admins）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('管理员管理页面访问', 'AdminManagement:admin:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增管理员', 'AdminManagement:admin:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑管理员', 'AdminManagement:admin:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看管理员详情', 'AdminManagement:admin:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用管理员', 'AdminManagement:admin:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用管理员', 'AdminManagement:admin:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除管理员', 'AdminManagement:admin:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- MenuManagement（菜单管理，/system/menus）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('菜单/资源管理页面访问', 'MenuManagement:menu:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增资源', 'MenuManagement:menu:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑资源', 'MenuManagement:menu:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看资源详情', 'MenuManagement:menu:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用资源', 'MenuManagement:menu:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用资源', 'MenuManagement:menu:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除资源', 'MenuManagement:menu:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- DictManagement（字典管理，/system/dicts）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('字典管理页面访问（含字典类型列表）', 'DictManagement:dictType:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增字典类型', 'DictManagement:dictType:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑字典类型', 'DictManagement:dictType:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看字典类型详情', 'DictManagement:dictType:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用字典类型', 'DictManagement:dictType:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用字典类型', 'DictManagement:dictType:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除字典类型', 'DictManagement:dictType:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增字典项（需先选中左侧字典类型）', 'DictManagement:dictItem:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑字典项', 'DictManagement:dictItem:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看字典项详情', 'DictManagement:dictItem:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用字典项', 'DictManagement:dictItem:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用字典项', 'DictManagement:dictItem:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除字典项', 'DictManagement:dictItem:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- MetadataFieldManagement（元数据配置，/system/metadata-fields）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('元数据配置页面访问', 'MetadataFieldManagement:metadataField:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑元数据字段（仅字段名称可改）', 'MetadataFieldManagement:metadataField:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看元数据字段详情', 'MetadataFieldManagement:metadataField:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用元数据字段', 'MetadataFieldManagement:metadataField:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用元数据字段', 'MetadataFieldManagement:metadataField:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- FormFieldManagement（表单管理，/system/form-fields）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('表单管理页面访问（含"字段定义""导入模板配置"两个 tab）', 'FormFieldManagement:formField:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增表单字段定义（从元数据配置目录选择可用字段绑定）', 'FormFieldManagement:formField:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑表单字段定义', 'FormFieldManagement:formField:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('查看表单字段定义详情', 'FormFieldManagement:formField:detail', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用表单字段定义', 'FormFieldManagement:formField:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用表单字段定义', 'FormFieldManagement:formField:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除表单字段定义', 'FormFieldManagement:formField:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增导入字段配置（从当前业务对象类型下启用的表单字段定义中选择关联字段）', 'FormFieldManagement:importFieldConfig:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑导入字段配置', 'FormFieldManagement:importFieldConfig:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除导入字段配置', 'FormFieldManagement:importFieldConfig:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- OperationLogManagement（操作日志管理，/log/operations）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('操作日志管理页面访问', 'OperationLogManagement:log:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- LoginLogManagement（登录日志管理，/log/logins）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('登录日志管理页面访问', 'LoginLogManagement:loginLog:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- UpstreamManagement（上游数据管理，/identity/upstream）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('上游数据管理页面访问', 'UpstreamManagement:source:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增上游数据源', 'UpstreamManagement:source:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑上游数据源基础信息', 'UpstreamManagement:source:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除上游数据源', 'UpstreamManagement:source:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用上游数据源', 'UpstreamManagement:source:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用上游数据源', 'UpstreamManagement:source:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('进入数据源配置页', 'UpstreamManagement:source:config', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('修改数据源配置', 'UpstreamManagement:source:config:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('立即同步一次', 'UpstreamManagement:source:manualSync', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- AppAccessManagement（应用访问授权，/permission/app-access）
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('应用访问授权页面访问', 'AppAccessManagement:appAccess:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增策略规则', 'AppAccessManagement:policy:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('编辑策略规则', 'AppAccessManagement:policy:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('启用策略规则', 'AppAccessManagement:policy:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('停用策略规则', 'AppAccessManagement:policy:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('执行策略规则', 'AppAccessManagement:policy:execute', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除策略规则', 'AppAccessManagement:policy:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('新增/编辑人工例外', 'AppAccessManagement:override:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('删除人工例外', 'AppAccessManagement:override:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ----------------------------------------------------------------------------
-- 超级管理员角色，关联全部种子权限点（含上面新增的 UpstreamManagement/AppAccessManagement
-- 系列权限点，因为本 INSERT...SELECT 在其之后执行，天然覆盖，不需要再单独补一次授权）
-- ----------------------------------------------------------------------------

INSERT INTO `tab_role` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('超级管理员', 'SUPER_ADMIN', 0, '系统初始化角色，拥有全部权限点', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`;

-- ----------------------------------------------------------------------------
-- 元数据字段配置种子数据
-- ----------------------------------------------------------------------------
-- 覆盖组织（ORG）、人员（USER）、任职（POSITION）、应用（APP）、角色（ROLE）五类
-- 业务对象各自"可开放配置的原有列"与全部 ext1~ext10 扩展列（ROLE 无 ext 扩展列）。
-- 已有专用交互控件的字段（parentId、orgId、userId、ownerId、status 等）不出现在此
-- 目录中，继续保持硬编码渲染；USER 的 gender 已改造为普通字典下拉字段，出现在此
-- 目录中；positionType 虽然绑定了专用的字典类型（position_type），但和其余表单
-- 字段定义一样纳入本目录、动态渲染，并且是承重字段（见 LockedFormFields），不属于
-- "硬编码渲染"这一类。ROLE 不加入 FormFieldBizType（角色管理页面不接入动态表单
-- 渲染管线），仅服务于 tab_app_sync_field_mapping.metadata_field_id 的可选来源。

-- ---- 组织（ORG，对应 tab_org，含 parent_code） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('ORG', 'tab_org', 'name', 'name', 'VARCHAR(64)', '组织名称', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'code', 'code', 'VARCHAR(64)', '组织编码', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'parent_code', 'parentCode', 'VARCHAR(64)', '上级组织编码', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'show_order', 'showOrder', 'INT', '显示序号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', 'tab_org', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 人员（USER，对应 tab_user） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('USER', 'tab_user', 'name', 'name', 'VARCHAR(64)', '用户姓名', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'code', 'code', 'VARCHAR(64)', '用户编号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'mobile', 'mobile', 'VARCHAR(20)', '手机号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'id_card', 'idCard', 'VARCHAR(18)', '身份证号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'gender', 'gender', 'VARCHAR(64)', '性别', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'show_order', 'showOrder', 'INT', '显示序号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', 'tab_user', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 任职（POSITION，对应 tab_user_position） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('POSITION', 'tab_user_position', 'position_type', 'positionType', 'VARCHAR(64)', '任职类型', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'position_address', 'positionAddress', 'VARCHAR(255)', '任职地址', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'position_phone', 'positionPhone', 'VARCHAR(20)', '任职电话', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'show_order', 'showOrder', 'INT', '显示序号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', 'tab_user_position', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 应用（APP，对应 tab_app） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('APP', 'tab_app', 'name', 'name', 'VARCHAR(64)', '应用名称', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'code', 'code', 'VARCHAR(64)', '应用编码', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'show_order', 'showOrder', 'INT', '显示序号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', 'tab_app', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 角色（ROLE，对应 tab_role，无 ext 扩展列） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`,
     `create_time`, `update_by`, `update_time`)
VALUES ('ROLE', 'tab_role', 'name', 'name', 'VARCHAR(64)', '角色名称', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ROLE', 'tab_role', 'code', 'code', 'VARCHAR(64)', '角色编码', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ROLE', 'tab_role', 'show_order', 'showOrder', 'INT', '显示序号', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ROLE', 'tab_role', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ----------------------------------------------------------------------------
-- 表单字段定义种子数据
-- ----------------------------------------------------------------------------
-- 为组织/人员/任职/应用四类业务对象各自"可开放配置的原有列"（不含 ext1~ext10）
-- 预置启用状态的字段定义，绑定上面写入的对应元数据字段，使这些页面无需管理员手动
-- 配置即可保持现有展示；USER 的性别字段绑定 gender 字典类型（字典下拉控件）。
-- ext1~ext10 对应的元数据字段默认不预置字段定义，保持"零配置不出现，管理员按需
-- 绑定"的状态。承重字段（组织/用户/应用各自的 name、code）的锁定保护不在此处理，
-- 由 cn.nihility.rbac.formfield.constant.LockedFormFields 白名单在运行时计算。

-- ---- 组织（ORG） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'name'),
        '组织名称', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'code'),
        '组织编码', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 3, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 5, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 人员（USER） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'name'),
        '用户姓名', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'code'),
        '用户编号', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 3, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'mobile'),
        '手机号', 'mobile', 1, NULL, 1, 0, 1, 1, 1, 1, NULL, NULL, 5, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'id_card'),
        '身份证号', 'idCard', 1, NULL, 1, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'gender'),
        '性别', 'gender', 3, 'gender', 0, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 9, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 11, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 任职（POSITION） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('POSITION', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user_position' AND `column_name` = 'position_type'),
        '任职类型', 'positionType', 3, 'position_type', 0, 1, 1, 1, 1,
        1, NULL, NULL, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'position_address'), '任职地址', 'positionAddress', 1, NULL, 0, 0, 1, 1, 1,
        1, NULL, NULL, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'position_phone'), '任职电话', 'positionPhone', 1, NULL, 0, 0, 1, 1, 1, 1,
        NULL, NULL, 2, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'show_order'), '显示序号', 'showOrder', 2, NULL, 0, 0, 1, 1, 1, 1, NULL,
        NULL, 3, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'remark'), '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 4,
        2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 应用（APP） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'name'),
        '应用名称', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'code'),
        '应用编码', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 3, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 5, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ----------------------------------------------------------------------------
-- Excel 导入字段配置种子数据
-- ----------------------------------------------------------------------------

-- POSITION 的表单字段定义清单里没有、也不应该有 userId/orgId（它们是选择器，不是
-- 展示字段），但 Excel 导入必须能通过人可读的编码定位到具体的人员和组织。这里预置
-- 两条不可删除、不可取消必填的固定导入配置行：__userCode（表头"人员编号"，导入时
-- 匹配 tab_user.code 得到 userId）与 __orgCode（表头"组织编码"，导入时匹配
-- tab_org.code 得到 orgId），form_field_definition_id 为 NULL。保护逻辑不落库，
-- 由 cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs 白名单在
-- 更新/删除时计算得出并拒绝相应请求。
INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('POSITION', NULL, '__userCode', '人员编号', 1, 1, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('POSITION', NULL, '__orgCode', '组织编码', 1, 1, 2, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- APP 的 ownerId/orgId 同理是选择器而非展示字段，比照 POSITION 预置两条固定标识列。
INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('APP', NULL, '__ownerCode', '负责人编码', 1, 1, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('APP', NULL, '__orgCode', '组织编码', 1, 1, 2, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ORG 的 parentId 同理是选择器（树形选择器）而非展示字段。__parentCode（表头"上级
-- 组织编码"，导入时匹配 tab_org.code 得到 parentId）是必填（is_required=1）：每一
-- 行都必须显式填写上级组织编码，若该组织本身是顶级组织，则填写字面值 "0"，而不是
-- 留空。
INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('ORG', NULL, '__parentCode', '上级组织编码', 0, 1, 1, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ----------------------------------------------------------------------------
-- 默认账号 admin 引导授权：赋予管理员身份并关联超级管理员角色
-- ----------------------------------------------------------------------------

INSERT INTO `tab_admin` (`name`, `code`, `user_id`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                          `update_by`, `update_time`)
VALUES ('系统管理员', 'admin', @admin_user_id, 0, '系统初始化默认管理员身份', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @admin_admin_id := (SELECT `id` FROM `tab_admin` WHERE `code` = 'admin');

INSERT INTO `tab_admin_role` (`admin_id`, `role_id`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES (@admin_admin_id, @super_admin_role_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());
