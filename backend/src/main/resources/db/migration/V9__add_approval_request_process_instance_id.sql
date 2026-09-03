-- ----------------------------------------------------------------------------
-- 审批业务模块接入通用审批引擎：tab_approval_request 关联流程实例
-- ----------------------------------------------------------------------------
-- workflow-approval-engine change 第 8 批"现有审批业务模块改造"：ApprovalRequestServiceImpl
-- 改为调用 WorkflowService（cn.nihility.rbac.workflow.engine.WorkflowService）驱动多级审批，
-- 需要把业务申请与引擎侧的 tab_wf_process_instance 关联起来，作为 approve/reject/withdraw/
-- pagePending 查询候选人命中情况的桥梁。旧的 flowable_process_instance_id/flowable_task_id
-- 两列继续保留，仅作兼容展示用，不再是驱动审批状态流转的依据。

ALTER TABLE `tab_approval_request`
    ADD COLUMN `process_instance_id` BIGINT NULL COMMENT '关联 tab_wf_process_instance.id，驱动多级审批的流程实例'
        AFTER `flowable_task_id`;
