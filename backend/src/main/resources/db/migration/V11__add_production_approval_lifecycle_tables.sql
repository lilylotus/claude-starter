-- ----------------------------------------------------------------------------
-- production-approval-lifecycle change 第 2 节"数据模型与兼容迁移"：按 design.md
-- Decision 9 逻辑数据模型表补齐 workflow-approval-engine 遗留的八张 tab_wf_* 表与
-- tab_approval_request 字段，并新增 10 张支撑发布审核、业务绑定、表单版本、节点轮次、
-- 业务活动锁、可靠执行 Outbox 的表。所有新增列均可空或带默认值，不破坏历史数据读取：
-- 历史流程定义的 schema_version 默认 1（v1 DSL），历史审批申请的 execution_mode 默认
-- LEGACY_SYNC，与本迁移的"历史数据回填"要求通过默认值自然满足，不需要额外 UPDATE 语句。
-- 全部使用 MySQL 5.7 兼容写法，不使用窗口函数/CTE/JSON_TABLE/厂商专属 upsert。
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- tab_wf_process_model：新增草稿修订号（乐观锁）、草稿状态（EDITING/IN_REVIEW/
-- APPROVED_FOR_RELEASE，与既有 status 字段的发布态区分）、是否接受新发起（enabled，
-- 与"是否有已发布版本"解耦，保存草稿不影响该开关）。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_process_model`
    ADD COLUMN `draft_revision` BIGINT NOT NULL DEFAULT 1 COMMENT '草稿修订号，乐观锁，每次保存草稿自增' AFTER `model_json`,
    ADD COLUMN `draft_status` VARCHAR(24) NOT NULL DEFAULT 'EDITING' COMMENT '草稿状态：EDITING/IN_REVIEW/APPROVED_FOR_RELEASE' AFTER `draft_revision`,
    ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否接受新发起，与草稿编辑/发布态解耦' AFTER `current_definition_id`;

-- ----------------------------------------------------------------------------
-- tab_wf_process_definition：新增 DSL schemaVersion、编译器版本、模型/XML 摘要、绑定的
-- 表单版本、BPMN XML 快照、节点到 activityId 映射快照、规则快照，供发布产物完整持久化。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_process_definition`
    ADD COLUMN `schema_version` INT NOT NULL DEFAULT 1 COMMENT 'DSL schemaVersion，历史 v1 定义默认 1' AFTER `version`,
    ADD COLUMN `compiler_version` VARCHAR(32) NULL COMMENT '编译该版本时使用的编译器版本号' AFTER `schema_version`,
    ADD COLUMN `model_digest` VARCHAR(128) NULL COMMENT 'DSL 快照摘要（如 SHA-256），供试运行报告/审核记录比对是否失效' AFTER `model_json_snapshot`,
    ADD COLUMN `xml_snapshot` LONGTEXT NULL COMMENT '编译产物 BPMN XML 快照，只读导出用' AFTER `model_digest`,
    ADD COLUMN `xml_digest` VARCHAR(128) NULL COMMENT 'BPMN XML 快照摘要' AFTER `xml_snapshot`,
    ADD COLUMN `node_mapping_json` LONGTEXT NULL COMMENT '节点 id 到 BPMN activityId 的映射快照（JSON）' AFTER `xml_digest`,
    ADD COLUMN `rule_snapshot_json` LONGTEXT NULL COMMENT '发布时刻节点审批人规则的完整快照（JSON），审计与试运行报告比对用' AFTER `node_mapping_json`,
    ADD COLUMN `form_version_id` BIGINT NULL COMMENT '绑定的表单版本 id，关联 tab_wf_form_version.id' AFTER `rule_snapshot_json`;

