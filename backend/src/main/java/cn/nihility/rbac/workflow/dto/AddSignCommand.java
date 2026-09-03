package cn.nihility.rbac.workflow.dto;

import java.util.List;

/**
 * 加签命令对象，为会签节点动态增加候选审批人；减签不在本次范围内实现的操作入口一并预留在
 * {@link cn.nihility.rbac.workflow.engine.WorkflowService} 接口层面之外，当前只支持加签。
 *
 * @param taskId         会签节点下任一现有审批任务 id（{@code tab_wf_approval_task.id}），
 *                       用于定位所属的多实例执行
 * @param operatorId     操作人用户 id
 * @param addUserIds     新增的候选审批人用户 id 列表
 * @param remark         加签说明，可为空
 * @param idempotencyKey 幂等键，可为空
 */
public record AddSignCommand(Long taskId, Long operatorId, List<Long> addUserIds, String remark,
        String idempotencyKey) {
}
