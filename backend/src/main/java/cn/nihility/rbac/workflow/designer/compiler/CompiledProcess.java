package cn.nihility.rbac.workflow.designer.compiler;

import cn.nihility.rbac.workflow.designer.dto.RouteFieldCode;
import java.util.List;
import org.flowable.bpmn.model.BpmnModel;

/**
 * {@link WorkflowModelCompiler} 编译产物：可直接用于 Flowable 部署的 {@link BpmnModel}，
 * 派生出的节点审批人规则草稿列表（workflow-approval-engine change design.md Decision 10），
 * 以及从全部条件边提取去重后的路由字段清单（workflow-condition-payload-fields change
 * design.md Decision 1）。
 *
 * @param bpmnModel       编译产物 BPMN 对象模型
 * @param assigneeRules   从 {@code APPROVAL} 节点派生的节点审批人规则草稿列表
 * @param routeFieldCodes 从全部条件边提取去重后的路由字段清单，随发布产物落库到
 *                        {@code tab_wf_process_definition.route_field_codes}
 */
public record CompiledProcess(
        BpmnModel bpmnModel, List<NodeAssigneeRuleDraft> assigneeRules, List<RouteFieldCode> routeFieldCodes) {
}