-- ----------------------------------------------------------------------------
-- tab_wf_process_instance：新增绑定维度回填、表单版本、申请人身份快照、明确 outcome、
-- 运维阻塞异常码、乐观锁 revision。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_process_instance`
    ADD COLUMN `binding_id` BIGINT NULL COMMENT '发起时命中的业务绑定 id，关联 tab_wf_process_binding.id' AFTER `process_definition_id`,
    ADD COLUMN `binding_revision` BIGINT NULL COMMENT '发起时命中的业务绑定修订号快照' AFTER `binding_id`,
    ADD COLUMN `form_version_id` BIGINT NULL COMMENT '发起时使用的表单版本 id 快照' AFTER `binding_revision`,
    ADD COLUMN `applicant_identity_snapshot` LONGTEXT NULL COMMENT '提交时冻结的申请人身份上下文快照（JSON：组织/岗位/角色等）' AFTER `applicant_org_id`,
    ADD COLUMN `outcome` VARCHAR(32) NULL COMMENT '流程正常结束的明确结果，不能从"找不到运行实例"反推' AFTER `status`,
    ADD COLUMN `exception_code` VARCHAR(64) NULL COMMENT '运维阻塞原因码，如 ASSIGNEE_EMPTY/JOB_FAILED，独立于 status，不伪造引擎终态' AFTER `outcome`,
    ADD COLUMN `revision` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁修订号，配合固定锁顺序使用' AFTER `finished_time`;

-- ----------------------------------------------------------------------------
-- tab_wf_approval_task：新增所属节点轮次、委派场景的 owner、委派状态、乐观锁 revision、
-- 取消原因、到期时间。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_approval_task`
    ADD COLUMN `node_run_id` BIGINT NULL COMMENT '所属节点轮次 id，关联 tab_wf_node_run.id' AFTER `process_instance_id`,
    ADD COLUMN `owner_id` BIGINT NULL COMMENT '委派场景下的原处理人（owner），受托人 resolve 后归还给该用户决策' AFTER `assignee_id`,
    ADD COLUMN `delegation_status` VARCHAR(24) NULL COMMENT '委派状态：DELEGATED/RESOLVED，非委派场景为空' AFTER `owner_id`,
    ADD COLUMN `revision` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁修订号' AFTER `status`,
    ADD COLUMN `cancel_reason` VARCHAR(255) NULL COMMENT '任务被取消（MI 提前结束/退回/终止）时的原因说明' AFTER `revision`,
    ADD COLUMN `due_time` DATETIME NULL COMMENT '节点操作期限，超时提醒依据，存储 UTC' AFTER `finished_time`;

-- ----------------------------------------------------------------------------
-- tab_wf_approval_task_candidate：新增候选人解析依据说明（如"角色 SECURITY_ADMIN 命中
-- 管理员 3 人"），供审计与运维排查。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_approval_task_candidate`
    ADD COLUMN `resolve_basis` VARCHAR(255) NULL COMMENT '候选人解析依据说明，供审计与运维排查' AFTER `candidate_value`;

