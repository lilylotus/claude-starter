-- app-sync-changelog-pull change tasks.md 6.1-6.3：通知任务先落库为 PENDING 状态、
-- 再按状态机（PENDING/PROCESSING/RETRY/SUCCESS/DEAD，见 task_status 列）异步发送。
-- 一条任务在真正发起过至少一次 HTTP 请求前，notify_status（历史遗留的 1=成功/2=失败
-- 展示字段）尚无意义，允许为空；发送出现结果后才回填（成功=1，失败或死信=2）。
ALTER TABLE `tab_app_notify_record`
    MODIFY COLUMN `notify_status` TINYINT NULL COMMENT '通知状态：1=成功，2=失败，任务处于 PENDING/PROCESSING/RETRY 时为空';
