-- ----------------------------------------------------------------------------
-- chat-gateway-core change：新增聊天能力第一阶段的持久化表（单节点 Netty 网关 +
-- 单聊/群聊 + 敏感词过滤，design.md Decision 7/8）：
-- 1. tab_chat_conversation：会话（单聊/群聊），next_seq 字段配合事务内
--    SELECT ... FOR UPDATE 生成会话级严格递增消息序号（不使用 MySQL 8.0+ 窗口函数）；
-- 2. tab_chat_conversation_member：会话成员/群成员，单聊固定两条成员记录；
-- 3. tab_chat_message：消息记录，msg_id 唯一索引作为跨重启幂等去重的权威依据；
-- 4. tab_chat_message_offline：离线消息队列，用户重新上线后按序补偿推送；
-- 5. tab_chat_sensitive_word：敏感词库，服务启动时加载进内存构建 AC 自动机，
--    管理员增删改后触发内存自动机重建。
-- 同时登记"聊天"一级导航分组 + Chat:conversation:view/create/manageMember 三个权限点、
-- "系统管理"分组下敏感词管理页面 + SensitiveWordManagement:sensitiveWord:view/add/
-- delete/enable/disable 五个权限点（tab_menu 与 tab_permission 均登记，写法与既有权限点
-- 登记方式一致），并为 SUPER_ADMIN 角色补授。
-- 字段命名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：conversation_type/next_seq/
-- msg_id/conversation_seq/sender_id/msg_type/content/filtered/send_time/message_id/
-- receiver_id/delivered/word 均非保留字（用 conversation 而非 group 表达群聊，避开 group
-- 关键字）。SQL 全部使用 MySQL 5.7 兼容写法，不使用窗口函数/CTE 等 8.0+ 专属特性。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_chat_conversation`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `conversation_type` INT         NOT NULL COMMENT '会话类型：1=单聊，2=群聊',
    `name`              VARCHAR(128) NULL COMMENT '群聊名称，单聊为空（前端按对方展示名动态显示）',
    `next_seq`          BIGINT      NOT NULL DEFAULT 1 COMMENT '下一个可分配的会话内消息序号，事务内 SELECT ... FOR UPDATE 取号后自增',
    `status`            INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=正常，3000=已解散（本阶段未提供解散入口，预留）',
    `create_by`         VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_chat_conversation_type` (`conversation_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '聊天会话表（单聊/群聊）';

CREATE TABLE IF NOT EXISTS `tab_chat_conversation_member`
(
    `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `conversation_id` BIGINT   NOT NULL COMMENT '所属会话 id，关联 tab_chat_conversation.id',
    `user_id`         BIGINT   NOT NULL COMMENT '成员用户 id，关联 tab_user.id',
    `role`            INT      NOT NULL DEFAULT 2 COMMENT '成员角色：1=群主，2=普通成员（单聊两条记录均为 2）',
    `joined_time`     DATETIME NOT NULL COMMENT '加入时间',
    `status`          INT      NOT NULL DEFAULT 2000 COMMENT '状态：2000=在会话中，3000=已退出/被移出',
    `create_by`       VARCHAR(64)       DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)       DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_chat_conversation_member_conv_user` (`conversation_id`, `user_id`),
    KEY `idx_tab_chat_conversation_member_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '聊天会话成员表（含群成员）';

