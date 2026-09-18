-- ----------------------------------------------------------------------------
-- chat-end-to-end-encryption change：单聊端到端加密（design.md Decision 4/6）。
-- 1) tab_chat_message 新增 sender_identity_key_fingerprint 列，落库发送方身份公钥指纹
--    快照，允许为空以兼容历史明文消息记录。
-- 2) 新增 tab_chat_user_key 密钥目录表：user_id 唯一，存放用户 X25519 身份公钥
--    （Base64）与后端留痕用的公钥指纹，不做物理外键（沿用项目既有约定）。
-- MySQL 5.7 兼容：不使用窗口函数/CTE/ADD COLUMN IF NOT EXISTS 等 8.0+ 语法。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_chat_message`
    ADD COLUMN `sender_identity_key_fingerprint` VARCHAR(128) NULL
        COMMENT '发送方身份公钥指纹快照（单聊端到端加密消息使用，历史明文消息为空）' AFTER `filtered`;

CREATE TABLE IF NOT EXISTS `tab_chat_user_key`
(
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`             BIGINT      NOT NULL COMMENT '用户 id，关联 tab_user.id，不建物理外键',
    `identity_public_key` VARCHAR(64) NOT NULL COMMENT 'X25519 身份公钥（Base64 编码，定长）',
    `key_fingerprint`     VARCHAR(128) NOT NULL COMMENT '身份公钥指纹，服务端留痕/审计用，非安全校验唯一依据',
    `create_by`           VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`           VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_chat_user_key_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '聊天单聊端到端加密身份公钥目录表，每个用户至多一条记录';
