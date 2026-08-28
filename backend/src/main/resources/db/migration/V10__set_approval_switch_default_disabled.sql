-- add-master-data-approval-workflow：系统初始化时审批默认改为关闭，管理员需要在“审批设置”
-- 页面按需手动开启（design.md Decision 9 调整）。V8 已在部分环境执行过，其 INSERT 语句里
-- enabled=1 的取值不能直接修改，改由本迁移统一收口：调整列默认值 + 回填四条既有记录为
-- enabled=0，新旧环境执行完本迁移后都收敛到默认关闭。仅使用 MySQL 5.7 兼容写法。

ALTER TABLE `tab_approval_switch` ALTER COLUMN `enabled` SET DEFAULT 0;

UPDATE `tab_approval_switch` SET `enabled` = 0 WHERE `biz_type` IN ('ORG', 'USER', 'POSITION', 'APP');
