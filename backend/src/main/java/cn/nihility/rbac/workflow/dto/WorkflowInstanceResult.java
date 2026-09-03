package cn.nihility.rbac.workflow.dto;

/**
 * 启动流程结果。
 *
 * @param processInstanceId        流程实例 id（{@code tab_wf_process_instance.id}）
 * @param flowableProcessInstanceId Flowable 流程实例 id
 * @param currentNodeId            启动后所在的当前节点 id
 * @param currentNodeName          启动后所在的当前节点名称
 */
public record WorkflowInstanceResult(
        Long processInstanceId,
        String flowableProcessInstanceId,
        String currentNodeId,
        String currentNodeName) {
}
