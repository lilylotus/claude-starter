-- ----------------------------------------------------------------------------
-- add-master-data-approval-workflow change：组织/用户/任职/应用四类主数据的
-- 新增/编辑/启用/停用/删除接入统一审批流程。本脚本：
-- 1. 新增 tab_approval_request（审批申请统一数据模型，design.md Decision 1）；
-- 2. 新增 tab_approval_switch（按 bizType 独立配置的审批开关，默认全部开启，
--    design.md Decision 9），并预置 ORG/USER/POSITION/APP 四条记录；
-- 3. 登记 ApprovalManagement:request:view/request:approve/switch:view/switch:edit
--    四个权限点（tab_menu 按钮/页面资源 + tab_permission 权限点两张表都要登记，
--    写法与既有 xxx:import/xxx:export 权限点一致），并为 SUPER_ADMIN 角色补授。
-- 字段命名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：biz_type/operation_type/
-- target_id/result_target_id/request_payload/status/approver_id/approve_time/opinion/
-- flowable_process_instance_id/flowable_task_id/enabled 均非保留字。SQL 全部使用
-- MySQL 5.7 兼容写法，不使用窗口函数/CTE 等 8.0+ 专属特性。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_approval_request` (
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`                      VARCHAR(16)  NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP',
    `operation_type`                VARCHAR(16)  NOT NULL COMMENT '操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE',
    `target_id`                     BIGINT       NULL COMMENT '目标记录 id，CREATE 申请为空，其余操作类型必填',
    `result_target_id`              BIGINT       NULL COMMENT '审批通过后实际生效的记录 id，仅 CREATE 申请审批通过后回填',
    `request_payload`               TEXT         NULL COMMENT 'JSON 序列化的原始请求体，CREATE/UPDATE 保存完整请求体，ENABLE/DISABLE/DELETE 为空',
    `status`                        INT          NOT NULL DEFAULT 1000 COMMENT '申请状态：1000=待审批，2000=已通过，3000=已拒绝，4000=已撤回',
    `approver_id`                   VARCHAR(64)  NULL COMMENT '审批人用户 id 文本，处理后回填',
    `approve_time`                  DATETIME     NULL COMMENT '审批处理时间，处理后回填',
    `opinion`                       VARCHAR(500) NULL COMMENT '审批意见，拒绝时必填，通过时可选',
    `flowable_process_instance_id`  VARCHAR(64)  NULL COMMENT '关联的 Flowable 流程实例 id',
    `flowable_task_id`              VARCHAR(64)  NULL COMMENT '关联的 Flowable 用户任务 id',
    `create_by`                     VARCHAR(64)           DEFAULT NULL COMMENT '创建人，即提交人',
    `create_time`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，即提交时间',
    `update_by`                     VARCHAR(64)           DEFAULT NULL COMMENT '更新人，即最后一次处理人',
    `update_time`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_approval_request_biz_type` (`biz_type`),
    KEY `idx_tab_approval_request_status` (`status`),
    KEY `idx_tab_approval_request_target` (`biz_type`, `target_id`),
    KEY `idx_tab_approval_request_create_by` (`create_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '主数据变更审批申请表，统一承载组织/用户/任职/应用四类对象 x 五种操作的审批申请';

CREATE TABLE IF NOT EXISTS `tab_approval_switch` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`    VARCHAR(16) NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP，唯一',
    `enabled`     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否开启审批：1=开启，0=关闭（关闭后对应 bizType 的写接口直接生效）',
    `create_by`   VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_approval_switch_biz_type` (`biz_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '主数据审批开关表，按 bizType 各维护一条记录，默认全部开启';

-- 四个 bizType 默认全部开启审批（design.md Decision 9）。
INSERT INTO `tab_approval_switch` (`biz_type`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('ORG', 1, '1', NOW(), '1', NOW()),
       ('USER', 1, '1', NOW(), '1', NOW()),
       ('POSITION', 1, '1', NOW(), '1', NOW()),
       ('APP', 1, '1', NOW(), '1', NOW());

-- ----------------------------------------------------------------------------
-- 权限点登记：新增"审批管理"侧边栏一级导航分组 + 我的申请/待我审批/审批设置三个
-- 二级页面菜单（resourceType = 1）+ 四个权限点（tab_menu 与 tab_permission 均登记，
-- 与既有权限点登记方式一致）。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('审批管理', 'approval', 0, 1, 25, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @approval_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'approval');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('我的申请', 'ApprovalManagement:request:view', @approval_group_id, 1, 20,
        '查看我的申请/待我审批页面访问', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('审批设置', 'ApprovalManagement:switch:view', @approval_group_id, 1, 10,
        '查看审批开关页面访问', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @approval_request_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'ApprovalManagement:request:view');
SET @approval_switch_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'ApprovalManagement:switch:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('审批通过/拒绝', 'ApprovalManagement:request:approve', @approval_request_view_id, 2, 10,
        '审批通过/拒绝待审批申请，同时门控“待我审批”页面的可见性', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('修改审批开关', 'ApprovalManagement:switch:edit', @approval_switch_view_id, 2, 10,
        NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`,
                              `update_time`)
VALUES ('查看我的申请/待我审批', 'ApprovalManagement:request:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('审批通过/拒绝', 'ApprovalManagement:request:approve', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('查看审批开关', 'ApprovalManagement:switch:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('修改审批开关', 'ApprovalManagement:switch:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 超级管理员角色补授本次新增权限点（初始化脚本 V1 里的 SUPER_ADMIN 授权在本脚本执行前
-- 已完成，这里需要单独补一条，否则超级管理员账号也看不到审批相关菜单/按钮）。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`
WHERE `code` IN ('ApprovalManagement:request:view', 'ApprovalManagement:request:approve',
                 'ApprovalManagement:switch:view', 'ApprovalManagement:switch:edit')
  AND @super_admin_role_id IS NOT NULL;
