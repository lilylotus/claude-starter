-- ----------------------------------------------------------------------------
-- production-approval-lifecycle change 第6节"运行时与复杂任务"配套增量迁移（tasks.md 6.3）：
-- tab_wf_node_assignee_rule 补 reject_policy 列，供会签（多实例）节点区分 VETO（一票否决）/
-- THRESHOLD（阈值制，A>=K 通过、A+U<K 拒绝、其余等待）两种反对票处理策略（design.md 第7节）。
-- 仅 approval_mode 为 AND/OR/PERCENT（会签）的节点使用，单人/候选组节点与 v1 编译产出的历史
-- 行均保持为 NULL，FlowableWorkflowService 据此区分一个会签任务是走 v1 遗留的"任一驳回即
-- 终止"判定还是 v2 的 N/A/R/U 计票判定，互不影响。
-- 全部使用 MySQL 5.7 兼容写法，不使用窗口函数/CTE/JSON_TABLE/厂商专属 upsert。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_node_assignee_rule`
    ADD COLUMN `reject_policy` VARCHAR(24) NULL COMMENT '会签反对票处理策略：VETO（一票否决）/THRESHOLD（阈值制），仅会签节点使用，DSL v2 专用，v1 编译器与单人/候选组节点恒为 NULL' AFTER `approval_percent`;