CREATE TABLE IF NOT EXISTS `tab_chat_message`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `msg_id`           VARCHAR(64) NOT NULL COMMENT '客户端生成的消息幂等 id，唯一索引，跨重启去重的权威依据',
    `conversation_id`  BIGINT      NOT NULL COMMENT '所属会话 id，关联 tab_chat_conversation.id',
    `conversation_seq` BIGINT      NOT NULL COMMENT '会话内严格递增、不重复的消息序号',
    `sender_id`        BIGINT      NOT NULL COMMENT '发送者用户 id，关联 tab_user.id',
    `msg_type`         INT         NOT NULL DEFAULT 1 COMMENT '消息内容类型：1=文本（本阶段仅支持文本，其余类型预留占位）',
    `content`          TEXT        NOT NULL COMMENT '消息内容（敏感词过滤/替换后落库，本阶段服务端可见明文，不做信封加密）',
    `filtered`         TINYINT     NOT NULL DEFAULT 0 COMMENT '是否命中过敏感词：0=否，1=是',
    `send_time`        DATETIME    NOT NULL COMMENT '发送时间',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_chat_message_msg_id` (`msg_id`),
    KEY `idx_tab_chat_message_conv_seq` (`conversation_id`, `conversation_seq`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '聊天消息记录表';

CREATE TABLE IF NOT EXISTS `tab_chat_message_offline`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `message_id`   BIGINT   NOT NULL COMMENT '关联 tab_chat_message.id',
    `receiver_id`  BIGINT   NOT NULL COMMENT '接收者用户 id，关联 tab_user.id',
    `delivered`    TINYINT  NOT NULL DEFAULT 0 COMMENT '是否已补偿推送：0=否，1=是',
    `create_by`    VARCHAR(64)       DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)       DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_chat_message_offline_receiver` (`receiver_id`, `delivered`),
    KEY `idx_tab_chat_message_offline_message_id` (`message_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '离线消息队列表，用户重新上线并完成认证后按序补偿推送';

CREATE TABLE IF NOT EXISTS `tab_chat_sensitive_word`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `word`         VARCHAR(64) NOT NULL COMMENT '敏感词词条',
    `status`       INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用（删除为物理删除，不使用逻辑删除状态）',
    `create_by`    VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_chat_sensitive_word_word` (`word`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '聊天敏感词库表，服务启动时加载进内存构建 AC 自动机，变更后触发内存重建';

-- 种子敏感词数据（最小可用示例集，生产环境请通过后台管理接口按需维护）。
INSERT INTO `tab_chat_sensitive_word` (`word`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('赌博', 2000, '1', NOW(), '1', NOW()),
       ('毒品', 2000, '1', NOW(), '1', NOW()),
       ('诈骗', 2000, '1', NOW(), '1', NOW()),
       ('枪支', 2000, '1', NOW(), '1', NOW()),
       ('色情', 2000, '1', NOW(), '1', NOW());

-- ----------------------------------------------------------------------------
-- 权限点登记：新增"聊天"侧边栏一级导航分组 + 聊天页面访问/创建群聊/群成员管理三个
-- 权限点；"系统管理"分组下新增"敏感词管理"页面 + 分页查询/新增/删除/启用/停用五个权限点。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('聊天', 'chat', 0, 1, 45, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @chat_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'chat');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('聊天', 'Chat:conversation:view', @chat_group_id, 1, 10,
        '聊天页面访问（会话列表 + 消息收发 + 历史消息查询）', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @chat_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'Chat:conversation:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('创建群聊', 'Chat:conversation:create', @chat_view_id, 2, 20, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('群成员管理', 'Chat:conversation:manageMember', @chat_view_id, 2, 10,
        '添加群成员、移除成员、退出群聊', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('敏感词管理', 'SensitiveWordManagement:sensitiveWord:view', @system_id, 1, 0,
        '聊天敏感词库分页查询', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @sensitive_word_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'SensitiveWordManagement:sensitiveWord:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增敏感词', 'SensitiveWordManagement:sensitiveWord:add', @sensitive_word_view_id, 2, 40, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除敏感词', 'SensitiveWordManagement:sensitiveWord:delete', @sensitive_word_view_id, 2, 30, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('启用敏感词', 'SensitiveWordManagement:sensitiveWord:enable', @sensitive_word_view_id, 2, 20, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('停用敏感词', 'SensitiveWordManagement:sensitiveWord:disable', @sensitive_word_view_id, 2, 10, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
VALUES ('聊天页面访问', 'Chat:conversation:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('创建群聊', 'Chat:conversation:create', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('群成员管理', 'Chat:conversation:manageMember', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
        NOW()),
       ('敏感词管理页面访问', 'SensitiveWordManagement:sensitiveWord:view', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('新增敏感词', 'SensitiveWordManagement:sensitiveWord:add', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('删除敏感词', 'SensitiveWordManagement:sensitiveWord:delete', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('启用敏感词', 'SensitiveWordManagement:sensitiveWord:enable', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('停用敏感词', 'SensitiveWordManagement:sensitiveWord:disable', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

-- 超级管理员角色补授本次新增权限点（V1 基线脚本里的 SUPER_ADMIN 授权在本脚本执行前已完成，
-- 这里需要单独补一条，否则超级管理员账号也看不到聊天/敏感词管理相关菜单/按钮）。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`
WHERE `code` IN ('Chat:conversation:view', 'Chat:conversation:create', 'Chat:conversation:manageMember',
                 'SensitiveWordManagement:sensitiveWord:view', 'SensitiveWordManagement:sensitiveWord:add',
                 'SensitiveWordManagement:sensitiveWord:delete', 'SensitiveWordManagement:sensitiveWord:enable',
                 'SensitiveWordManagement:sensitiveWord:disable')
  AND @super_admin_role_id IS NOT NULL;