-- ----------------------------------------------------------------------------
-- tab_approval_request：新增执行模式双轨（LEGACY_SYNC 同步/RELIABLE_ASYNC 可靠异步）、
-- 执行状态、发起时目标业务数据版本快照（乐观并发校验用）、关联的前序申请 id（改变路由或
-- payload 必须撤回/拒绝后新建申请并关联，不复用旧审批结论）。历史申请 execution_mode
-- 通过默认值自然回填为 LEGACY_SYNC。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_approval_request`
    ADD COLUMN `execution_mode` VARCHAR(24) NOT NULL DEFAULT 'LEGACY_SYNC' COMMENT '执行模式：LEGACY_SYNC 同步执行（历史行为）/RELIABLE_ASYNC 审批通过后经 Outbox 可靠异步执行' AFTER `current_node_name`,
    ADD COLUMN `execution_status` VARCHAR(24) NULL COMMENT '业务执行状态：NOT_READY/PENDING/EXECUTING/SUCCEEDED/FAILED_RETRYABLE/FAILED_MANUAL，仅 RELIABLE_ASYNC 使用' AFTER `execution_mode`,
    ADD COLUMN `base_revision` VARCHAR(128) NULL COMMENT '发起时目标业务数据的版本/哈希快照，执行前重新校验是否已变化' AFTER `execution_status`,
    ADD COLUMN `previous_request_id` BIGINT NULL COMMENT '因路由或 payload 变更而重新发起时关联的前一条申请 id' AFTER `base_revision`;

-- ----------------------------------------------------------------------------
-- tab_wf_release_review（新增）：流程模型发布审核记录，编辑者与审核者分离；审核历史保留，
-- 不覆盖，草稿修改后此前审核记录随 draft_revision 失效但不删除。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_release_review` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `process_model_id` BIGINT       NOT NULL COMMENT '所属流程模型 id，关联 tab_wf_process_model.id',
    `draft_revision`   BIGINT       NOT NULL COMMENT '发起审核时的草稿修订号快照',
    `artifact_digest`  VARCHAR(128) NOT NULL COMMENT '发起审核时的 DSL 产物摘要，草稿再次修改后与最新摘要不一致即视为审核失效',
    `editor_id`        BIGINT       NOT NULL COMMENT '提交审核的编辑者用户 id',
    `reviewer_id`      BIGINT       NULL COMMENT '审核者用户 id，做出审核决策后回填，且不能与 editor_id 相同',
    `review_status`    VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
    `review_opinion`   VARCHAR(500) NULL COMMENT '审核意见',
    `test_report_ref`  VARCHAR(128) NULL COMMENT '关联的测试环境试运行报告引用 id',
    `submit_time`      DATETIME     NOT NULL COMMENT '提交审核时间',
    `review_time`      DATETIME     NULL COMMENT '审核决策时间',
    `create_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_wf_release_review_model_revision` (`process_model_id`, `draft_revision`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '流程模型发布审核记录表，历史保留不覆盖';

-- ----------------------------------------------------------------------------
-- tab_wf_process_binding（新增）：业务绑定维度 (biz_type, operation_type, scope_type,
-- scope_id)，scope_type=GLOBAL 时 scope_id 固定为 0 哨兵值，不使用 NULL 规避唯一性约束。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_process_binding` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`        VARCHAR(32) NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP',
    `operation_type`  VARCHAR(16) NOT NULL COMMENT '操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE',
    `scope_type`      VARCHAR(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '绑定范围类型：ORG（精确组织）/GLOBAL（全局兜底）',
    `scope_id`        BIGINT      NOT NULL DEFAULT 0 COMMENT '范围内组织 id，scope_type=GLOBAL 时固定为 0',
    `definition_id`   BIGINT      NOT NULL COMMENT '绑定的流程定义 id，关联 tab_wf_process_definition.id，显式版本，不隐式取最新',
    `execution_mode`  VARCHAR(24) NOT NULL DEFAULT 'LEGACY_SYNC' COMMENT '该绑定下发起申请使用的执行模式：LEGACY_SYNC/RELIABLE_ASYNC',
    `revision`        BIGINT      NOT NULL DEFAULT 1 COMMENT '乐观锁修订号，切换绑定版本时自增',
    `enabled`         TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否启用，禁用后该维度拒绝新发起，与唯一性约束无关（保留行以便重新启用）',
    `create_by`       VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_process_binding_dimension` (`biz_type`, `operation_type`, `scope_type`, `scope_id`),
    KEY `idx_tab_wf_process_binding_definition` (`definition_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务绑定表：biz_type+operation_type 精确定位到显式 definitionId';

-- ----------------------------------------------------------------------------
-- tab_wf_form_version（新增）：基于既有动态字段元数据生成的不可变表单版本快照。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_form_version` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `form_code`      VARCHAR(64)  NOT NULL COMMENT '表单编码，通常对应 biz_type',
    `form_version`   INT          NOT NULL COMMENT '表单版本号，同 form_code 下自增',
    `schema_text`    LONGTEXT     NOT NULL COMMENT '表单字段 schema 快照（JSON），来自动态字段元数据，只读不可变',
    `schema_digest`  VARCHAR(128) NOT NULL COMMENT 'schema_text 摘要，供快速比对是否变化',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_form_version` (`form_code`, `form_version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '不可变表单版本快照表';

-- ----------------------------------------------------------------------------
-- tab_wf_node_run（新增）：每次节点激活生成一条轮次记录，承载会签计票（N/A/R）与作用域
-- 隔离；重入节点（退回后再次到达）产生新的 round_no。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_node_run` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `instance_id`    BIGINT      NOT NULL COMMENT '所属流程实例 id，关联 tab_wf_process_instance.id',
    `node_id`        VARCHAR(64) NOT NULL COMMENT '节点 id',
    `execution_id`   VARCHAR(64) NULL COMMENT 'Flowable 执行 execution id，MI 场景对应 miBody execution',
    `round_no`       INT         NOT NULL DEFAULT 1 COMMENT '同一节点第几次激活（退回重建轮次时递增）',
    `total_count`    INT         NOT NULL DEFAULT 0 COMMENT '总票数 N',
    `agree_count`    INT         NOT NULL DEFAULT 0 COMMENT '同意票数 A',
    `reject_count`   INT         NOT NULL DEFAULT 0 COMMENT '反对/驳回票数 R',
    `run_status`     VARCHAR(24) NOT NULL DEFAULT 'RUNNING' COMMENT '轮次状态：RUNNING/COMPLETED/CANCELLED',
    `revision`       BIGINT      NOT NULL DEFAULT 1 COMMENT '乐观锁修订号，同实例锁内更新计票',
    `create_by`      VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_node_run` (`instance_id`, `node_id`, `round_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '节点轮次表，承载会签计票与作用域隔离';

-- ----------------------------------------------------------------------------
-- tab_wf_business_lock（新增）：同业务目标同时间只允许一条活动变更申请，业务活动申请锁。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_business_lock` (
    `biz_type`          VARCHAR(32)  NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP',
    `target_key`        VARCHAR(128) NOT NULL COMMENT '业务目标标识（如目标记录 id 文本，CREATE 场景可用申请自身临时键）',
    `active_request_id` BIGINT       NULL COMMENT '当前占用该锁的活动申请 id，为空表示锁行存在但当前空闲，可复用行不必删除',
    `revision`           BIGINT      NOT NULL DEFAULT 1 COMMENT '乐观锁修订号',
    `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`biz_type`, `target_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务活动申请锁表，锁行可保留复用';

-- ----------------------------------------------------------------------------
-- tab_wf_outbox_event（新增）：审批终态副作用（业务执行、通知、抄送）的可靠事件表，同事务
-- 写入保证不丢；MySQL 5.7 领取方式为按索引读候选后逐条条件 UPDATE 抢占租约。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_outbox_event` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `event_id`         VARCHAR(64)  NOT NULL COMMENT '业务幂等事件 id',
    `aggregate_id`     VARCHAR(64)  NOT NULL COMMENT '聚合根标识，通常为流程实例 id 文本',
    `event_seq`        BIGINT       NOT NULL COMMENT '同一聚合根内事件序号，供顺序消费参考',
    `event_type`       VARCHAR(32)  NOT NULL COMMENT '事件类型：TASK_CREATED/TASK_ASSIGNED/TASK_CANCELLED/PROCESS_APPROVED/PROCESS_REJECTED/BUSINESS_SUCCEEDED/BUSINESS_FAILED/CC_CREATED',
    `payload`          LONGTEXT     NOT NULL COMMENT '事件负载（JSON）',
    `status`           VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/LEASED/SUCCEEDED/FAILED',
    `next_retry_time`  DATETIME     NOT NULL COMMENT '下次可领取/重试时间，用于按索引扫描到期候选',
    `lease_token`      VARCHAR(64)  NULL COMMENT '当前持有租约的 token，完成/续期均需匹配该 token（CAS）',
    `lease_until`      DATETIME     NULL COMMENT '租约到期时间，过期后其他消费者可重新抢占',
    `attempt_count`    INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数，超过上限转人工处理队列',
    `create_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_outbox_event_id` (`event_id`),
    KEY `idx_tab_wf_outbox_event_poll` (`status`, `next_retry_time`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Outbox 可靠事件表';

-- ----------------------------------------------------------------------------
-- tab_wf_event_consume（新增）：消费者按 eventId+consumerCode 去重。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_event_consume` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `event_id`       VARCHAR(64)  NOT NULL COMMENT '关联 tab_wf_outbox_event.event_id',
    `consumer_code`  VARCHAR(32)  NOT NULL COMMENT '消费者标识，如 BUSINESS_EXECUTOR/NOTIFIER',
    `result`         VARCHAR(24)  NOT NULL DEFAULT 'SUCCEEDED' COMMENT '消费结果：SUCCEEDED/FAILED',
    `processed_time` DATETIME     NOT NULL COMMENT '处理完成时间',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_event_consume` (`event_id`, `consumer_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '事件消费去重表';

-- ----------------------------------------------------------------------------
-- tab_wf_business_execution（新增）：业务执行每次尝试的记录，最终成功结果的唯一性由
-- tab_approval_request 行的 CAS（execution_status 从 PENDING/EXECUTING 转 SUCCEEDED）保证，
-- 本表 (request_id, attempt_no) 唯一仅约束"同一次尝试不得重复写入"。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_business_execution` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `request_id`        BIGINT      NOT NULL COMMENT '关联 tab_approval_request.id',
    `attempt_no`        INT         NOT NULL DEFAULT 1 COMMENT '第几次执行尝试',
    `lease_token`       VARCHAR(64) NULL COMMENT '执行该次尝试时持有的 Outbox 租约 token',
    `execution_status`  VARCHAR(24) NOT NULL DEFAULT 'EXECUTING' COMMENT '本次尝试的执行状态：EXECUTING/SUCCEEDED/FAILED_RETRYABLE/FAILED_MANUAL',
    `error_code`        VARCHAR(64) NULL COMMENT '失败错误码，供运维分类处理',
    `result_target_id`  BIGINT      NULL COMMENT '执行成功后生效的业务记录 id（CREATE 场景）',
    `create_by`         VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_business_execution` (`request_id`, `attempt_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务执行尝试记录表';

-- ----------------------------------------------------------------------------
-- tab_wf_cc_record（新增）：抄送持久化 recipient 记录，非 userTask，不阻塞流程。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_cc_record` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `instance_id`   BIGINT      NOT NULL COMMENT '所属流程实例 id，关联 tab_wf_process_instance.id',
    `node_run_id`   BIGINT      NOT NULL COMMENT '产生该抄送的节点轮次 id，关联 tab_wf_node_run.id',
    `recipient_id`  BIGINT      NOT NULL COMMENT '抄送接收人用户 id',
    `read_time`     DATETIME    NULL COMMENT '接收人查看时间，未读为空',
    `create_by`     VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_cc_record` (`node_run_id`, `recipient_id`),
    KEY `idx_tab_wf_cc_record_recipient` (`recipient_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '抄送记录表';

-- ----------------------------------------------------------------------------
-- tab_wf_notification（新增）：站内通知投递记录，通知失败独立重试，不回滚审批。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_wf_notification` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `event_id`         VARCHAR(64) NOT NULL COMMENT '关联 tab_wf_outbox_event.event_id',
    `recipient_id`     BIGINT      NOT NULL COMMENT '接收人用户 id',
    `channel`          VARCHAR(24) NOT NULL DEFAULT 'IN_APP' COMMENT '通知渠道：IN_APP 站内消息，首轮唯一可靠渠道',
    `delivery_status`  VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：PENDING/DELIVERED/FAILED',
    `attempt_count`    INT         NOT NULL DEFAULT 0 COMMENT '已尝试投递次数',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_wf_notification` (`event_id`, `recipient_id`, `channel`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知投递记录表';
