-- ----------------------------------------------------------------------------
-- workflow-condition-payload-fields change：为 tab_wf_process_definition 增加
-- route_field_codes 列，落库该流程定义发布时从条件分支提取出的去重路由字段清单
-- （JSON 数组，元素形如 {"bizType":"ORG","fieldCode":"riskLevel"}），供审批提交
-- 发起流程实例时按提交内容构建 Flowable 流程变量，不需要每次重新解析
-- model_json_snapshot（design.md Decision 1）。允许为空，历史已发布版本不回填。
-- MySQL 5.7 兼容语法：普通 ADD COLUMN，不使用任何 8.0+ 特性。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_process_definition`
    ADD COLUMN `route_field_codes` TEXT NULL COMMENT '条件分支引用的路由字段清单（JSON 数组，元素形如 {"bizType":"ORG","fieldCode":"riskLevel"}），发布时从条件边提取去重写入，历史版本为空'
    AFTER `form_version_id`;
