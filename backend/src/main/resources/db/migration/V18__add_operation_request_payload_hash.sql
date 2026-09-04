-- ----------------------------------------------------------------------------
-- production-approval-lifecycle change 第6节"运行时与复杂任务"配套增量迁移（tasks.md 6.2）：
-- tab_wf_operation_request 补齐 payload_hash/result_text 两列，供 IdempotencyService 区分
-- "同一幂等键的真实重试"（payload 摘要一致，直接返回原结果）与"复用同一幂等键提交了不同
-- 内容"（payload 摘要不一致，拒绝并报 IDEMPOTENCY_CONFLICT，design.md 第8节）。两列均可为
-- 空以兼容历史行（历史行由本迁移之前的代码写入，从未落过 payload/result，比对时不会命中，
-- 不影响新请求的幂等判断——历史 request_key 早已被消费过，不会被新请求重复使用）。
-- 全部使用 MySQL 5.7 兼容写法，不使用窗口函数/CTE/JSON_TABLE/厂商专属 upsert。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_wf_operation_request`
    ADD COLUMN `payload_hash` VARCHAR(64) NULL COMMENT '本次请求规范化 payload 的 SHA-256 摘要（十六进制小写），用于判断重复请求是否为真实重试' AFTER `operation`,
    ADD COLUMN `result_text`  LONGTEXT    NULL COMMENT '首次执行成功后的返回结果 JSON 快照，命中同 key 同 payload 的重复请求时直接反序列化返回' AFTER `payload_hash`;
