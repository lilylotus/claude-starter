package cn.nihility.rbac.approval.dto;

/**
 * Flowable 审批流程启动结果。
 *
 * @param processInstanceId 流程实例 id
 * @param taskId            当前用户任务 id
 */
public record ApprovalProcessInstance(String processInstanceId, String taskId) {
}
