-- add-master-data-approval-workflow：V8 已在部分环境执行，不能直接修改其内容。
-- 将 V8 中作为“我的申请”按钮子资源登记的 ApprovalManagement:request:approve
-- 调整为“审批管理”下的“待我审批”页面资源，使数据库菜单层级与前端三个菜单项一致。
-- 仅使用 MySQL 5.7 可用的普通 UPDATE 与标量子查询写法。

SET @approval_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'approval');

UPDATE `tab_menu`
SET `name` = '待我审批',
    `parent_id` = @approval_group_id,
    `resource_type` = 1,
    `show_order` = 15,
    `remark` = '查看并处理全部待审批申请',
    `update_by` = '1',
    `update_time` = NOW()
WHERE `code` = 'ApprovalManagement:request:approve';
