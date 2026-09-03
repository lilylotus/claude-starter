-- 通用多级审批引擎建表（workflow-approval-engine change design.md Decision 3）。新增 8 张
-- `tab_wf_` 前缀表：流程模型、流程定义（不可变发布版本快照）、节点审批人规则、流程实例、
-- 审批任务、审批任务候选人明细、审批轨迹、操作幂等记录；`tab_wf_process_model` 属于后续
-- 设计器批次范围，本次仅建表结构预留，不写业务代码使用。所有表均含创建人/创建时间/更新人/
-- 更新时间四字段，禁止窗口函数/CTE 等 MySQL 5.7 不兼容写法。同时为 `tab_approval_request`
-- 补充 `current_node_name` 字段，并预置默认两级（部门负责人 -> 安全管理员）审批流程的
-- 流程模型/流程定义/节点审批人规则种子数据，`flowable_definition_id` 留空，由应用启动后的
-- `WorkflowDefinitionBackfillRunner` 回填实际部署产生的 Flowable 流程定义 id。

-- ----------------------------------------------------------------------------
-- 流程模型（tab_wf_process_model）：一个流程的"身份"，可反复编辑草稿；草稿/发布/下线的
-- 业务代码属于后续设计器批次范围。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_process_model` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `process_code`          VARCHAR(64)  NOT NULL COMMENT '业务侧流程编码，唯一，如 MASTER_DATA_APPROVAL',
    `process_name`          VARCHAR(128) NOT NULL COMMENT '流程名称',
    `model_json`            TEXT         NULL COMMENT '当前草稿 DSL（JSON），status 为 PUBLISHED/DISABLED 时仍可继续编辑覆盖',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED',
    `current_definition_id` BIGINT       NULL COMMENT '当前生效的已发布版本，关联 tab_wf_process_definition.id',
    `create_by`             VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`             VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_process_model_process_code` (`process_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程模型主数据表';

-- ----------------------------------------------------------------------------
-- 流程定义（tab_wf_process_definition）：流程模型的一次不可变发布快照，"版本历史列表"的
-- 数据来源；节点审批人规则/流程实例均关联本表主键而非可变的 flowable_definition_key，确保
-- 旧版本运行中实例不受新版本发布影响。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_process_definition` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `process_model_id`        BIGINT       NOT NULL COMMENT '所属流程模型 id，关联 tab_wf_process_model.id',
    `process_code`            VARCHAR(64)  NOT NULL COMMENT '业务侧流程编码，冗余自流程模型',
    `version`                 INT          NOT NULL COMMENT '同一流程模型下的版本号，自增',
    `flowable_definition_key` VARCHAR(128) NOT NULL COMMENT 'Flowable 流程定义 key（BPMN process 的 id）',
    `flowable_definition_id`  VARCHAR(64)  NULL COMMENT 'Flowable 部署后生成的流程定义 id，部署完成前为空',
    `model_json_snapshot`     TEXT         NULL COMMENT '发布时刻的 DSL 快照，只读',
    `status`                  VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED/DISABLED',
    `published_by`            VARCHAR(64)  NULL COMMENT '发布人',
    `published_time`          DATETIME     NULL COMMENT '发布时间',
    `create_by`               VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`               VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_process_definition_model_version` (`process_model_id`, `version`),
    KEY `idx_tab_wf_process_definition_key` (`flowable_definition_key`),
    KEY `idx_tab_wf_process_definition_flowable_id` (`flowable_definition_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程定义（不可变发布版本快照）表';

