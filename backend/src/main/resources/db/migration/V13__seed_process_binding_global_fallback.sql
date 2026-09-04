-- ----------------------------------------------------------------------------
-- production-approval-lifecycle change tasks.md 4.5"兼容性种子迁移"：
-- ApprovalProcessServiceImpl.start() 接入 ProcessBindingResolutionService 后，四个业务模块
-- （ORG/USER/POSITION/APP）提交审批必须能解析出一条已启用的业务绑定，否则直接报"未配置
-- 绑定"而无法提交审批。本脚本为 ORG/USER/POSITION/APP 与 ApprovalOperationType 全部取值
-- （CREATE/UPDATE/ENABLE/DISABLE/DELETE）各插入一条全局兜底绑定（scope_type=GLOBAL，
-- scope_id=0），definition_id 取现有 MASTER_DATA_APPROVAL 流程模型的 current_definition_id，
-- execution_mode 固定 LEGACY_SYNC（历史同步执行行为，不受本轮"拒绝 RELIABLE_ASYNC"限制）。
-- 用子查询动态获取 current_definition_id，不硬编码具体 id 数字；若该流程模型尚未发布任何
-- 版本（current_definition_id 为 NULL），WHERE 条件令派生结果集为空，本脚本优雅跳过插入，
-- 不产生违反 tab_wf_process_binding 唯一约束或引用无效 definition_id 的脏数据。
-- 全部使用 MySQL 5.7 兼容写法，不使用窗口函数/CTE/JSON_TABLE/厂商专属 upsert。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';

SET @wf_master_data_definition_id := (
    SELECT `current_definition_id`
    FROM `tab_wf_process_model`
    WHERE `process_code` = 'MASTER_DATA_APPROVAL'
    LIMIT 1
);

INSERT INTO `tab_wf_process_binding` (`biz_type`, `operation_type`, `scope_type`, `scope_id`, `definition_id`,
                                       `execution_mode`, `revision`, `enabled`, `create_by`, `create_time`,
                                       `update_by`, `update_time`)
SELECT biz.`biz_type`, op.`operation_type`, 'GLOBAL', 0, @wf_master_data_definition_id, 'LEGACY_SYNC', 1, 1,
       @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM (SELECT 'ORG' AS `biz_type`
      UNION ALL
      SELECT 'USER'
      UNION ALL
      SELECT 'POSITION'
      UNION ALL
      SELECT 'APP') biz
         CROSS JOIN (SELECT 'CREATE' AS `operation_type`
                     UNION ALL
                     SELECT 'UPDATE'
                     UNION ALL
                     SELECT 'ENABLE'
                     UNION ALL
                     SELECT 'DISABLE'
                     UNION ALL
                     SELECT 'DELETE') op
WHERE @wf_master_data_definition_id IS NOT NULL;
