package cn.nihility.rbac.workflow.designer.compiler;

import java.util.List;
import org.flowable.bpmn.model.BpmnModel;

/**
 * {@link WorkflowModelCompiler} 编译产物：可直接用于 Flowable 部署的 {@link BpmnModel}，
 * 以及派生出的节点审批人规则草稿列表（workflow-approval-engine change design.md
 * Decision 10）。
 *
 * @param bpmnModel     编译产物 BPMN 对象模型
 * @param assigneeRules 从 {@code APPROVAL} 节点派生的节点审批人规则草稿列表
 */
public record CompiledProcess(BpmnModel bpmnModel, List<NodeAssigneeRuleDraft> assigneeRules) {
}