-- ----------------------------------------------------------------------------
-- 节点审批人规则（tab_wf_node_assignee_rule）："可配置多级审批"的核心表：BPMN 只声明节点
-- 顺序与网关，节点的审批人规则、会签模式、允许的操作均在本表配置。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_node_assignee_rule` (
    `id`                      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `process_definition_id`   BIGINT      NOT NULL COMMENT '所属流程定义版本 id，关联 tab_wf_process_definition.id',
    `node_id`                 VARCHAR(64) NOT NULL COMMENT 'BPMN 用户任务节点 id',
    `node_name`               VARCHAR(128) NOT NULL COMMENT '节点名称',
    `node_order`              INT         NOT NULL DEFAULT 0 COMMENT '节点顺序，用于展示"第几级审批"',
    `assignee_type`           VARCHAR(32) NOT NULL COMMENT '审批人来源类型：USER/ROLE/POSITION/ORG_LEADER/APPLICANT_DEPT_LEADER/APPLICANT_DEPT_PARENT_LEADER/INITIATOR/PREVIOUS_APPROVER',
    `assignee_value`          VARCHAR(255) NULL COMMENT '审批人来源取值，按 assignee_type 解释',
    `approval_mode`           VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '审批模式：SINGLE/AND/OR/PERCENT',
    `approval_percent`        INT         NULL COMMENT '会签通过比例（0~100 的整数），仅 approval_mode=PERCENT 使用',
    `empty_assignee_strategy` VARCHAR(24) NOT NULL DEFAULT 'TO_WORKFLOW_ADMIN' COMMENT '空审批人策略：TO_WORKFLOW_ADMIN/AUTO_SKIP/REJECT',
    `allow_self_approval`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否允许审批人为发起人本人（自审）',
    `allow_transfer`          TINYINT     NOT NULL DEFAULT 0 COMMENT '是否允许转办',
    `allow_delegate`          TINYINT     NOT NULL DEFAULT 0 COMMENT '是否允许委派',
    `allow_add_sign`          TINYINT     NOT NULL DEFAULT 0 COMMENT '是否允许加签',
    `allow_return`            TINYINT     NOT NULL DEFAULT 0 COMMENT '是否允许退回到该节点',
    `create_by`               VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`               VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_node_assignee_rule` (`process_definition_id`, `node_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '节点审批人规则表';

-- ----------------------------------------------------------------------------
-- 流程实例（tab_wf_process_instance）：applicant_id/applicant_org_id 是发起时刻快照，全部
-- AssigneeResolver 只读这个快照，不实时重查申请人当前组织。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_process_instance` (
    `id`                     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `flowable_instance_id`   VARCHAR(64) NULL COMMENT 'Flowable 流程实例 id，创建本行时流程实例可能尚未启动',
    `process_definition_id`  BIGINT      NOT NULL COMMENT '所属流程定义版本 id，关联 tab_wf_process_definition.id',
    `business_type`          VARCHAR(32) NOT NULL COMMENT '业务对象类型，如 ORG/USER/POSITION/APP',
    `business_id`            BIGINT      NULL COMMENT '业务对象 id',
    `title`                  VARCHAR(255) NULL COMMENT '流程标题，供列表展示',
    `applicant_id`           BIGINT      NOT NULL COMMENT '发起人用户 id',
    `applicant_org_id`       BIGINT      NULL COMMENT '发起人所属组织 id，发起时快照',
    `status`                 VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/APPROVED/REJECTED/WITHDRAWN/TERMINATED',
    `current_node_id`        VARCHAR(64) NULL COMMENT '当前所在节点 id，结束后置空',
    `current_node_name`      VARCHAR(128) NULL COMMENT '当前所在节点名称，结束后置空',
    `started_time`           DATETIME    NOT NULL COMMENT '启动时间',
    `finished_time`          DATETIME    NULL COMMENT '结束时间，运行中为空',
    `create_by`              VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`              VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_process_instance_flowable_id` (`flowable_instance_id`),
    KEY `idx_tab_wf_process_instance_business` (`business_type`, `business_id`),
    KEY `idx_tab_wf_process_instance_applicant` (`applicant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程实例表';

-- ----------------------------------------------------------------------------
-- 审批任务（tab_wf_approval_task）："我的待办"查询以本表为准，不直接查询 ACT_RU_TASK。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_approval_task` (
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `flowable_task_id`     VARCHAR(64) NOT NULL COMMENT 'Flowable 用户任务 id',
    `process_instance_id`  BIGINT      NULL COMMENT '所属流程实例 id，关联 tab_wf_process_instance.id',
    `node_id`              VARCHAR(64) NOT NULL COMMENT '节点 id',
    `node_name`            VARCHAR(128) NULL COMMENT '节点名称',
    `assignee_id`          BIGINT      NULL COMMENT '指定处理人用户 id，候选组任务未认领时为空',
    `candidate_type`       VARCHAR(16) NULL COMMENT '候选人类型：USER/ROLE，会签/候选组场景为空则查明细表',
    `status`               VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/CLAIMED/COMPLETED/TRANSFERRED/RETURNED',
    `finished_time`        DATETIME    NULL COMMENT '完成时间，未完成为空',
    `create_by`            VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，即任务创建时间',
    `update_by`            VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_approval_task_flowable_id` (`flowable_task_id`),
    KEY `idx_tab_wf_approval_task_instance` (`process_instance_id`),
    KEY `idx_tab_wf_approval_task_assignee` (`assignee_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批任务表';

-- ----------------------------------------------------------------------------
-- 审批任务候选人明细（tab_wf_approval_task_candidate）：会签/候选组节点每个候选人一行。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_approval_task_candidate` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `task_id`          BIGINT      NOT NULL COMMENT '所属审批任务 id，关联 tab_wf_approval_task.id',
    `candidate_type`   VARCHAR(16) NOT NULL COMMENT '候选人类型：USER/ROLE',
    `candidate_value`  VARCHAR(64) NOT NULL COMMENT '候选人取值：USER 为用户 id 文本，ROLE 为角色编码',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_approval_task_candidate` (`task_id`, `candidate_type`, `candidate_value`),
    KEY `idx_tab_wf_approval_task_candidate_value` (`candidate_type`, `candidate_value`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批任务候选人明细表';

-- ----------------------------------------------------------------------------
-- 审批轨迹（tab_wf_approval_record）：完整审批轨迹，"我的已办"查询与 WithdrawPolicy 判断
-- "是否已有人审批过"均以本表为准，不查 ACT_HI_TASKINST。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_approval_record` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `process_instance_id` BIGINT      NOT NULL COMMENT '所属流程实例 id，关联 tab_wf_process_instance.id',
    `task_id`             BIGINT      NULL COMMENT '关联的审批任务 id，SUBMIT/TERMINATE 无关联任务时为空',
    `node_id`             VARCHAR(64) NULL COMMENT '节点 id，无关联节点时为空',
    `node_name`           VARCHAR(128) NULL COMMENT '节点名称，无关联节点时为空',
    `operator_id`         BIGINT      NULL COMMENT '操作人用户 id',
    `action`              VARCHAR(16) NOT NULL COMMENT '动作类型：SUBMIT/APPROVE/REJECT/RETURN/TRANSFER/DELEGATE/ADD_SIGN/WITHDRAW/TERMINATE',
    `remark`              VARCHAR(500) NULL COMMENT '处理意见/说明',
    `from_user_id`        BIGINT      NULL COMMENT '转办/委派场景记录的原处理人用户 id',
    `create_by`           VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，即操作发生时间',
    `update_by`           VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_wf_approval_record_instance` (`process_instance_id`),
    KEY `idx_tab_wf_approval_record_task` (`task_id`),
    KEY `idx_tab_wf_approval_record_operator` (`operator_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批轨迹表，只追加不更新不删除';

-- ----------------------------------------------------------------------------
-- 操作幂等记录（tab_wf_operation_request）：request_key 取自 X-Request-Id，为空时退化为
-- 不做幂等保护。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_operation_request` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `request_key`   VARCHAR(128) NOT NULL COMMENT '幂等键，取自 X-Request-Id 请求头',
    `task_id`       BIGINT      NULL COMMENT '关联的审批任务 id，部分操作（如撤回）不针对具体任务，可为空',
    `operator_id`   BIGINT      NULL COMMENT '操作人用户 id',
    `operation`     VARCHAR(16) NOT NULL COMMENT '操作类型，同 tab_wf_approval_record.action 枚举',
    `status`        VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '执行结果状态：SUCCESS/FAILED',
    `create_by`     VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_operation_request_key` (`request_key`),
    KEY `idx_tab_wf_operation_request_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作幂等记录表';

-- ----------------------------------------------------------------------------
-- tab_approval_request 补充 current_node_name 字段，用于列表展示当前在哪一级审批
-- （四个业务模块接入新引擎的改造属于后续第 8 节任务范围，本次只补字段）。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_approval_request`
    ADD COLUMN `current_node_name` VARCHAR(128) NULL COMMENT '当前所在审批节点名称，流程结束后置空' AFTER `flowable_task_id`;

-- ----------------------------------------------------------------------------
-- 默认两级审批规则需要的占位角色：部门负责人（组织负责人类审批人规则兜底/演示）、安全管理员。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_role` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('部门负责人', 'DEPT_LEADER', 0, '组织负责人类审批人规则兜底/演示角色（workflow-approval-engine change）', 2000, 'system', NOW(), 'system', NOW()),
       ('安全管理员', 'SECURITY_ADMIN', 0, '默认两级审批流程第二级审批角色（workflow-approval-engine change）', 2000, 'system', NOW(), 'system', NOW());

-- ----------------------------------------------------------------------------
-- 默认主数据审批流程（部门负责人 -> 安全管理员两级）种子数据：流程模型 + 流程定义（version=1）+
-- 节点审批人规则。flowable_definition_id 留空，由 WorkflowDefinitionBackfillRunner 在应用
-- 启动、Flowable 完成部署后回填。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_wf_process_model` (`process_code`, `process_name`, `model_json`, `status`, `current_definition_id`,
                                     `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('MASTER_DATA_APPROVAL', '主数据变更审批流程',
        '{"processCode":"MASTER_DATA_APPROVAL","processName":"主数据变更审批流程","nodes":[{"id":"start","type":"START"},{"id":"deptLeaderApprove","type":"APPROVAL","name":"部门负责人审批","assigneeType":"ORG_LEADER","assigneeValue":"DEPT_LEADER","approvalMode":"SINGLE","emptyAssigneeStrategy":"TO_WORKFLOW_ADMIN","allowSelfApproval":false,"allowTransfer":true,"allowDelegate":true,"allowAddSign":false,"allowReturn":false},{"id":"securityAdminApprove","type":"APPROVAL","name":"安全管理员审批","assigneeType":"ROLE","assigneeValue":"SECURITY_ADMIN","approvalMode":"SINGLE","emptyAssigneeStrategy":"TO_WORKFLOW_ADMIN","allowSelfApproval":false,"allowTransfer":true,"allowDelegate":true,"allowAddSign":false,"allowReturn":true},{"id":"end","type":"END"}],"edges":[{"from":"start","to":"deptLeaderApprove"},{"from":"deptLeaderApprove","to":"securityAdminApprove"},{"from":"securityAdminApprove","to":"end"}]}',
        'PUBLISHED', NULL, 'system', NOW(), 'system', NOW());

SET @wf_master_data_model_id := (SELECT `id` FROM `tab_wf_process_model` WHERE `process_code` = 'MASTER_DATA_APPROVAL');

INSERT INTO `tab_wf_process_definition` (`process_model_id`, `process_code`, `version`, `flowable_definition_key`,
                                          `flowable_definition_id`, `model_json_snapshot`, `status`, `published_by`,
                                          `published_time`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @wf_master_data_model_id, 'MASTER_DATA_APPROVAL', 1, 'masterDataApprovalProcess', NULL, `model_json`,
       'PUBLISHED', 'system', NOW(), 'system', NOW(), 'system', NOW()
FROM `tab_wf_process_model`
WHERE `id` = @wf_master_data_model_id;

SET @wf_master_data_definition_id := (SELECT `id` FROM `tab_wf_process_definition` WHERE `process_model_id` = @wf_master_data_model_id AND `version` = 1);

UPDATE `tab_wf_process_model`
SET `current_definition_id` = @wf_master_data_definition_id
WHERE `id` = @wf_master_data_model_id;

INSERT INTO `tab_wf_node_assignee_rule` (`process_definition_id`, `node_id`, `node_name`, `node_order`,
                                          `assignee_type`, `assignee_value`, `approval_mode`, `approval_percent`,
                                          `empty_assignee_strategy`, `allow_self_approval`, `allow_transfer`,
                                          `allow_delegate`, `allow_add_sign`, `allow_return`, `create_by`,
                                          `create_time`, `update_by`, `update_time`)
VALUES (@wf_master_data_definition_id, 'deptLeaderApprove', '部门负责人审批', 1, 'ORG_LEADER', 'DEPT_LEADER', 'SINGLE',
        NULL, 'TO_WORKFLOW_ADMIN', 0, 1, 1, 0, 0, 'system', NOW(), 'system', NOW()),
       (@wf_master_data_definition_id, 'securityAdminApprove', '安全管理员审批', 2, 'ROLE', 'SECURITY_ADMIN', 'SINGLE',
        NULL, 'TO_WORKFLOW_ADMIN', 0, 1, 1, 0, 1, 'system', NOW(), 'system', NOW());
